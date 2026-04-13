package com.yshalsager.mafza.emergency.steps

import android.content.Context
import com.yshalsager.mafza.emergency.telegram.TelegramBotClient
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ActionPolicyKeys
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.IdentifiedEmergencyStep
import com.yshalsager.mafza.core.contracts.PolicyBoundEmergencyStep
import com.yshalsager.mafza.core.contracts.StepBranch
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepResult
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TelegramBotActionSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class TelegramBotStep(
    private val app_context: Context?,
    private val run_step_state: RunStepState,
    private val telegram_action_spec: TelegramBotActionSpec,
    private val action_index: Int,
    private val telegram_bot_client: TelegramBotClient,
    private val message_renderer: ((StepContext, RunStepState, String?) -> String)? = null,
    private val now_provider: () -> Long = { System.currentTimeMillis() }
) : PolicyBoundEmergencyStep, IdentifiedEmergencyStep {
    override val action_id: ActionId = ActionId.NOTIFY_TELEGRAM_BOT
    override val policy_key: String = ActionPolicyKeys.for_telegram_bot(telegram_action_spec.id)
    override val branch: StepBranch = StepBranch.NOTIFY
    override val step_id: String = "${STEP_ID}_${action_index + 1}"

    override suspend fun execute(ctx: StepContext): StepResult {
        val started_at = now_provider()
        if (!telegram_action_spec.enabled) {
            return step_result(
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "telegram_action_disabled",
                started_at = started_at
            )
        }

        val token = telegram_action_spec.bot_token.trim()
        val chat_id = telegram_action_spec.chat_id.trim()
        if (token.isEmpty() || chat_id.isEmpty()) {
            return step_result(
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "telegram_missing_config",
                started_at = started_at
            )
        }

        if (ctx.mode == ExecutionMode.DRY_RUN) {
            return step_result(
                status = StepStatus.SKIPPED_DRY_RUN,
                details = "dry_run_telegram_bot",
                started_at = started_at
            )
        }

        val timeout_seconds = telegram_action_spec.timeout_seconds.coerceIn(1, 180)
        val rendered_message = render_message(ctx)
        val send_result = withTimeoutOrNull(timeout_seconds * 1_000L) {
            withContext(Dispatchers.IO) {
                telegram_bot_client.send_message(
                    bot_token = token,
                    chat_id = chat_id,
                    text = rendered_message,
                    timeout_seconds = timeout_seconds
                )
            }
        } ?: return step_result(
            status = StepStatus.TIMED_OUT,
            details = "telegram_timeout_${timeout_seconds}s",
            started_at = started_at
        )

        return step_result(
            status = if (send_result.success) StepStatus.SUCCESS else StepStatus.FAILED,
            details = send_result.details,
            started_at = started_at
        )
    }

    private fun render_message(step_context: StepContext): String {
        val renderer = message_renderer
        if (renderer != null) {
            return renderer(step_context, run_step_state, telegram_action_spec.template_override)
        }
        return MessageTemplateRenderer.render(
            context = require_app_context(),
            step_context = step_context,
            run_step_state = run_step_state,
            template_override = telegram_action_spec.template_override
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
            "TelegramBotStep requires app_context when no message_renderer override is provided"
        }
    }

    companion object {
        private const val STEP_ID = "notify_telegram_bot"
    }
}
