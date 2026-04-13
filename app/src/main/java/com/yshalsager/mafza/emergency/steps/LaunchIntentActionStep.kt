package com.yshalsager.mafza.emergency.steps

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ActionPolicyKeys
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.IdentifiedEmergencyStep
import com.yshalsager.mafza.core.contracts.IntentActionSpec
import com.yshalsager.mafza.core.contracts.PolicyBoundEmergencyStep
import com.yshalsager.mafza.core.contracts.StepBranch
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepResult
import com.yshalsager.mafza.core.contracts.StepStatus
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

class LaunchIntentActionStep(
    private val app_context: Context?,
    private val intent_action_spec: IntentActionSpec,
    private val intent_builder: (() -> Intent)? = null,
    private val intent_resolver: ((Intent) -> Boolean)? = null,
    private val intent_launcher: (suspend (Intent) -> Result<Unit>)? = null,
    private val now_provider: () -> Long = { System.currentTimeMillis() }
) : PolicyBoundEmergencyStep, IdentifiedEmergencyStep {
    override val action_id: ActionId = ActionId.LAUNCH_INTENT
    override val policy_key: String = ActionPolicyKeys.for_intent(intent_action_spec.id)
    override val branch: StepBranch = StepBranch.NOTIFY
    override val step_id: String = "launch_intent_${intent_action_spec.id}"

    override suspend fun execute(ctx: StepContext): StepResult {
        val started_at = now_provider()
        if (!intent_action_spec.enabled) {
            return StepResult(
                step_id = step_id,
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "intent_step_disabled",
                started_at_epoch_ms = started_at,
                finished_at_epoch_ms = now_provider()
            )
        }
        if (ctx.mode == ExecutionMode.DRY_RUN) {
            return StepResult(
                step_id = step_id,
                status = StepStatus.SKIPPED_DRY_RUN,
                details = "dry_run_intent",
                started_at_epoch_ms = started_at,
                finished_at_epoch_ms = now_provider()
            )
        }

        val intent = intent_builder?.invoke() ?: build_intent()
        apply_intent_extras(intent)
        val can_resolve = resolve_intent(intent)
        if (!can_resolve) {
            return StepResult(
                step_id = step_id,
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "intent_unresolvable",
                started_at_epoch_ms = started_at,
                finished_at_epoch_ms = now_provider()
            )
        }

        val timeout_seconds = (if (intent_action_spec.timeout_seconds > 0) {
            intent_action_spec.timeout_seconds
        } else {
            ctx.profile.intent_timeout_seconds
        }).coerceIn(1, 60)
        val launch_result = withTimeoutOrNull(timeout_seconds * 1_000L) {
            launch_intent(intent)
        }
        val finished_at = now_provider()
        if (launch_result == null) {
            return StepResult(
                step_id = step_id,
                status = StepStatus.TIMED_OUT,
                details = "intent_timeout_${timeout_seconds}s",
                started_at_epoch_ms = started_at,
                finished_at_epoch_ms = finished_at
            )
        }

        val launch_error = launch_result.exceptionOrNull()
        if (launch_error == null) {
            return StepResult(
                step_id = step_id,
                status = StepStatus.SUCCESS,
                details = "intent_launched",
                started_at_epoch_ms = started_at,
                finished_at_epoch_ms = finished_at
            )
        }
        if (launch_error is ActivityNotFoundException) {
            return StepResult(
                step_id = step_id,
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "intent_activity_not_found",
                started_at_epoch_ms = started_at,
                finished_at_epoch_ms = finished_at
            )
        }
        return StepResult(
            step_id = step_id,
            status = StepStatus.FAILED,
            details = launch_error.message ?: "intent_launch_failed",
            started_at_epoch_ms = started_at,
            finished_at_epoch_ms = finished_at
        )
    }

    private fun resolve_intent(intent: Intent): Boolean {
        val override_resolver = intent_resolver
        if (override_resolver != null) return override_resolver(intent)

        val context = require_app_context()
        return intent.resolveActivity(context.packageManager) != null
    }

    private suspend fun launch_intent(intent: Intent): Result<Unit> {
        val override_launcher = intent_launcher
        if (override_launcher != null) return override_launcher(intent)

        val context = require_app_context()
        return runCatching { context.startActivity(intent) }
    }

    private fun require_app_context(): Context {
        return requireNotNull(app_context) {
            "LaunchIntentActionStep requires app_context when no test overrides are provided"
        }
    }

    private fun build_intent(): Intent {
        val action = intent_action_spec.intent_action?.ifBlank { null } ?: Intent.ACTION_VIEW
        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent_action_spec.data_uri != null || intent_action_spec.mime_type != null) {
            val parsed_uri = intent_action_spec.data_uri?.let { runCatching { Uri.parse(it) }.getOrNull() }
            if (parsed_uri != null && !intent_action_spec.mime_type.isNullOrBlank()) {
                intent.setDataAndType(parsed_uri, intent_action_spec.mime_type)
            } else if (parsed_uri != null) {
                intent.data = parsed_uri
            } else if (!intent_action_spec.mime_type.isNullOrBlank()) {
                intent.type = intent_action_spec.mime_type
            }
        }

        intent_action_spec.categories.forEach { category ->
            if (category.isNotBlank()) intent.addCategory(category)
        }

        val package_name = intent_action_spec.package_name?.ifBlank { null }
        val activity_name = intent_action_spec.activity_name?.ifBlank { null }
        if (package_name != null) intent.`package` = package_name
        if (package_name != null && activity_name != null) {
            intent.component = ComponentName(package_name, activity_name)
        }

        intent_action_spec.flags.mapNotNull(::resolve_intent_flag).forEach(intent::addFlags)
        return intent
    }

    private fun apply_intent_extras(intent: Intent) {
        val extras_json = intent_action_spec.extras_json?.trim().orEmpty()
        if (extras_json.isEmpty()) return

        val extras_object = runCatching { json_parser.parseToJsonElement(extras_json).jsonObject }.getOrNull() ?: return
        extras_object.forEach { (key, value) ->
            if (value == JsonNull) return@forEach
            if (value is JsonPrimitive) {
                if (value.isString) {
                    intent.putExtra(key, value.content)
                    return@forEach
                }
                value.booleanOrNull?.let {
                    intent.putExtra(key, it)
                    return@forEach
                }
                value.longOrNull?.let {
                    if (it in Int.MIN_VALUE..Int.MAX_VALUE) {
                        intent.putExtra(key, it.toInt())
                    } else {
                        intent.putExtra(key, it)
                    }
                    return@forEach
                }
                value.doubleOrNull?.let {
                    intent.putExtra(key, it)
                    return@forEach
                }
            }
            intent.putExtra(key, value.toString())
        }
    }

    private fun resolve_intent_flag(raw_flag: String): Int? {
        return when (raw_flag) {
            "FLAG_ACTIVITY_NEW_TASK" -> Intent.FLAG_ACTIVITY_NEW_TASK
            "FLAG_ACTIVITY_CLEAR_TOP" -> Intent.FLAG_ACTIVITY_CLEAR_TOP
            "FLAG_ACTIVITY_SINGLE_TOP" -> Intent.FLAG_ACTIVITY_SINGLE_TOP
            "FLAG_ACTIVITY_CLEAR_TASK" -> Intent.FLAG_ACTIVITY_CLEAR_TASK
            "FLAG_ACTIVITY_NO_HISTORY" -> Intent.FLAG_ACTIVITY_NO_HISTORY
            else -> raw_flag.toIntOrNull()
        }
    }

    companion object {
        private val json_parser = Json { ignoreUnknownKeys = true }
    }
}
