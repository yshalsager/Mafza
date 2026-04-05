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
    val status: RunStatus
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

interface BackupService {
    fun exportEncryptedBackup(passphrase: CharArray, includeHistory: Boolean): Uri
    fun restoreEncryptedBackup(uri: Uri, passphrase: CharArray): RestoreResult
}

data class RestoreResult(
    val success: Boolean,
    val restored_history_count: Int = 0,
    val error_message: String? = null
)
