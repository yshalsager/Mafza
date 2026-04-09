package com.yshalsager.mafza.profile

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yshalsager.mafza.R
import com.yshalsager.mafza.core.contracts.BackupPreview

private const val BACKUP_MIN_PASSPHRASE_LENGTH = 12

internal enum class ExportBackupPassphraseValidation {
    VALID,
    TOO_SHORT,
    MISMATCH
}

internal fun validate_export_backup_passphrase(
    passphrase: String,
    passphrase_confirm: String
): ExportBackupPassphraseValidation {
    if (passphrase.length < BACKUP_MIN_PASSPHRASE_LENGTH) return ExportBackupPassphraseValidation.TOO_SHORT
    if (passphrase != passphrase_confirm) return ExportBackupPassphraseValidation.MISMATCH
    return ExportBackupPassphraseValidation.VALID
}

internal fun is_restore_backup_passphrase_valid(passphrase: String): Boolean = passphrase.isNotBlank()

@Composable
internal fun MessageBindingPickerDialog(
    picker_index: Int?,
    bindings: List<EditableMessageBinding>,
    options: List<AppChooserOption>,
    on_update_bindings: (List<EditableMessageBinding>) -> Unit,
    on_dismiss: () -> Unit,
    on_mark_profile_dirty: () -> Unit
) {
    val target_index = picker_index
    if (target_index == null || target_index !in bindings.indices) return

    SearchableAppChooserDialog(
        title = stringResource(R.string.profile_message_binding_picker_title),
        options = options,
        on_select = { selected ->
            on_update_bindings(
                bindings.mapIndexed { index, binding ->
                    if (index == target_index) {
                        binding.copy(
                            package_name = selected.package_name,
                            activity_name = selected.activity_name.orEmpty()
                        )
                    } else {
                        binding
                    }
                }
            )
            on_dismiss()
            on_mark_profile_dirty()
        },
        on_dismiss = on_dismiss
    )
}

@Composable
internal fun UninstallPackagePickerDialog(
    picker_index: Int?,
    uninstall_packages: List<String>,
    options: List<AppChooserOption>,
    on_update_packages: (List<String>) -> Unit,
    on_dismiss: () -> Unit,
    on_mark_profile_dirty: () -> Unit
) {
    val target_index = picker_index
    if (target_index == null || target_index !in uninstall_packages.indices) return

    SearchableAppChooserDialog(
        title = stringResource(R.string.profile_uninstall_pick_package_action),
        options = options,
        on_select = { selected ->
            on_update_packages(
                uninstall_packages.mapIndexed { index, package_name ->
                    if (index == target_index) selected.package_name else package_name
                }
            )
            on_dismiss()
            on_mark_profile_dirty()
        },
        on_dismiss = on_dismiss
    )
}

@Composable
internal fun DestructiveEnableConfirmDialog(
    show: Boolean,
    on_confirm: () -> Unit,
    on_dismiss: () -> Unit
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = on_dismiss,
        title = { Text(text = stringResource(R.string.profile_destructive_confirm_title)) },
        text = { Text(text = stringResource(R.string.profile_destructive_confirm_body)) },
        confirmButton = {
            TextButton(onClick = on_confirm) {
                Text(text = stringResource(R.string.profile_destructive_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = on_dismiss) {
                Text(text = stringResource(R.string.run_live_cancel_action))
            }
        }
    )
}

@Composable
internal fun SelfUninstallEnableConfirmDialog(
    show: Boolean,
    on_confirm: () -> Unit,
    on_dismiss: () -> Unit
) {
    if (!show) return
    AlertDialog(
        onDismissRequest = on_dismiss,
        title = { Text(text = stringResource(R.string.profile_self_uninstall_confirm_title)) },
        text = { Text(text = stringResource(R.string.profile_self_uninstall_confirm_body)) },
        confirmButton = {
            TextButton(onClick = on_confirm) {
                Text(text = stringResource(R.string.profile_self_uninstall_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = on_dismiss) {
                Text(text = stringResource(R.string.run_live_cancel_action))
            }
        }
    )
}

@Composable
internal fun ExportBackupDialog(
    show: Boolean,
    on_dismiss: () -> Unit,
    on_confirm_export: (passphrase: CharArray, include_history: Boolean) -> Unit
) {
    if (!show) return

    var passphrase by remember { mutableStateOf("") }
    var passphrase_confirm by remember { mutableStateOf("") }
    var include_history by remember { mutableStateOf(false) }
    val passphrase_validation = validate_export_backup_passphrase(passphrase, passphrase_confirm)
    val passphrase_error = when (passphrase_validation) {
        ExportBackupPassphraseValidation.TOO_SHORT -> stringResource(R.string.profile_backup_passphrase_short)
        ExportBackupPassphraseValidation.MISMATCH -> stringResource(R.string.profile_backup_passphrase_mismatch)
        ExportBackupPassphraseValidation.VALID -> null
    }

    AlertDialog(
        onDismissRequest = on_dismiss,
        title = { Text(text = stringResource(R.string.profile_backup_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(text = stringResource(R.string.profile_backup_passphrase_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
                OutlinedTextField(
                    value = passphrase_confirm,
                    onValueChange = { passphrase_confirm = it },
                    label = { Text(text = stringResource(R.string.profile_backup_passphrase_confirm_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    isError = passphrase_error != null
                )
                if (passphrase_error != null) {
                    Text(text = passphrase_error)
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = include_history,
                        onCheckedChange = { checked -> include_history = checked }
                    )
                    Text(text = stringResource(R.string.profile_backup_include_history))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    on_confirm_export(passphrase.toCharArray(), include_history)
                },
                enabled = passphrase_validation == ExportBackupPassphraseValidation.VALID
            ) {
                Text(text = stringResource(R.string.profile_backup_export_action))
            }
        },
        dismissButton = {
            TextButton(onClick = on_dismiss) {
                Text(text = stringResource(R.string.run_live_cancel_action))
            }
        }
    )
}

@Composable
internal fun RestoreBackupPassphraseDialog(
    show: Boolean,
    on_dismiss: () -> Unit,
    on_confirm_passphrase: (CharArray) -> Unit
) {
    if (!show) return

    var passphrase by remember { mutableStateOf("") }
    val passphrase_valid = is_restore_backup_passphrase_valid(passphrase)
    val passphrase_error = if (!passphrase_valid) {
        stringResource(R.string.profile_backup_passphrase_required)
    } else {
        null
    }
    AlertDialog(
        onDismissRequest = on_dismiss,
        title = { Text(text = stringResource(R.string.profile_backup_restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = stringResource(R.string.profile_backup_restore_description))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(text = stringResource(R.string.profile_backup_passphrase_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    isError = passphrase_error != null
                )
                if (passphrase_error != null) {
                    Text(text = passphrase_error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { on_confirm_passphrase(passphrase.toCharArray()) },
                enabled = passphrase_valid
            ) {
                Text(text = stringResource(R.string.profile_backup_continue_action))
            }
        },
        dismissButton = {
            TextButton(onClick = on_dismiss) {
                Text(text = stringResource(R.string.run_live_cancel_action))
            }
        }
    )
}

@Composable
internal fun RestoreBackupConfirmDialog(
    show: Boolean,
    preview: BackupPreview?,
    in_progress: Boolean,
    on_dismiss: () -> Unit,
    on_confirm_restore: () -> Unit
) {
    if (!show || preview == null) return

    AlertDialog(
        onDismissRequest = {
            if (!in_progress) on_dismiss()
        },
        title = { Text(text = stringResource(R.string.profile_backup_restore_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = stringResource(R.string.profile_backup_restore_preview_exported_at, format_backup_epoch(preview.exported_at_epoch_ms)))
                Text(text = stringResource(R.string.profile_backup_restore_preview_version, preview.app_version))
                Text(
                    text = stringResource(
                        R.string.profile_backup_restore_preview_history,
                        if (preview.include_history) preview.history_count else 0
                    )
                )
                Text(text = stringResource(R.string.profile_backup_restore_warning))
            }
        },
        confirmButton = {
            TextButton(
                onClick = on_confirm_restore,
                enabled = !in_progress
            ) {
                Text(text = stringResource(R.string.profile_backup_restore_action))
            }
        },
        dismissButton = {
            TextButton(
                onClick = on_dismiss,
                enabled = !in_progress
            ) {
                Text(text = stringResource(R.string.run_live_cancel_action))
            }
        }
    )
}

private fun format_backup_epoch(epoch_ms: Long): String {
    return java.time.Instant.ofEpochMilli(epoch_ms)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
}
