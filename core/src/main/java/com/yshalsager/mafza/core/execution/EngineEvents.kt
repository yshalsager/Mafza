package com.yshalsager.mafza.core.execution

import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.RunId
import com.yshalsager.mafza.core.contracts.RunStatus
import com.yshalsager.mafza.core.contracts.StepResult
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TriggerSource

sealed interface EngineEvent {
    data class RunStarted(
        val run_id: RunId,
        val trigger: TriggerSource,
        val mode: ExecutionMode,
        val started_at_epoch_ms: Long
    ) : EngineEvent

    data class CancelWindowOpened(
        val run_id: RunId,
        val window_ends_at_epoch_ms: Long
    ) : EngineEvent

    data class IgnoredDuplicateTrigger(
        val active_run_id: RunId,
        val requested_trigger: TriggerSource,
        val requested_mode: ExecutionMode
    ) : EngineEvent

    data class RunCancelledPreStart(
        val run_id: RunId
    ) : EngineEvent

    data class StepCompleted(
        val run_id: RunId,
        val step_result: StepResult
    ) : EngineEvent

    data class RunCompleted(
        val run_id: RunId,
        val run_status: RunStatus,
        val step_statuses: List<StepStatus>,
        val completed_at_epoch_ms: Long
    ) : EngineEvent
}
