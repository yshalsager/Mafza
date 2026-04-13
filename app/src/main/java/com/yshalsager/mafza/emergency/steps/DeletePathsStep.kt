package com.yshalsager.mafza.emergency.steps

import com.yshalsager.mafza.emergency.shell.PrivilegedCommandExecutor
import com.yshalsager.mafza.emergency.shell.PrivilegedCommandResult
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.DeleteTarget
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.IdentifiedEmergencyStep
import com.yshalsager.mafza.core.contracts.PolicyBoundEmergencyStep
import com.yshalsager.mafza.core.contracts.StepBranch
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepResult
import com.yshalsager.mafza.core.contracts.StepStatus
import android.os.Build
import android.os.Environment
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.withTimeoutOrNull

class DeletePathsStep(
    private val command_executor: PrivilegedCommandExecutor,
    private val has_all_files_access_checker: (() -> Boolean)? = null,
    private val now_provider: () -> Long = { System.currentTimeMillis() }
) : PolicyBoundEmergencyStep, IdentifiedEmergencyStep {
    override val action_id: ActionId = ActionId.DELETE_PATHS
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
                details = "dry_run_delete_paths",
                started_at = started_at
            )
        }
        val targets = ctx.profile.delete_allowlist
        if (targets.isEmpty()) {
            return step_result(
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "empty_delete_allowlist",
                started_at = started_at
            )
        }

        var success_count = 0
        var failed_count = 0
        var timed_out_count = 0
        var unavailable_count = 0

        targets.forEach { target ->
            if (!is_valid_target(target)) {
                failed_count += 1
                return@forEach
            }

            val command_result = withTimeoutOrNull(DELETE_TIMEOUT_SECONDS * 1_000L) {
                execute_delete_target(target)
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

        val summary = "delete_success=$success_count,failed=$failed_count,timed_out=$timed_out_count,unavailable=$unavailable_count"
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

    private fun is_valid_target(target: DeleteTarget): Boolean {
        return is_valid_path_target(target)
    }

    private fun is_valid_path_target(target: DeleteTarget): Boolean {
        val raw_path = target.path.trim()
        if (raw_path.isEmpty()) return false
        if (!raw_path.startsWith("/")) return false

        val path_file = File(raw_path)
        val canonical_path = runCatching { path_file.canonicalPath }.getOrNull() ?: return false
        if (canonical_path != raw_path) return false
        if (canonical_path == "/") return false
        if (Files.isSymbolicLink(path_file.toPath())) return false
        return true
    }

    private suspend fun execute_delete_target(target: DeleteTarget): PrivilegedCommandResult {
        if (command_executor.is_available()) {
            val target_path = target.path.trim()
            return if (target.recursive) {
                command_executor.execute_argv(
                    argv = listOf("rm", "-rf", "--", target_path),
                    timeout_seconds = DELETE_TIMEOUT_SECONDS
                )
            } else {
                execute_non_recursive_delete(target_path)
            }
        }

        if (!has_all_files_access()) {
            return PrivilegedCommandResult(
                exit_code = 1,
                stdout = "",
                stderr = "all_files_access_required",
                timed_out = false,
                unavailable = true
            )
        }

        val target_path = target.path.trim()
        return execute_local_path_delete(target_path = target_path, recursive = target.recursive)
    }

    private fun has_all_files_access(): Boolean {
        val override_checker = has_all_files_access_checker
        if (override_checker != null) return override_checker()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        return runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
    }

    private fun execute_local_path_delete(target_path: String, recursive: Boolean): PrivilegedCommandResult {
        val target_file = File(target_path)
        if (!target_file.exists()) {
            return PrivilegedCommandResult(
                exit_code = 0,
                stdout = "",
                stderr = "",
                timed_out = false,
                unavailable = false
            )
        }
        val deleted = if (recursive) {
            target_file.deleteRecursively()
        } else {
            target_file.delete()
        }
        return PrivilegedCommandResult(
            exit_code = if (deleted) 0 else 1,
            stdout = "",
            stderr = if (deleted) "" else "delete_failed",
            timed_out = false,
            unavailable = false
        )
    }

    private suspend fun execute_non_recursive_delete(target_path: String): PrivilegedCommandResult {
        val rmdir_result = command_executor.execute_argv(
            argv = listOf("rmdir", "--", target_path),
            timeout_seconds = DELETE_TIMEOUT_SECONDS
        )
        if (rmdir_result.timed_out || rmdir_result.unavailable || rmdir_result.exit_code == 0) {
            return rmdir_result
        }

        return command_executor.execute_argv(
            argv = listOf("rm", "-f", "--", target_path),
            timeout_seconds = DELETE_TIMEOUT_SECONDS
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
        private const val STEP_ID = "delete_paths"
        private const val DELETE_TIMEOUT_SECONDS = 15
    }
}
