package com.yshalsager.mafza.core.contracts

import kotlinx.serialization.Serializable

typealias RunId = String

@Serializable
enum class ExecutionMode {
    LIVE,
    DRY_RUN
}

@Serializable
enum class StepStatus {
    SUCCESS,
    FAILED,
    TIMED_OUT,
    SKIPPED_UNAVAILABLE,
    SKIPPED_DRY_RUN,
    CANCELLED_PRE_START
}

@Serializable
enum class RunStatus {
    RUNNING,
    CANCELLED_PRE_START,
    COMPLETED_SUCCESS,
    COMPLETED_PARTIAL,
    COMPLETED_FAILED
}

@Serializable
enum class TriggerSource {
    MANUAL_IN_APP,
    SHORTCUT,
    WIDGET,
    QUICK_SETTINGS_TILE
}

object RunStatusDeriver {
    fun derive_run_status(
        is_running: Boolean,
        cancelled_pre_start: Boolean,
        step_statuses: List<StepStatus>
    ): RunStatus {
        if (is_running) return RunStatus.RUNNING
        if (cancelled_pre_start) return RunStatus.CANCELLED_PRE_START
        if (step_statuses.isEmpty()) return RunStatus.COMPLETED_FAILED

        val success_count = step_statuses.count { it == StepStatus.SUCCESS }
        val all_success = success_count == step_statuses.size
        if (all_success) return RunStatus.COMPLETED_SUCCESS

        val has_non_success = step_statuses.any { it != StepStatus.SUCCESS }
        if (success_count > 0 && has_non_success) return RunStatus.COMPLETED_PARTIAL

        val has_hard_failure = step_statuses.any { it == StepStatus.FAILED || it == StepStatus.TIMED_OUT }
        if (success_count == 0 && has_hard_failure) return RunStatus.COMPLETED_FAILED

        // If no step succeeded but all outcomes are non-failing skips, treat run as successful.
        val all_non_failing_skips = step_statuses.all {
            it == StepStatus.SKIPPED_UNAVAILABLE || it == StepStatus.SKIPPED_DRY_RUN
        }
        if (all_non_failing_skips) return RunStatus.COMPLETED_SUCCESS

        return RunStatus.COMPLETED_FAILED
    }
}
