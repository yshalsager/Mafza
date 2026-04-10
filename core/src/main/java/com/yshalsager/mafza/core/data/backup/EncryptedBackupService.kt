package com.yshalsager.mafza.core.data.backup

import android.content.Context
import android.net.Uri
import com.yshalsager.mafza.core.contracts.BackupPayload
import com.yshalsager.mafza.core.contracts.BackupPreview
import com.yshalsager.mafza.core.contracts.BackupService
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.RestoreResult
import com.yshalsager.mafza.core.contracts.RunHistoryExportItem
import com.yshalsager.mafza.core.data.history.RunHistoryStore
import com.yshalsager.mafza.core.data.profile.EncryptedProfileStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Instant

private const val MIN_EXPORT_PASSPHRASE_LENGTH = 12

const val BACKUP_FILE_EXTENSION = ".mafza.bak"

class EncryptedBackupService(
    private val app_context: Context,
    private val profile_store: EncryptedProfileStore,
    private val history_store: RunHistoryStore,
    private val now_epoch_ms: () -> Long = { Instant.now().toEpochMilli() },
    private val app_version: () -> String = { resolve_app_version(app_context) },
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) : BackupService {
    override fun exportEncryptedBackup(output_uri: Uri, passphrase: CharArray, includeHistory: Boolean): Uri {
        require(passphrase.size >= MIN_EXPORT_PASSPHRASE_LENGTH) {
            "passphrase must be at least $MIN_EXPORT_PASSPHRASE_LENGTH characters"
        }

        val payload = runBlocking {
            val profile = profile_store.read_profile()
            val history = if (includeHistory) {
                history_store.export_runs_for_backup()
            } else {
                emptyList()
            }
            BackupPayload(
                schema_version = BACKUP_SCHEMA_VERSION,
                exported_at_epoch_ms = now_epoch_ms(),
                app_version = app_version(),
                profile = profile,
                include_history = includeHistory,
                history = history
            )
        }

        val payload_bytes = json.encodeToString(BackupPayload.serializer(), payload).encodeToByteArray()
        val envelope = BackupCrypto.encrypt_payload(
            payload_bytes = payload_bytes,
            passphrase = passphrase,
            schema_version = BACKUP_SCHEMA_VERSION
        )
        write_encrypted_envelope(output_uri, envelope)
        return output_uri
    }

    override fun readBackupPreview(uri: Uri, passphrase: CharArray): BackupPreview {
        val payload = decode_backup_payload(uri, passphrase)
        return BackupPreview(
            exported_at_epoch_ms = payload.exported_at_epoch_ms,
            app_version = payload.app_version,
            include_history = payload.include_history,
            history_count = payload.history.size
        )
    }

    override fun restoreEncryptedBackup(uri: Uri, passphrase: CharArray): RestoreResult {
        if (passphrase.isEmpty()) {
            return RestoreResult(
                success = false,
                restored_history_count = 0,
                error_message = "Passphrase is required"
            )
        }

        return runCatching {
            val payload = decode_backup_payload(uri, passphrase)
            if (payload.schema_version != BACKUP_SCHEMA_VERSION) {
                return@runCatching RestoreResult(
                    success = false,
                    error_message = "Unsupported backup schema: ${payload.schema_version}"
                )
            }

            val restored_history_count = runBlocking {
                apply_restored_payload_with_rollback(
                    payload = payload,
                    read_profile = { profile_store.read_profile() },
                    write_profile = { profile_store.write_profile(it) },
                    export_history = { history_store.export_runs_for_backup() },
                    replace_history = { history_store.replace_runs_from_backup(it) }
                )
            }

            RestoreResult(
                success = true,
                restored_history_count = restored_history_count,
                error_message = null
            )
        }.getOrElse { error ->
            RestoreResult(
                success = false,
                restored_history_count = 0,
                error_message = error.message ?: "Failed to restore backup"
            )
        }
    }

    private fun decode_backup_payload(uri: Uri, passphrase: CharArray): BackupPayload {
        val envelope = read_encrypted_envelope(uri)
        val plain_bytes = BackupCrypto.decrypt_payload(envelope, passphrase)
        return json.decodeFromString(
            BackupPayload.serializer(),
            plain_bytes.decodeToString()
        )
    }

    private fun write_encrypted_envelope(uri: Uri, envelope: EncryptedBackupEnvelope) {
        val bytes = json.encodeToString(EncryptedBackupEnvelope.serializer(), envelope).encodeToByteArray()
        val output_stream = app_context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Cannot open backup destination")
        output_stream.use { stream ->
            stream.write(bytes)
            stream.flush()
        }
    }

    private fun read_encrypted_envelope(uri: Uri): EncryptedBackupEnvelope {
        val input_stream = app_context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open backup file")
        val bytes = input_stream.use { stream -> stream.readBytes() }
        if (bytes.isEmpty()) {
            throw IOException("Backup file is empty")
        }
        return json.decodeFromString(
            EncryptedBackupEnvelope.serializer(),
            bytes.decodeToString()
        )
    }
}

internal suspend fun apply_restored_payload_with_rollback(
    payload: BackupPayload,
    read_profile: suspend () -> EmergencyProfile,
    write_profile: suspend (EmergencyProfile) -> Unit,
    export_history: suspend () -> List<RunHistoryExportItem>,
    replace_history: suspend (List<RunHistoryExportItem>) -> Unit
): Int {
    val current_profile = read_profile()
    val current_history = if (payload.include_history) export_history() else emptyList()
    runCatching {
        write_profile(payload.profile)
        if (payload.include_history) {
            replace_history(payload.history)
        }
    }.onFailure {
        write_profile(current_profile)
        if (payload.include_history) {
            replace_history(current_history)
        }
        throw it
    }
    return if (payload.include_history) payload.history.size else 0
}

private fun resolve_app_version(context: Context): String {
    return runCatching {
        val package_info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        package_info.versionName ?: "unknown"
    }.getOrDefault("unknown")
}
