package com.yshalsager.mafza.emergency.steps

import com.yshalsager.mafza.emergency.shell.PrivilegedCommandExecutor
import com.yshalsager.mafza.emergency.shell.PrivilegedCommandResult
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.DeleteTarget
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.PolicyBoundEmergencyStep
import com.yshalsager.mafza.core.contracts.StepBranch
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepResult
import com.yshalsager.mafza.core.contracts.StepStatus
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.withTimeoutOrNull

class DeletePathsStep(
    private val command_executor: PrivilegedCommandExecutor,
    private val content_uri_delete_executor: ContentUriDeleteExecutor = UnavailableContentUriDeleteExecutor,
    private val now_provider: () -> Long = { System.currentTimeMillis() }
) : PolicyBoundEmergencyStep {
    override val action_id: ActionId = ActionId.DELETE_PATHS
    override val branch: StepBranch = StepBranch.DESTRUCTIVE

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
        val has_path = target.path.trim().isNotEmpty()
        val has_content_uri = target.content_uri?.trim().isNullOrEmpty().not()
        if (has_path == has_content_uri) return false
        if (has_content_uri) return is_valid_content_uri_target(target)
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

    private fun is_valid_content_uri_target(target: DeleteTarget): Boolean {
        val raw_content_uri = target.content_uri?.trim().orEmpty()
        if (raw_content_uri.isEmpty()) return false
        return raw_content_uri.startsWith("content://", ignoreCase = true)
    }

    private suspend fun execute_delete_target(target: DeleteTarget): PrivilegedCommandResult {
        val raw_content_uri = target.content_uri?.trim().orEmpty()
        if (raw_content_uri.isNotEmpty()) {
            return content_uri_delete_executor.execute_delete(
                target = target,
                timeout_seconds = DELETE_TIMEOUT_SECONDS
            )
        }

        if (!command_executor.is_available()) {
            return PrivilegedCommandResult(
                exit_code = 1,
                stdout = "",
                stderr = "shizuku_unavailable",
                timed_out = false,
                unavailable = true
            )
        }

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
            step_id = STEP_ID,
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
