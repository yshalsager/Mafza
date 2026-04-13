package com.yshalsager.mafza.emergency.steps

import android.content.Context
import com.yshalsager.mafza.emergency.shell.PrivilegedCommandExecutor
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.IdentifiedEmergencyStep
import com.yshalsager.mafza.core.contracts.PolicyBoundEmergencyStep
import com.yshalsager.mafza.core.contracts.StepBranch
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepResult
import com.yshalsager.mafza.core.contracts.StepStatus
import kotlinx.coroutines.withTimeoutOrNull

class SelfUninstallStep(
    private val app_context: Context,
    private val command_executor: PrivilegedCommandExecutor,
    private val now_provider: () -> Long = { System.currentTimeMillis() }
) : PolicyBoundEmergencyStep, IdentifiedEmergencyStep {
    override val action_id: ActionId = ActionId.SELF_UNINSTALL
    override val branch: StepBranch = StepBranch.FINALIZE
    override val step_id: String = STEP_ID

    override suspend fun execute(ctx: StepContext): StepResult {
        val started_at = now_provider()
        if (!ctx.profile.destructive_actions_enabled) {
            return step_result(
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "destructive_actions_disabled",
                started_at = started_at
            )
        }
        if (!ctx.profile.self_uninstall_enabled) {
            return step_result(
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "self_uninstall_disabled",
                started_at = started_at
            )
        }
        if (ctx.mode == ExecutionMode.DRY_RUN) {
            return step_result(
                status = StepStatus.SKIPPED_DRY_RUN,
                details = "dry_run_self_uninstall",
                started_at = started_at
            )
        }
        if (!command_executor.is_available()) {
            return step_result(
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "shizuku_unavailable",
                started_at = started_at
            )
        }

        val package_name = app_context.packageName
        val command_result = withTimeoutOrNull(SELF_UNINSTALL_TIMEOUT_SECONDS * 1_000L) {
            command_executor.execute_argv(
                argv = listOf("pm", "uninstall", "--user", "0", package_name),
                timeout_seconds = SELF_UNINSTALL_TIMEOUT_SECONDS
            )
        }
        if (command_result == null || command_result.timed_out) {
            return step_result(
                status = StepStatus.TIMED_OUT,
                details = "self_uninstall_timeout_${SELF_UNINSTALL_TIMEOUT_SECONDS}s",
                started_at = started_at
            )
        }
        if (command_result.unavailable) {
            return step_result(
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "shizuku_unavailable",
                started_at = started_at
            )
        }
        if (command_result.exit_code == 0) {
            return step_result(
                status = StepStatus.SUCCESS,
                details = "self_uninstall_triggered",
                started_at = started_at
            )
        }
        return step_result(
            status = StepStatus.FAILED,
            details = "self_uninstall_failed_exit_${command_result.exit_code}",
            started_at = started_at
        )
    }

    private fun step_result(
        status: StepStatus,
        details: String,
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

    companion object {
        private const val STEP_ID = "self_uninstall"
        private const val SELF_UNINSTALL_TIMEOUT_SECONDS = 15
    }
}
