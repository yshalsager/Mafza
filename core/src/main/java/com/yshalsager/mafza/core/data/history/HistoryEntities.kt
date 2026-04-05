package com.yshalsager.mafza.core.data.history

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.RunStatus
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TriggerSource

@Entity(tableName = "run_history")
data class RunHistoryEntity(
    @PrimaryKey val run_id: String,
    val started_at_epoch_ms: Long,
    val completed_at_epoch_ms: Long?,
    val trigger: TriggerSource,
    val mode: ExecutionMode,
    val status: RunStatus
)

@Entity(
    tableName = "step_history",
    primaryKeys = ["run_id", "step_index"],
    foreignKeys = [
        ForeignKey(
            entity = RunHistoryEntity::class,
            parentColumns = ["run_id"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["run_id"])]
)
data class StepHistoryEntity(
    val run_id: String,
    val step_index: Int,
    val step_id: String,
    val status: StepStatus,
    val details: String?,
    val started_at_epoch_ms: Long,
    val finished_at_epoch_ms: Long
)

@Entity(
    tableName = "command_audit",
    primaryKeys = ["run_id", "step_index", "command_index"],
    foreignKeys = [
        ForeignKey(
            entity = RunHistoryEntity::class,
            parentColumns = ["run_id"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["run_id"])]
)
data class CommandAuditEntity(
    val run_id: String,
    val step_index: Int,
    val command_index: Int,
    val action_id: ActionId,
    val target_summary: String,
    val exit_code: Int?,
    val stderr_snippet: String?
)
