package com.yshalsager.mafza.emergency.steps

import android.content.Context
import com.yshalsager.mafza.emergency.providers.ActionProviderRegistry
import com.yshalsager.mafza.core.contracts.ActionBinding
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ActionPolicyKeys
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.IdentifiedEmergencyStep
import com.yshalsager.mafza.core.contracts.PolicyBoundEmergencyStep
import com.yshalsager.mafza.core.contracts.ProviderCapabilities
import com.yshalsager.mafza.core.contracts.ProviderRequest
import com.yshalsager.mafza.core.contracts.StepBranch
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepResult
import com.yshalsager.mafza.core.contracts.StepStatus
import kotlinx.coroutines.withTimeoutOrNull

class MessageAppProviderStep(
    private val app_context: Context?,
    private val action_provider_registry: ActionProviderRegistry,
    private val run_step_state: RunStepState,
    private val action_binding: ActionBinding,
    private val binding_index: Int,
    private val message_renderer: ((StepContext, RunStepState) -> String)? = null,
    private val timeout_millis_provider: (StepContext) -> Long = { 20_000L },
    private val now_provider: () -> Long = { System.currentTimeMillis() }
) : PolicyBoundEmergencyStep, IdentifiedEmergencyStep {
    override val action_id: ActionId = ActionId.NOTIFY_MESSAGE_APP
    override val policy_key: String = ActionPolicyKeys.for_binding(action_binding)
    override val branch: StepBranch = StepBranch.NOTIFY
    override val step_id: String = "${STEP_ID}_${binding_index + 1}"

    override suspend fun execute(ctx: StepContext): StepResult {
        val started_at = now_provider()
        if (!action_binding.enabled) {
            return step_result(
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "notify_binding_disabled",
                started_at = started_at
            )
        }

        val provider = action_provider_registry.provider_for(ActionId.NOTIFY_MESSAGE_APP)
            ?: return step_result(
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "missing_notify_provider",
                started_at = started_at
            )

        if (!provider.isAvailable(action_binding)) {
            return step_result(
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "provider_unavailable",
                started_at = started_at
            )
        }

        val required_capabilities = required_capabilities(ctx)
        val provider_capabilities = provider.capabilities(action_binding)
        if (!meets_required_capabilities(required_capabilities, provider_capabilities)) {
            return step_result(
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "provider_capability_mismatch",
                started_at = started_at
            )
        }

        val preflight = provider.preflight(action_binding)
        if (!preflight.ready) {
            return step_result(
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = preflight.blocking_reason ?: "provider_preflight_failed",
                started_at = started_at
            )
        }

        if (ctx.mode == ExecutionMode.DRY_RUN) {
            return step_result(
                status = StepStatus.SKIPPED_DRY_RUN,
                details = "dry_run_message_provider",
                started_at = started_at
            )
        }

        val message = render_message(ctx)
        val timeout_millis = timeout_millis_provider(ctx).coerceIn(1_000L, 60_000L)
        val provider_result = withTimeoutOrNull(timeout_millis) {
            provider.execute(
                request = ProviderRequest(
                    run_id = ctx.run_id,
                    mode = ctx.mode,
                    action_binding = action_binding,
                    notify_target = ctx.profile.notify_target,
                    rendered_message = message,
                    timeout_seconds = (timeout_millis / 1_000L).toInt()
                )
            )
        } ?: return step_result(
            status = StepStatus.TIMED_OUT,
            details = "provider_timeout_${timeout_millis / 1_000L}s",
            started_at = started_at
        )

        return step_result(
            status = provider_result.status,
            details = provider_result.details,
            started_at = started_at
        )
    }

    private fun required_capabilities(ctx: StepContext): ProviderCapabilities {
        return ProviderCapabilities(
            supports_template = true,
            supports_target = ctx.profile.notify_target.isNotBlank(),
            supports_auto_send = false
        )
    }

    private fun meets_required_capabilities(
        required: ProviderCapabilities,
        provided: ProviderCapabilities
    ): Boolean {
        if (required.supports_template && !provided.supports_template) return false
        if (required.supports_target && !provided.supports_target) return false
        if (required.supports_auto_send && !provided.supports_auto_send) return false
        return true
    }

    private fun render_message(step_context: StepContext): String {
        val override_renderer = message_renderer
        if (override_renderer != null) return override_renderer(step_context, run_step_state)

        return MessageTemplateRenderer.render(
            context = require_app_context(),
            step_context = step_context,
            run_step_state = run_step_state
        )
    }

    private fun step_result(
        status: StepStatus,
        details: String?,
        started_at: Long
    ): StepResult {
        return StepResult(
            step_id = step_id,
            status = status,
            details = details,
            started_at_epoch_ms = started_at,
            finished_at_epoch_ms = now_provider()
        )
    }

    private fun require_app_context(): Context {
        return requireNotNull(app_context) {
            "MessageAppProviderStep requires app_context when no message_renderer override is provided"
        }
    }

    companion object {
        private const val STEP_ID = "notify_message_app"
    }
}
