package com.yshalsager.mafza.profile

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yshalsager.mafza.R
import com.yshalsager.mafza.core.contracts.ActionBinding
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.emergency.providers.ActionProviderRegistry
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileActionEditorSheet(
    app_context: Context,
    action_provider_registry: ActionProviderRegistry,
    editing_action_row: ProfileActionRow?,
    sms_recipients: List<String>,
    sms_contact_picker_error_res_id: Int?,
    message_app_bindings: List<EditableMessageBinding>,
    intent_actions: List<EditableIntentAction>,
    uninstall_packages: List<String>,
    delete_targets: List<EditableDeleteTarget>,
    delete_target_picker_error_res_id: Int?,
    advanced_shell_commands: List<EditableShellCommand>,
    self_uninstall_enabled: Boolean,
    on_dismiss: () -> Unit,
    on_mark_profile_dirty: () -> Unit,
    on_update_sms_recipients: (List<String>) -> Unit,
    on_update_sms_contact_picker_error: (Int?) -> Unit,
    on_pick_sms_contact: (Int) -> Unit,
    on_update_message_app_bindings: (List<EditableMessageBinding>) -> Unit,
    on_open_message_binding_picker: (Int) -> Unit,
    on_update_intent_actions: (List<EditableIntentAction>) -> Unit,
    on_update_uninstall_packages: (List<String>) -> Unit,
    on_open_uninstall_package_picker: (Int) -> Unit,
    on_update_delete_targets: (List<EditableDeleteTarget>) -> Unit,
    on_update_delete_target_picker_error: (Int?) -> Unit,
    on_pick_delete_file: (Int) -> Unit,
    on_pick_delete_directory: (Int) -> Unit,
    on_update_advanced_shell_commands: (List<EditableShellCommand>) -> Unit,
    on_toggle_self_uninstall_enabled: (Boolean) -> Unit,
    action_rule_for: (ActionId) -> EditableActionPolicyRow,
    on_update_action_rule: (ActionId, EditableActionPolicyRow) -> Unit
) {
    if (editing_action_row == null) return

    val app_scope = rememberCoroutineScope()
    val editing_message_binding = (editing_action_row as? MessageBindingActionRow)?.let { row ->
        message_app_bindings.getOrNull(row.item_index)
    }
    var show_advanced_execution_rule by remember(editing_action_row.row_id) { mutableStateOf(false) }
    var message_binding_test_status by remember(editing_action_row.row_id, editing_message_binding) { mutableStateOf<String?>(null) }
    var intent_action_test_status by remember(editing_action_row.row_id) { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = on_dismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(profile_action_row_type_label_res(editing_action_row.row_type)),
                style = MaterialTheme.typography.titleMedium
            )
            when (editing_action_row) {
                is SmsRecipientActionRow -> SmsRecipientEditorSection(
                    row = editing_action_row,
                    sms_recipients = sms_recipients,
                    sms_contact_picker_error_res_id = sms_contact_picker_error_res_id,
                    on_update_sms_recipients = on_update_sms_recipients,
                    on_update_sms_contact_picker_error = on_update_sms_contact_picker_error,
                    on_pick_sms_contact = on_pick_sms_contact,
                    show_advanced_execution_rule = show_advanced_execution_rule,
                    on_toggle_advanced = { show_advanced_execution_rule = !show_advanced_execution_rule },
                    action_rule_for = action_rule_for,
                    on_update_action_rule = on_update_action_rule,
                    on_mark_profile_dirty = on_mark_profile_dirty
                )
                is MessageBindingActionRow -> MessageBindingEditorSection(
                    row = editing_action_row,
                    message_app_bindings = message_app_bindings,
                    on_update_message_app_bindings = on_update_message_app_bindings,
                    on_open_message_binding_picker = on_open_message_binding_picker,
                    test_status = message_binding_test_status,
                    on_test_binding = {
                        val binding = message_app_bindings.getOrNull(editing_action_row.item_index)
                        if (binding == null) {
                            message_binding_test_status = "preflight_failed"
                            return@MessageBindingEditorSection
                        }

                        val package_name = binding.package_name.trim()
                        if (package_name.isEmpty()) {
                            message_binding_test_status = "missing_package"
                            return@MessageBindingEditorSection
                        }

                        app_scope.launch {
                            val provider = action_provider_registry.provider_for(ActionId.NOTIFY_MESSAGE_APP)
                            if (provider == null) {
                                message_binding_test_status = "no_provider"
                                return@launch
                            }

                            val preflight_result = runCatching {
                                provider.preflight(
                                    ActionBinding(
                                        action_id = ActionId.NOTIFY_MESSAGE_APP,
                                        binding_id = binding.binding_id,
                                        package_name = package_name,
                                        activity_name = binding.activity_name.trim().ifEmpty { null },
                                        enabled = true
                                    )
                                )
                            }.getOrNull()

                            message_binding_test_status = if (preflight_result == null) {
                                "preflight_failed"
                            } else if (preflight_result.ready) {
                                "ready"
                            } else {
                                preflight_result.blocking_reason ?: "preflight_failed"
                            }
                        }
                    },
                    on_clear_test_status = { message_binding_test_status = null },
                    show_advanced_execution_rule = show_advanced_execution_rule,
                    on_toggle_advanced = { show_advanced_execution_rule = !show_advanced_execution_rule },
                    on_mark_profile_dirty = on_mark_profile_dirty
                )
                is IntentActionRow -> IntentActionEditorSection(
                    row = editing_action_row,
                    intent_actions = intent_actions,
                    on_update_intent_actions = on_update_intent_actions,
                    test_status = intent_action_test_status,
                    on_test_intent = {
                        val intent_action = intent_actions.getOrNull(editing_action_row.item_index)
                        if (intent_action == null) {
                            intent_action_test_status = "unknown"
                        } else {
                            intent_action_test_status = test_intent_action(app_context, intent_action)
                        }
                    },
                    on_clear_test_status = { intent_action_test_status = null },
                    show_advanced_execution_rule = show_advanced_execution_rule,
                    on_toggle_advanced = { show_advanced_execution_rule = !show_advanced_execution_rule },
                    on_mark_profile_dirty = on_mark_profile_dirty
                )
                is UninstallPackageActionRow -> UninstallPackageEditorSection(
                    row = editing_action_row,
                    uninstall_packages = uninstall_packages,
                    on_update_uninstall_packages = on_update_uninstall_packages,
                    on_open_uninstall_package_picker = on_open_uninstall_package_picker,
                    show_advanced_execution_rule = show_advanced_execution_rule,
                    on_toggle_advanced = { show_advanced_execution_rule = !show_advanced_execution_rule },
                    action_rule_for = action_rule_for,
                    on_update_action_rule = on_update_action_rule,
                    on_mark_profile_dirty = on_mark_profile_dirty
                )
                is DeleteTargetActionRow -> DeleteTargetEditorSection(
                    row = editing_action_row,
                    delete_targets = delete_targets,
                    delete_target_picker_error_res_id = delete_target_picker_error_res_id,
                    on_update_delete_targets = on_update_delete_targets,
                    on_update_delete_target_picker_error = on_update_delete_target_picker_error,
                    on_pick_delete_file = on_pick_delete_file,
                    on_pick_delete_directory = on_pick_delete_directory,
                    show_advanced_execution_rule = show_advanced_execution_rule,
                    on_toggle_advanced = { show_advanced_execution_rule = !show_advanced_execution_rule },
                    action_rule_for = action_rule_for,
                    on_update_action_rule = on_update_action_rule,
                    on_mark_profile_dirty = on_mark_profile_dirty
                )
                is ShellCommandActionRow -> ShellCommandEditorSection(
                    row = editing_action_row,
                    advanced_shell_commands = advanced_shell_commands,
                    on_update_advanced_shell_commands = on_update_advanced_shell_commands,
                    show_advanced_execution_rule = show_advanced_execution_rule,
                    on_toggle_advanced = { show_advanced_execution_rule = !show_advanced_execution_rule },
                    action_rule_for = action_rule_for,
                    on_update_action_rule = on_update_action_rule,
                    on_mark_profile_dirty = on_mark_profile_dirty
                )
                is SelfUninstallActionRow -> SelfUninstallEditorSection(
                    self_uninstall_enabled = self_uninstall_enabled,
                    on_toggle_self_uninstall_enabled = on_toggle_self_uninstall_enabled,
                    show_advanced_execution_rule = show_advanced_execution_rule,
                    on_toggle_advanced = { show_advanced_execution_rule = !show_advanced_execution_rule },
                    action_rule_for = action_rule_for,
                    on_update_action_rule = on_update_action_rule,
                    on_mark_profile_dirty = on_mark_profile_dirty
                )
            }
            TextButton(
                onClick = on_dismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.profile_editor_done_action))
            }
        }
    }
}
