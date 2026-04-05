package com.yshalsager.mafza.emergency.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.yshalsager.mafza.core.contracts.EmergencyStep
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepResult
import com.yshalsager.mafza.core.contracts.StepStatus
import kotlinx.coroutines.withTimeoutOrNull

class SmsRecipientStep(
    private val app_context: Context?,
    private val run_step_state: RunStepState,
    private val recipient: String,
    private val recipient_index: Int,
    private val has_sms_permission_checker: (() -> Boolean)? = null,
    private val sms_sender: (suspend (recipient: String, message: String) -> Result<Unit>)? = null,
    private val message_renderer: ((StepContext, RunStepState) -> String)? = null,
    private val now_provider: () -> Long = { System.currentTimeMillis() }
) : EmergencyStep {
    override suspend fun execute(ctx: StepContext): StepResult {
        val started_at = now_provider()
        if (recipient.isBlank()) {
            return StepResult(
                step_id = step_id(),
                status = StepStatus.FAILED,
                details = "empty_recipient",
                started_at_epoch_ms = started_at,
                finished_at_epoch_ms = now_provider()
            )
        }

        if (ctx.mode == ExecutionMode.DRY_RUN) {
            return StepResult(
                step_id = step_id(),
                status = StepStatus.SKIPPED_DRY_RUN,
                details = "dry_run_sms_${redact_recipient(recipient)}",
                started_at_epoch_ms = started_at,
                finished_at_epoch_ms = now_provider()
            )
        }

        if (!has_sms_permission()) {
            return StepResult(
                step_id = step_id(),
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "missing_send_sms_permission",
                started_at_epoch_ms = started_at,
                finished_at_epoch_ms = now_provider()
            )
        }

        val message = render_message(ctx)

        val timeout_seconds = ctx.profile.sms_timeout_seconds.coerceIn(1, 60)
        val send_result = withTimeoutOrNull(timeout_seconds * 1_000L) {
            send_sms(recipient, message)
        }
        if (send_result == null) {
            return StepResult(
                step_id = step_id(),
                status = StepStatus.TIMED_OUT,
                details = "sms_timeout_${timeout_seconds}s_${redact_recipient(recipient)}",
                started_at_epoch_ms = started_at,
                finished_at_epoch_ms = now_provider()
            )
        }

        return send_result.fold(
            onSuccess = {
                StepResult(
                    step_id = step_id(),
                    status = StepStatus.SUCCESS,
                    details = "sms_sent_${redact_recipient(recipient)}",
                    started_at_epoch_ms = started_at,
                    finished_at_epoch_ms = now_provider()
                )
            },
            onFailure = { throwable ->
                if (throwable is SmsServiceUnavailableException) {
                    return@fold StepResult(
                        step_id = step_id(),
                        status = StepStatus.SKIPPED_UNAVAILABLE,
                        details = "sms_service_unavailable",
                        started_at_epoch_ms = started_at,
                        finished_at_epoch_ms = now_provider()
                    )
                }
                StepResult(
                    step_id = step_id(),
                    status = StepStatus.FAILED,
                    details = throwable.message ?: "sms_send_failed_${redact_recipient(recipient)}",
                    started_at_epoch_ms = started_at,
                    finished_at_epoch_ms = now_provider()
                )
            }
        )
    }

    private fun step_id(): String = "sms_recipient_${recipient_index + 1}"

    private fun render_message(step_context: StepContext): String {
        val override_renderer = message_renderer
        if (override_renderer != null) return override_renderer(step_context, run_step_state)
        return MessageTemplateRenderer.render(
            context = require_app_context(),
            step_context = step_context,
            run_step_state = run_step_state
        )
    }

    private fun has_sms_permission(): Boolean {
        val override_checker = has_sms_permission_checker
        if (override_checker != null) return override_checker()

        val context = require_app_context()
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun send_sms(recipient: String, message: String): Result<Unit> {
        val override_sender = sms_sender
        if (override_sender != null) return override_sender(recipient, message)

        val context = require_app_context()
        val sms_manager = context.getSystemService(SmsManager::class.java)
            ?: return Result.failure(SmsServiceUnavailableException())

        return runCatching {
            sms_manager.sendTextMessage(recipient, null, message, null, null)
        }
    }

    private fun require_app_context(): Context {
        return requireNotNull(app_context) {
            "SmsRecipientStep requires app_context when no test overrides are provided"
        }
    }

    private fun redact_recipient(value: String): String {
        if (value.length <= 4) return value
        return "${value.take(2)}***${value.takeLast(2)}"
    }

    class SmsServiceUnavailableException : IllegalStateException("SmsManager unavailable")
}
