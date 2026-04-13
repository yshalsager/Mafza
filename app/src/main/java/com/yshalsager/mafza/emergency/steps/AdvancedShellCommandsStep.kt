package com.yshalsager.mafza.emergency.steps

import com.yshalsager.mafza.emergency.shell.PrivilegedCommandExecutor
import com.yshalsager.mafza.emergency.shell.PrivilegedCommandResult
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.IdentifiedEmergencyStep
import com.yshalsager.mafza.core.contracts.PolicyBoundEmergencyStep
import com.yshalsager.mafza.core.contracts.ShellCommandSpec
import com.yshalsager.mafza.core.contracts.StepBranch
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepResult
import com.yshalsager.mafza.core.contracts.StepStatus
import kotlinx.coroutines.withTimeoutOrNull

class AdvancedShellCommandsStep(
    private val command_executor: PrivilegedCommandExecutor,
    private val now_provider: () -> Long = { System.currentTimeMillis() }
) : PolicyBoundEmergencyStep, IdentifiedEmergencyStep {
    override val action_id: ActionId = ActionId.ADVANCED_SHELL_COMMANDS
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
                details = "dry_run_advanced_shell",
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

        val commands = ctx.profile.advanced_shell_commands.filter { it.enabled }
        if (commands.isEmpty()) {
            return step_result(
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "empty_advanced_shell_commands",
                started_at = started_at
            )
        }

        var success_count = 0
        var failed_count = 0
        var timed_out_count = 0
        var unavailable_count = 0

        for (command in commands) {
            val command_result = execute_command(command)
            when {
                command_result == null || command_result.timed_out -> timed_out_count += 1
                command_result.unavailable -> unavailable_count += 1
                command_result.exit_code == 0 -> success_count += 1
                else -> failed_count += 1
            }

            val command_failed = command_result == null || command_result.timed_out || command_result.unavailable || command_result.exit_code != 0
            if (command_failed && !command.continue_on_failure) break
        }

        val summary = "advanced_shell_success=$success_count,failed=$failed_count,timed_out=$timed_out_count,unavailable=$unavailable_count"
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

    private suspend fun execute_command(command: ShellCommandSpec): PrivilegedCommandResult? {
        val timeout_seconds = command.timeout_seconds.coerceIn(1, 120)
        val raw_shell = command.raw_shell?.trim().orEmpty()
        return withTimeoutOrNull(timeout_seconds * 1_000L) {
            if (command.allow_raw_shell && raw_shell.isNotEmpty()) {
                return@withTimeoutOrNull command_executor.execute_raw_shell(
                    raw_shell = raw_shell,
                    timeout_seconds = timeout_seconds
                )
            }

            val argv = command.argv.map(String::trim).filter { it.isNotEmpty() }
            if (argv.isEmpty()) {
                return@withTimeoutOrNull PrivilegedCommandResult(
                    exit_code = null,
                    stdout = "",
                    stderr = "",
                    timed_out = false,
                    failure_message = "invalid_safe_command_argv"
                )
            }
            return@withTimeoutOrNull command_executor.execute_argv(
                argv = argv,
                timeout_seconds = timeout_seconds
            )
        }
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
        private const val STEP_ID = "advanced_shell_commands"
    }
}
