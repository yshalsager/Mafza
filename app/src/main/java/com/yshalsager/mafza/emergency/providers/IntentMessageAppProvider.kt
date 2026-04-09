package com.yshalsager.mafza.emergency.providers

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.yshalsager.mafza.core.contracts.ActionBinding
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ActionProvider
import com.yshalsager.mafza.core.contracts.ProviderCapabilities
import com.yshalsager.mafza.core.contracts.ProviderExecutionResult
import com.yshalsager.mafza.core.contracts.ProviderPreflightResult
import com.yshalsager.mafza.core.contracts.ProviderRequest
import com.yshalsager.mafza.core.contracts.StepStatus

class IntentMessageAppProvider(
    private val app_context: Context
) : ActionProvider {
    override fun actionId(): ActionId = ActionId.NOTIFY_MESSAGE_APP

    override fun isAvailable(binding: ActionBinding): Boolean {
        if (!binding.enabled) return false
        if (binding.action_id != actionId()) return false
        val intent = build_send_intent(
            package_name = binding.package_name,
            activity_name = binding.activity_name,
            rendered_message = ""
        )
        return intent.resolveActivity(app_context.packageManager) != null
    }

    override fun capabilities(binding: ActionBinding): ProviderCapabilities {
        return ProviderCapabilities(
            supports_template = true,
            supports_target = true,
            supports_auto_send = false
        )
    }

    override suspend fun preflight(binding: ActionBinding): ProviderPreflightResult {
        if (!isAvailable(binding)) {
            return ProviderPreflightResult(
                ready = false,
                blocking_reason = "provider_unavailable"
            )
        }
        return ProviderPreflightResult(ready = true)
    }

    override suspend fun execute(request: ProviderRequest): ProviderExecutionResult {
        val intent = build_send_intent(
            package_name = request.action_binding.package_name,
            activity_name = request.action_binding.activity_name,
            rendered_message = request.rendered_message,
            notify_target = request.notify_target
        )

        val can_resolve = intent.resolveActivity(app_context.packageManager) != null
        if (!can_resolve) {
            return ProviderExecutionResult(
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "provider_intent_unresolvable"
            )
        }

        return try {
            app_context.startActivity(intent)
            ProviderExecutionResult(
                status = StepStatus.SUCCESS,
                details = "provider_intent_launched"
            )
        } catch (_: ActivityNotFoundException) {
            ProviderExecutionResult(
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "provider_activity_not_found"
            )
        } catch (throwable: Throwable) {
            ProviderExecutionResult(
                status = StepStatus.FAILED,
                details = throwable.message ?: "provider_launch_failed"
            )
        }
    }

    private fun build_send_intent(
        package_name: String,
        activity_name: String?,
        rendered_message: String,
        notify_target: String = ""
    ): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage(package_name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_TEXT, rendered_message)
            if (notify_target.isNotBlank()) {
                putExtra("address", notify_target)
            }

            val resolved_activity_name = activity_name?.ifBlank { null }
            if (resolved_activity_name != null) {
                component = ComponentName(package_name, resolved_activity_name)
            }
        }
    }
}
