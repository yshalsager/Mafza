package com.yshalsager.mafza.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yshalsager.mafza.R
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ActionPolicyKeys

@Composable
internal fun SmsRecipientEditorSection(
    row: SmsRecipientActionRow,
    sms_recipients: List<String>,
    sms_contact_picker_error_res_id: Int?,
    on_update_sms_recipients: (List<String>) -> Unit,
    on_update_sms_contact_picker_error: (Int?) -> Unit,
    on_pick_sms_contact: (Int) -> Unit,
    show_advanced_execution_rule: Boolean,
    on_toggle_advanced: () -> Unit,
    action_rule_for: (ActionId) -> EditableActionPolicyRow,
    on_update_action_rule: (ActionId, EditableActionPolicyRow) -> Unit,
    on_mark_profile_dirty: () -> Unit
) {
    val current_row = sms_recipients.getOrNull(row.item_index).orEmpty()
    OutlinedTextField(
        value = current_row,
        onValueChange = { value ->
            on_update_sms_recipients(
                sms_recipients.mapIndexed { index, recipient ->
                    if (index == row.item_index) value else recipient
                }
            )
            on_update_sms_contact_picker_error(null)
            on_mark_profile_dirty()
        },
        label = { Text(text = stringResource(R.string.profile_sms_recipient_number_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    TextButton(
        onClick = { on_pick_sms_contact(row.item_index) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = stringResource(R.string.profile_sms_recipient_pick_contact_action))
    }
    if (sms_contact_picker_error_res_id != null) {
        Text(
            text = stringResource(sms_contact_picker_error_res_id),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    AdvancedExecutionRuleToggle(
        show_advanced_execution_rule = show_advanced_execution_rule,
        on_toggle_advanced = on_toggle_advanced
    )
    if (show_advanced_execution_rule) {
        val action_rule = action_rule_for(ActionId.SEND_SMS)
        ExecutionRuleEditor(
            rule = action_rule,
            on_update = { updated_rule ->
                on_update_action_rule(ActionId.SEND_SMS, updated_rule)
            }
        )
    }
}

@Composable
internal fun MessageBindingEditorSection(
    row: MessageBindingActionRow,
    message_app_bindings: List<EditableMessageBinding>,
    on_update_message_app_bindings: (List<EditableMessageBinding>) -> Unit,
    on_open_message_binding_picker: (Int) -> Unit,
    test_status: String?,
    on_test_binding: () -> Unit,
    on_clear_test_status: () -> Unit,
    show_advanced_execution_rule: Boolean,
    on_toggle_advanced: () -> Unit,
    on_mark_profile_dirty: () -> Unit
) {
    val binding = message_app_bindings.getOrNull(row.item_index) ?: return

    OutlinedTextField(
        value = binding.package_name,
        onValueChange = { value ->
            on_update_message_app_bindings(
                message_app_bindings.update_item(row.item_index) { current_binding ->
                    current_binding.copy(package_name = value)
                }
            )
            on_clear_test_status()
            on_mark_profile_dirty()
        },
        label = { Text(text = stringResource(R.string.profile_message_binding_package_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = binding.activity_name,
        onValueChange = { value ->
            on_update_message_app_bindings(
                message_app_bindings.update_item(row.item_index) { current_binding ->
                    current_binding.copy(activity_name = value)
                }
            )
            on_clear_test_status()
            on_mark_profile_dirty()
        },
        label = { Text(text = stringResource(R.string.profile_message_binding_activity_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    TextButton(
        onClick = { on_open_message_binding_picker(row.item_index) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = stringResource(R.string.profile_message_binding_picker_title))
    }
    TextButton(
        onClick = on_test_binding,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = stringResource(R.string.profile_message_binding_test_action))
    }
    if (test_status != null) {
        val status_res_id = message_binding_test_status_res(test_status)
        Text(
            text = stringResource(R.string.profile_message_binding_test_result_prefix) + stringResource(status_res_id),
            style = MaterialTheme.typography.bodySmall,
            color = if (test_status == "ready") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.profile_message_binding_enabled_label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = binding.enabled,
            onCheckedChange = { enabled ->
                on_update_message_app_bindings(
                    message_app_bindings.update_item(row.item_index) { current_binding ->
                        current_binding.copy(enabled = enabled)
                    }
                )
                on_clear_test_status()
                on_mark_profile_dirty()
            }
        )
    }
    AdvancedExecutionRuleToggle(
        show_advanced_execution_rule = show_advanced_execution_rule,
        on_toggle_advanced = on_toggle_advanced
    )
    if (show_advanced_execution_rule) {
        PolicyModeSelector(
            policy_mode = binding.policy_mode,
            on_select_mode = { selected_mode ->
                on_update_message_app_bindings(
                    message_app_bindings.update_item(row.item_index) { current_binding ->
                        current_binding.copy(policy_mode = selected_mode)
                    }
                )
                on_mark_profile_dirty()
            }
        )
        if (binding.policy_mode == ProfilePolicyMode.OVERRIDE) {
            ExecutionRuleEditor(
                rule = EditableActionPolicyRow(
                    action_id = ActionId.NOTIFY_MESSAGE_APP,
                    policy_key = "binding:${ActionId.NOTIFY_MESSAGE_APP.name.lowercase()}:${binding.binding_id}",
                    enabled = binding.policy_enabled,
                    required = binding.policy_required,
                    continue_on_failure = binding.policy_continue_on_failure,
                    execution_order = parse_int_or_fallback(
                        value = binding.policy_execution_order,
                        fallback = row.item_index + 1,
                        min_value = PROFILE_MIN_POLICY_ORDER,
                        max_value = PROFILE_MAX_POLICY_ORDER
                    )
                ),
                on_update = { updated_rule ->
                    on_update_message_app_bindings(
                        message_app_bindings.update_item(row.item_index) { current_binding ->
                            current_binding.copy(
                                policy_enabled = updated_rule.enabled,
                                policy_required = updated_rule.required,
                                policy_continue_on_failure = updated_rule.continue_on_failure,
                                policy_execution_order = updated_rule.execution_order.toString()
                            )
                        }
                    )
                    on_mark_profile_dirty()
                }
            )
        }
    }
}

@Composable
internal fun TelegramBotActionEditorSection(
    row: TelegramBotActionRow,
    telegram_bot_actions: List<EditableTelegramBotAction>,
    on_update_telegram_bot_actions: (List<EditableTelegramBotAction>) -> Unit,
    test_status: String?,
    on_test_bot: () -> Unit,
    on_clear_test_status: () -> Unit,
    show_advanced_execution_rule: Boolean,
    on_toggle_advanced: () -> Unit,
    on_mark_profile_dirty: () -> Unit
) {
    val action = telegram_bot_actions.getOrNull(row.item_index) ?: return

    OutlinedTextField(
        value = action.label,
        onValueChange = { value ->
            on_update_telegram_bot_actions(
                telegram_bot_actions.update_item(row.item_index) { current_action ->
                    current_action.copy(label = value)
                }
            )
            on_clear_test_status()
            on_mark_profile_dirty()
        },
        label = { Text(text = stringResource(R.string.profile_telegram_bot_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = action.bot_token,
        onValueChange = { value ->
            on_update_telegram_bot_actions(
                telegram_bot_actions.update_item(row.item_index) { current_action ->
                    current_action.copy(bot_token = value)
                }
            )
            on_clear_test_status()
            on_mark_profile_dirty()
        },
        label = { Text(text = stringResource(R.string.profile_telegram_bot_token_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = action.chat_id,
        onValueChange = { value ->
            on_update_telegram_bot_actions(
                telegram_bot_actions.update_item(row.item_index) { current_action ->
                    current_action.copy(chat_id = value)
                }
            )
            on_clear_test_status()
            on_mark_profile_dirty()
        },
        label = { Text(text = stringResource(R.string.profile_telegram_bot_chat_id_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = action.template_override,
        onValueChange = { value ->
            on_update_telegram_bot_actions(
                telegram_bot_actions.update_item(row.item_index) { current_action ->
                    current_action.copy(template_override = value)
                }
            )
            on_mark_profile_dirty()
        },
        label = { Text(text = stringResource(R.string.profile_telegram_bot_template_override_label)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2
    )
    OutlinedTextField(
        value = action.timeout_seconds,
        onValueChange = { value ->
            on_update_telegram_bot_actions(
                telegram_bot_actions.update_item(row.item_index) { current_action ->
                    current_action.copy(timeout_seconds = value)
                }
            )
            on_mark_profile_dirty()
        },
        label = { Text(text = stringResource(R.string.profile_telegram_bot_timeout_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    TextButton(
        onClick = on_test_bot,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = stringResource(R.string.profile_telegram_bot_test_action))
    }
    if (test_status != null) {
        val (status_text, is_success) = if (test_status.startsWith("error:")) {
            test_status.removePrefix("error:") to false
        } else {
            val status_res_id = telegram_bot_test_status_res(test_status)
            stringResource(status_res_id) to (test_status == "ready")
        }
        Text(
            text = stringResource(R.string.profile_telegram_bot_test_result_prefix) + status_text,
            style = MaterialTheme.typography.bodySmall,
            color = if (is_success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.profile_telegram_bot_enabled_label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = action.enabled,
            onCheckedChange = { enabled ->
                on_update_telegram_bot_actions(
                    telegram_bot_actions.update_item(row.item_index) { current_action ->
                        current_action.copy(enabled = enabled)
                    }
                )
                on_mark_profile_dirty()
            }
        )
    }
    AdvancedExecutionRuleToggle(
        show_advanced_execution_rule = show_advanced_execution_rule,
        on_toggle_advanced = on_toggle_advanced
    )
    if (show_advanced_execution_rule) {
        PolicyModeSelector(
            policy_mode = action.policy_mode,
            on_select_mode = { selected_mode ->
                on_update_telegram_bot_actions(
                    telegram_bot_actions.update_item(row.item_index) { current_action ->
                        current_action.copy(policy_mode = selected_mode)
                    }
                )
                on_mark_profile_dirty()
            }
        )
        if (action.policy_mode == ProfilePolicyMode.OVERRIDE) {
            ExecutionRuleEditor(
                rule = EditableActionPolicyRow(
                    action_id = ActionId.NOTIFY_TELEGRAM_BOT,
                    policy_key = ActionPolicyKeys.for_telegram_bot(action.id),
                    enabled = action.policy_enabled,
                    required = action.policy_required,
                    continue_on_failure = action.policy_continue_on_failure,
                    execution_order = parse_int_or_fallback(
                        value = action.policy_execution_order,
                        fallback = row.item_index + 1,
                        min_value = PROFILE_MIN_POLICY_ORDER,
                        max_value = PROFILE_MAX_POLICY_ORDER
                    )
                ),
                on_update = { updated_rule ->
                    on_update_telegram_bot_actions(
                        telegram_bot_actions.update_item(row.item_index) { current_action ->
                            current_action.copy(
                                policy_enabled = updated_rule.enabled,
                                policy_required = updated_rule.required,
                                policy_continue_on_failure = updated_rule.continue_on_failure,
                                policy_execution_order = updated_rule.execution_order.toString()
                            )
                        }
                    )
                    on_mark_profile_dirty()
                }
            )
        }
    }
}

@Composable
internal fun IntentActionEditorSection(
    row: IntentActionRow,
    intent_actions: List<EditableIntentAction>,
    on_update_intent_actions: (List<EditableIntentAction>) -> Unit,
    test_status: String?,
    on_test_intent: () -> Unit,
    on_clear_test_status: () -> Unit,
    show_advanced_execution_rule: Boolean,
    on_toggle_advanced: () -> Unit,
    on_mark_profile_dirty: () -> Unit
) {
    val intent_action = intent_actions.getOrNull(row.item_index) ?: return

    OutlinedTextField(
        value = intent_action.label,
        onValueChange = { value ->
            on_update_intent_actions(
                intent_actions.update_item(row.item_index) { current_intent ->
                    current_intent.copy(label = value)
                }
            )
            on_clear_test_status()
            on_mark_profile_dirty()
        },
        label = { Text(text = stringResource(R.string.profile_intent_action_label_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = intent_action.intent_action,
        onValueChange = { value ->
            on_update_intent_actions(
                intent_actions.update_item(row.item_index) { current_intent ->
                    current_intent.copy(intent_action = value)
                }
            )
            on_clear_test_status()
            on_mark_profile_dirty()
        },
        label = { Text(text = stringResource(R.string.profile_intent_action_action_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = intent_action.data_uri,
        onValueChange = { value ->
            on_update_intent_actions(
                intent_actions.update_item(row.item_index) { current_intent ->
                    current_intent.copy(data_uri = value)
                }
            )
            on_clear_test_status()
            on_mark_profile_dirty()
        },
        label = { Text(text = stringResource(R.string.profile_intent_action_data_uri_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    TextButton(
        onClick = on_test_intent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = stringResource(R.string.profile_intent_action_test_action))
    }
    if (test_status != null) {
        val status_res_id = intent_test_status_res(test_status)
        Text(
            text = stringResource(R.string.profile_intent_action_test_result_prefix) + stringResource(status_res_id),
            style = MaterialTheme.typography.bodySmall,
            color = if (test_status == "resolvable") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.profile_intent_action_enabled_label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = intent_action.enabled,
            onCheckedChange = { enabled ->
                on_update_intent_actions(
                    intent_actions.update_item(row.item_index) { current_intent ->
                        current_intent.copy(enabled = enabled)
                    }
                )
                on_mark_profile_dirty()
            }
        )
    }
    AdvancedExecutionRuleToggle(
        show_advanced_execution_rule = show_advanced_execution_rule,
        on_toggle_advanced = on_toggle_advanced
    )
    if (show_advanced_execution_rule) {
        PolicyModeSelector(
            policy_mode = intent_action.policy_mode,
            on_select_mode = { selected_mode ->
                on_update_intent_actions(
                    intent_actions.update_item(row.item_index) { current_intent ->
                        current_intent.copy(policy_mode = selected_mode)
                    }
                )
                on_mark_profile_dirty()
            }
        )
        if (intent_action.policy_mode == ProfilePolicyMode.OVERRIDE) {
            ExecutionRuleEditor(
                rule = EditableActionPolicyRow(
                    action_id = ActionId.LAUNCH_INTENT,
                    policy_key = ActionPolicyKeys.for_intent(intent_action.id),
                    enabled = intent_action.policy_enabled,
                    required = intent_action.policy_required,
                    continue_on_failure = intent_action.policy_continue_on_failure,
                    execution_order = parse_int_or_fallback(
                        value = intent_action.policy_execution_order,
                        fallback = row.item_index + 1,
                        min_value = PROFILE_MIN_POLICY_ORDER,
                        max_value = PROFILE_MAX_POLICY_ORDER
                    )
                ),
                on_update = { updated_rule ->
                    on_update_intent_actions(
                        intent_actions.update_item(row.item_index) { current_intent ->
                            current_intent.copy(
                                policy_enabled = updated_rule.enabled,
                                policy_required = updated_rule.required,
                                policy_continue_on_failure = updated_rule.continue_on_failure,
                                policy_execution_order = updated_rule.execution_order.toString()
                            )
                        }
                    )
                    on_mark_profile_dirty()
                }
            )
        }
    }
}

@Composable
internal fun UninstallPackageEditorSection(
    row: UninstallPackageActionRow,
    uninstall_packages: List<String>,
    on_update_uninstall_packages: (List<String>) -> Unit,
    on_open_uninstall_package_picker: (Int) -> Unit,
    show_advanced_execution_rule: Boolean,
    on_toggle_advanced: () -> Unit,
    action_rule_for: (ActionId) -> EditableActionPolicyRow,
    on_update_action_rule: (ActionId, EditableActionPolicyRow) -> Unit,
    on_mark_profile_dirty: () -> Unit
) {
    val package_name = uninstall_packages.getOrNull(row.item_index).orEmpty()
    OutlinedTextField(
        value = package_name,
        onValueChange = { value ->
            on_update_uninstall_packages(
                uninstall_packages.update_item(row.item_index) { value }
            )
            on_mark_profile_dirty()
        },
        label = { Text(text = stringResource(R.string.profile_uninstall_package_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    TextButton(
        onClick = { on_open_uninstall_package_picker(row.item_index) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = stringResource(R.string.profile_uninstall_pick_package_action))
    }
    AdvancedExecutionRuleToggle(
        show_advanced_execution_rule = show_advanced_execution_rule,
        on_toggle_advanced = on_toggle_advanced
    )
    if (show_advanced_execution_rule) {
        val action_rule = action_rule_for(ActionId.UNINSTALL_APPS)
        ExecutionRuleEditor(
            rule = action_rule,
            on_update = { updated_rule ->
                on_update_action_rule(ActionId.UNINSTALL_APPS, updated_rule)
            }
        )
    }
}

@Composable
internal fun DeleteTargetEditorSection(
    row: DeleteTargetActionRow,
    delete_targets: List<EditableDeleteTarget>,
    delete_target_picker_error_res_id: Int?,
    on_update_delete_targets: (List<EditableDeleteTarget>) -> Unit,
    on_update_delete_target_picker_error: (Int?) -> Unit,
    on_pick_delete_file: (Int) -> Unit,
    on_pick_delete_directory: (Int) -> Unit,
    show_advanced_execution_rule: Boolean,
    on_toggle_advanced: () -> Unit,
    action_rule_for: (ActionId) -> EditableActionPolicyRow,
    on_update_action_rule: (ActionId, EditableActionPolicyRow) -> Unit,
    on_mark_profile_dirty: () -> Unit
) {
    val delete_target = delete_targets.getOrNull(row.item_index) ?: return

    OutlinedTextField(
        value = delete_target.path,
        onValueChange = { value ->
            on_update_delete_targets(
                delete_targets.update_item(row.item_index) { current_target ->
                    current_target.copy(
                        path = value,
                        content_uri = if (value.trim().isNotEmpty()) "" else current_target.content_uri
                    )
                }
            )
            on_update_delete_target_picker_error(null)
            on_mark_profile_dirty()
        },
        label = { Text(text = stringResource(R.string.profile_delete_target_path_label)) },
        placeholder = { Text(text = stringResource(R.string.profile_delete_target_path_placeholder)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = delete_target.content_uri,
        onValueChange = { value ->
            on_update_delete_targets(
                delete_targets.update_item(row.item_index) { current_target ->
                    current_target.copy(
                        content_uri = value,
                        path = if (value.trim().isNotEmpty()) "" else current_target.path
                    )
                }
            )
            on_update_delete_target_picker_error(null)
            on_mark_profile_dirty()
        },
        label = { Text(text = stringResource(R.string.profile_delete_target_content_uri_label)) },
        placeholder = { Text(text = stringResource(R.string.profile_delete_target_content_uri_placeholder)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.profile_delete_target_recursive_label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = delete_target.recursive,
            onCheckedChange = { recursive ->
                on_update_delete_targets(
                    delete_targets.update_item(row.item_index) { current_target ->
                        current_target.copy(recursive = recursive)
                    }
                )
                on_mark_profile_dirty()
            }
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(
            onClick = { on_pick_delete_file(row.item_index) },
            modifier = Modifier.weight(1f)
        ) {
            Text(text = stringResource(R.string.profile_delete_pick_file_action))
        }
        TextButton(
            onClick = { on_pick_delete_directory(row.item_index) },
            modifier = Modifier.weight(1f)
        ) {
            Text(text = stringResource(R.string.profile_delete_pick_directory_action))
        }
    }
    Text(
        text = stringResource(R.string.profile_delete_target_picker_hint),
        style = MaterialTheme.typography.bodySmall
    )
    if (delete_target_picker_error_res_id != null) {
        Text(
            text = stringResource(delete_target_picker_error_res_id),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    val delete_target_error = delete_target_error_message(
        path = delete_target.path,
        content_uri = delete_target.content_uri
    )
    if (delete_target_error != null) {
        Text(
            text = delete_target_error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    AdvancedExecutionRuleToggle(
        show_advanced_execution_rule = show_advanced_execution_rule,
        on_toggle_advanced = on_toggle_advanced
    )
    if (show_advanced_execution_rule) {
        val action_rule = action_rule_for(ActionId.DELETE_PATHS)
        ExecutionRuleEditor(
            rule = action_rule,
            on_update = { updated_rule ->
                on_update_action_rule(ActionId.DELETE_PATHS, updated_rule)
            }
        )
    }
}

@Composable
internal fun ShellCommandEditorSection(
    row: ShellCommandActionRow,
    advanced_shell_commands: List<EditableShellCommand>,
    on_update_advanced_shell_commands: (List<EditableShellCommand>) -> Unit,
    show_advanced_execution_rule: Boolean,
    on_toggle_advanced: () -> Unit,
    action_rule_for: (ActionId) -> EditableActionPolicyRow,
    on_update_action_rule: (ActionId, EditableActionPolicyRow) -> Unit,
    on_mark_profile_dirty: () -> Unit
) {
    val shell_command = advanced_shell_commands.getOrNull(row.item_index) ?: return

    Text(
        text = stringResource(R.string.profile_section_basic_title),
        style = MaterialTheme.typography.titleSmall
    )
    OutlinedTextField(
        value = shell_command.label,
        onValueChange = { value ->
            on_update_advanced_shell_commands(
                advanced_shell_commands.update_item(row.item_index) { current_command ->
                    current_command.copy(label = value)
                }
            )
            on_mark_profile_dirty()
        },
        label = { Text(text = stringResource(R.string.profile_shell_label_label)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = shell_command.raw_shell,
        onValueChange = { value ->
            on_update_advanced_shell_commands(
                advanced_shell_commands.update_item(row.item_index) { current_command ->
                    current_command.copy(raw_shell = value)
                }
            )
            on_mark_profile_dirty()
        },
        label = { Text(text = stringResource(R.string.profile_shell_raw_label)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2
    )
    OutlinedTextField(
        value = shell_command.argv_multiline,
        onValueChange = { value ->
            on_update_advanced_shell_commands(
                advanced_shell_commands.update_item(row.item_index) { current_command ->
                    current_command.copy(argv_multiline = value)
                }
            )
            on_mark_profile_dirty()
        },
        label = { Text(text = stringResource(R.string.profile_shell_argv_label)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2
    )
    Text(
        text = stringResource(R.string.profile_shell_argv_help),
        style = MaterialTheme.typography.bodySmall
    )
    AdvancedExecutionRuleToggle(
        show_advanced_execution_rule = show_advanced_execution_rule,
        on_toggle_advanced = on_toggle_advanced,
        show_label_res_id = R.string.profile_advanced_show_action
    )
    if (show_advanced_execution_rule) {
        Text(
            text = stringResource(R.string.profile_actions_advanced_title),
            style = MaterialTheme.typography.titleSmall
        )
        OutlinedTextField(
            value = shell_command.timeout_seconds,
            onValueChange = { value ->
                on_update_advanced_shell_commands(
                    advanced_shell_commands.update_item(row.item_index) { current_command ->
                        current_command.copy(timeout_seconds = value)
                    }
                )
                on_mark_profile_dirty()
            },
            label = { Text(text = stringResource(R.string.profile_shell_timeout_label)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.profile_shell_enabled_label),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = shell_command.enabled,
                onCheckedChange = { enabled ->
                    on_update_advanced_shell_commands(
                        advanced_shell_commands.update_item(row.item_index) { current_command ->
                            current_command.copy(enabled = enabled)
                        }
                    )
                    on_mark_profile_dirty()
                }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.profile_shell_continue_label),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = shell_command.continue_on_failure,
                onCheckedChange = { continue_on_failure ->
                    on_update_advanced_shell_commands(
                        advanced_shell_commands.update_item(row.item_index) { current_command ->
                            current_command.copy(continue_on_failure = continue_on_failure)
                        }
                    )
                    on_mark_profile_dirty()
                }
            )
        }
        val action_rule = action_rule_for(ActionId.ADVANCED_SHELL_COMMANDS)
        ExecutionRuleEditor(
            rule = action_rule,
            on_update = { updated_rule ->
                on_update_action_rule(ActionId.ADVANCED_SHELL_COMMANDS, updated_rule)
            }
        )
    }
}

@Composable
internal fun SelfUninstallEditorSection(
    self_uninstall_enabled: Boolean,
    on_toggle_self_uninstall_enabled: (Boolean) -> Unit,
    show_advanced_execution_rule: Boolean,
    on_toggle_advanced: () -> Unit,
    action_rule_for: (ActionId) -> EditableActionPolicyRow,
    on_update_action_rule: (ActionId, EditableActionPolicyRow) -> Unit,
    on_mark_profile_dirty: () -> Unit
) {
    ToggleField(
        title = stringResource(R.string.profile_self_uninstall_enabled_label),
        checked = self_uninstall_enabled,
        on_checked_change = { enabled ->
            on_toggle_self_uninstall_enabled(enabled)
            on_mark_profile_dirty()
        }
    )
    AdvancedExecutionRuleToggle(
        show_advanced_execution_rule = show_advanced_execution_rule,
        on_toggle_advanced = on_toggle_advanced
    )
    if (show_advanced_execution_rule) {
        val action_rule = action_rule_for(ActionId.SELF_UNINSTALL)
        ExecutionRuleEditor(
            rule = action_rule,
            on_update = { updated_rule ->
                on_update_action_rule(ActionId.SELF_UNINSTALL, updated_rule)
            }
        )
    }
}
