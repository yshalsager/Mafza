package com.yshalsager.mafza.core.contracts

import android.net.Uri
import kotlinx.serialization.Serializable

@Serializable
data class RunHistoryExportItem(
    val run_id: RunId,
    val started_at_epoch_ms: Long,
    val completed_at_epoch_ms: Long,
    val trigger: TriggerSource,
    val mode: ExecutionMode,
    val status: RunStatus,
    val steps: List<RunHistoryStepExportItem> = emptyList(),
    val command_audits: List<RunHistoryCommandAuditExportItem> = emptyList()
)

@Serializable
data class RunHistoryStepExportItem(
    val step_index: Int,
    val step_id: String,
    val status: StepStatus,
    val details: String?,
    val started_at_epoch_ms: Long,
    val finished_at_epoch_ms: Long
)

@Serializable
data class RunHistoryCommandAuditExportItem(
    val step_index: Int,
    val command_index: Int,
    val action_id: ActionId,
    val target_summary: String,
    val exit_code: Int?,
    val stderr_snippet: String?
)

@Serializable
data class BackupPayload(
    val schema_version: Int,
    val exported_at_epoch_ms: Long,
    val app_version: String,
    val profile: EmergencyProfile,
    val include_history: Boolean,
    val history: List<RunHistoryExportItem> = emptyList()
)

data class BackupPreview(
    val exported_at_epoch_ms: Long,
    val app_version: String,
    val include_history: Boolean,
    val history_count: Int
)

interface BackupService {
    fun exportEncryptedBackup(output_uri: Uri, passphrase: CharArray, includeHistory: Boolean): Uri
    fun readBackupPreview(uri: Uri, passphrase: CharArray): BackupPreview
    fun restoreEncryptedBackup(uri: Uri, passphrase: CharArray): RestoreResult
}

data class RestoreResult(
    val success: Boolean,
    val restored_history_count: Int = 0,
    val error_message: String? = null
)
