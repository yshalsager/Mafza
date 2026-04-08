package com.yshalsager.mafza.profile

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yshalsager.mafza.R

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
