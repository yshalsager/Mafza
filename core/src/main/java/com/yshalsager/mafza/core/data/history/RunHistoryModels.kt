package com.yshalsager.mafza.core.data.history

import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.RunStatus
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TriggerSource
import kotlinx.serialization.Serializable

data class RunHistoryInsertBundle(
    val run: RunHistoryEntity,
    val steps: List<StepHistoryEntity> = emptyList(),
    val command_audits: List<CommandAuditEntity> = emptyList()
)

@Serializable
data class RedactedAuditExport(
    val runs: List<RedactedRunAudit>
)

@Serializable
data class RedactedRunAudit(
    val run_id: String,
    val started_at_epoch_ms: Long,
    val completed_at_epoch_ms: Long?,
    val trigger: TriggerSource,
    val mode: ExecutionMode,
    val status: RunStatus,
    val step_statuses: List<RedactedStepStatus>,
    val command_audits: List<RedactedCommandAudit>
)

@Serializable
data class RedactedStepStatus(
    val step_id: String,
    val status: StepStatus
)

@Serializable
data class RedactedCommandAudit(
    val action_id: ActionId,
    val target_summary: String,
    val exit_code: Int?,
    val stderr_snippet: String?
)
