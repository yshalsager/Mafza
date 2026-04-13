package com.yshalsager.mafza.emergency.steps

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

class UninstallAppsStep(
    private val command_executor: PrivilegedCommandExecutor,
    private val now_provider: () -> Long = { System.currentTimeMillis() }
) : PolicyBoundEmergencyStep, IdentifiedEmergencyStep {
    override val action_id: ActionId = ActionId.UNINSTALL_APPS
    override val branch: StepBranch = StepBranch.DESTRUCTIVE
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
        if (ctx.mode == ExecutionMode.DRY_RUN) {
            return step_result(
                status = StepStatus.SKIPPED_DRY_RUN,
                details = "dry_run_uninstall",
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

        val packages = ctx.profile.uninstall_allowlist
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .distinct()
        if (packages.isEmpty()) {
            return step_result(
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "empty_uninstall_allowlist",
                started_at = started_at
            )
        }

        var success_count = 0
        var failed_count = 0
        var timed_out_count = 0
        var unavailable_count = 0

        packages.forEach { package_name ->
            if (!PACKAGE_NAME_REGEX.matches(package_name)) {
                failed_count += 1
                return@forEach
            }

            val command_result = withTimeoutOrNull(UNINSTALL_TIMEOUT_SECONDS * 1_000L) {
                command_executor.execute_argv(
                    argv = listOf("pm", "uninstall", "--user", "0", package_name),
                    timeout_seconds = UNINSTALL_TIMEOUT_SECONDS
                )
            }
            if (command_result == null || command_result.timed_out) {
                timed_out_count += 1
                return@forEach
            }
            if (command_result.unavailable) {
                unavailable_count += 1
                return@forEach
            }

            if (command_result.exit_code == 0) {
                success_count += 1
            } else {
                failed_count += 1
            }
        }

        val summary = "uninstall_success=$success_count,failed=$failed_count,timed_out=$timed_out_count,unavailable=$unavailable_count"
        if (timed_out_count > 0 && success_count == 0 && failed_count == 0) {
            return step_result(
                status = StepStatus.TIMED_OUT,
                details = summary,
                started_at = started_at
            )
        }
        if (failed_count == 0 && timed_out_count == 0 && unavailable_count == 0) {
            return step_result(
                status = StepStatus.SUCCESS,
                details = summary,
                started_at = started_at
            )
        }
        if (success_count == 0 && failed_count == 0 && timed_out_count == 0 && unavailable_count > 0) {
            return step_result(
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = summary,
                started_at = started_at
            )
        }
        if (timed_out_count > 0 && success_count == 0) {
            return step_result(
                status = StepStatus.TIMED_OUT,
                details = summary,
                started_at = started_at
            )
        }
        return step_result(
            status = StepStatus.FAILED,
            details = summary,
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
        private const val STEP_ID = "uninstall_apps"
        private const val UNINSTALL_TIMEOUT_SECONDS = 15
        private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    }
}
