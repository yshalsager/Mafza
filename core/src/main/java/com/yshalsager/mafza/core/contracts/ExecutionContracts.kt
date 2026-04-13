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
        step_statuses: List<StepStatus>,
        required_step_failed: Boolean = false
    ): RunStatus {
        if (is_running) return RunStatus.RUNNING
        if (cancelled_pre_start) return RunStatus.CANCELLED_PRE_START
        if (step_statuses.isEmpty()) return RunStatus.COMPLETED_FAILED

        val success_count = step_statuses.count { it == StepStatus.SUCCESS }
        val all_success = success_count == step_statuses.size
        val derived_status = when {
            all_success -> RunStatus.COMPLETED_SUCCESS

            step_statuses.any { it != StepStatus.SUCCESS } && success_count > 0 -> RunStatus.COMPLETED_PARTIAL

            success_count == 0 && step_statuses.any { it == StepStatus.FAILED || it == StepStatus.TIMED_OUT } -> {
                RunStatus.COMPLETED_FAILED
            }

            // If no step succeeded but all outcomes are non-failing skips, treat run as successful.
            step_statuses.all {
                it == StepStatus.SKIPPED_UNAVAILABLE || it == StepStatus.SKIPPED_DRY_RUN
            } -> RunStatus.COMPLETED_SUCCESS

            else -> RunStatus.COMPLETED_FAILED
        }
        if (!required_step_failed) return derived_status
        if (derived_status != RunStatus.COMPLETED_SUCCESS) return derived_status
        return if (success_count > 0) RunStatus.COMPLETED_PARTIAL else RunStatus.COMPLETED_FAILED
    }
}
