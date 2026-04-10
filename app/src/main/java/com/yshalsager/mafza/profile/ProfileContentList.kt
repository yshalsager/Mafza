package com.yshalsager.mafza.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yshalsager.mafza.R
import com.yshalsager.mafza.ui.theme.MafzaTokens

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProfileContentList(
    modifier: Modifier,
    profile_list_state: LazyListState,
    action_policy_rows: List<EditableActionPolicyRow>,
    telegram_bot_actions: List<EditableTelegramBotAction>,
    message_app_bindings: List<EditableMessageBinding>,
    intent_actions: List<EditableIntentAction>,
    show_reorder_helper: Boolean,
    group_expansion: Map<ProfileActionGroup, Boolean>,
    on_toggle_group: (ProfileActionGroup) -> Unit,
    action_rows_by_group: Map<ProfileActionGroup, List<ProfileActionRow>>,
    sms_recipients: List<String>,
    uninstall_packages: List<String>,
    delete_targets: List<EditableDeleteTarget>,
    advanced_shell_commands: List<EditableShellCommand>,
    has_actions: Boolean,
    on_add_first_action: () -> Unit,
    on_edit_action_row: (String) -> Unit,
    on_move_action_row: (ProfileActionRow, Int) -> Unit,
    on_remove_action_row: (ProfileActionRow) -> Unit,
    profile_settings_expanded: Boolean,
    on_profile_settings_expanded_change: (Boolean) -> Unit,
    message_settings_expanded: Boolean,
    on_message_settings_expanded_change: (Boolean) -> Unit,
    location_lookup_settings_expanded: Boolean,
    on_location_lookup_settings_expanded_change: (Boolean) -> Unit,
    timeouts_settings_expanded: Boolean,
    on_timeouts_settings_expanded_change: (Boolean) -> Unit,
    safety_settings_expanded: Boolean,
    on_safety_settings_expanded_change: (Boolean) -> Unit,
    notify_target_input: String,
    on_notify_target_change: (String) -> Unit,
    message_template_input: String,
    on_message_template_change: (String) -> Unit,
    cancel_window_input: String,
    cancel_window_error: String?,
    on_cancel_window_change: (String) -> Unit,
    location_timeout_input: String,
    location_timeout_error: String?,
    on_location_timeout_change: (String) -> Unit,
    opencellid_api_key_input: String,
    on_opencellid_api_key_change: (String) -> Unit,
    sms_timeout_input: String,
    sms_timeout_error: String?,
    on_sms_timeout_change: (String) -> Unit,
    intent_timeout_input: String,
    intent_timeout_error: String?,
    on_intent_timeout_change: (String) -> Unit,
    destructive_actions_enabled: Boolean,
    on_destructive_actions_toggle: (Boolean) -> Unit,
    triggers_enabled: Boolean,
    on_triggers_toggle: (Boolean) -> Unit,
    self_uninstall_enabled: Boolean,
    on_self_uninstall_toggle: (Boolean) -> Unit,
    backup_in_progress: Boolean,
    backup_status_message: String?,
    backup_error_message: String?,
    on_export_backup: () -> Unit,
    on_restore_backup: () -> Unit,
    validation_issues: List<ProfileValidationIssue>,
    on_focus_validation_issue: (ProfileValidationIssueKey) -> Unit
) {
    val spacing = MafzaTokens.spacing
    LazyColumn(
        state = profile_list_state,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm + spacing.xs / 2),
        verticalArrangement = Arrangement.spacedBy(spacing.sm + spacing.xs / 2)
    ) {
        item {
            Text(
                text = stringResource(R.string.profile_subtitle),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        item {
            ProfileHintCard(text = stringResource(R.string.profile_actions_add_hint))
        }
        if (!has_actions) {
            item {
                ProfileEmptyActionsOnboardingCard(
                    on_add_first_action = on_add_first_action
                )
            }
        }
        item {
            ActionExecutionPreviewCard(
                action_policy_rows = action_policy_rows,
                telegram_bot_actions = telegram_bot_actions,
                message_app_bindings = message_app_bindings,
                intent_actions = intent_actions
            )
        }
        if (show_reorder_helper) {
            item {
                ProfileHintCard(text = stringResource(R.string.profile_actions_reorder_hint))
            }
        }
        ProfileActionGroup.entries.forEach { group ->
            stickyHeader(key = "group_${group.name}") {
                ProfileActionGroupHeader(
                    title = stringResource(profile_action_group_label_res(group)),
                    expanded = group_expansion[group] ?: true,
                    on_toggle = { on_toggle_group(group) }
                )
            }
            if (group_expansion[group] == true) {
                val group_rows = action_rows_by_group[group].orEmpty()
                if (group_rows.isEmpty()) {
                    if (has_actions) {
                        item(key = "group_${group.name}_empty") {
                            Text(
                                text = stringResource(R.string.profile_actions_group_empty),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    items(
                        items = group_rows,
                        key = { row -> row.row_id }
                    ) { row ->
                        val current_index = action_row_current_index(row)
                        val last_index = action_row_last_index(
                            row = row,
                            sms_recipients = sms_recipients,
                            telegram_bot_actions = telegram_bot_actions,
                            message_app_bindings = message_app_bindings,
                            intent_actions = intent_actions,
                            uninstall_packages = uninstall_packages,
                            delete_targets = delete_targets,
                            advanced_shell_commands = advanced_shell_commands
                        )
                        ProfileActionRowCard(
                            title = stringResource(profile_action_row_type_label_res(row.row_type)),
                            summary = profile_action_row_summary(row),
                            branch_label = stringResource(profile_action_group_label_res(row.group)),
                            lane_label = stringResource(profile_action_row_lane_res(row.row_type)),
                            can_move_up = current_index > 0,
                            can_move_down = current_index in 0 until last_index,
                            can_move_top = current_index > 0,
                            can_move_bottom = current_index in 0 until last_index,
                            on_edit = { on_edit_action_row(row.row_id) },
                            on_move_up = {
                                if (current_index > 0) {
                                    on_move_action_row(row, current_index - 1)
                                }
                            },
                            on_move_down = {
                                if (current_index < last_index) {
                                    on_move_action_row(row, current_index + 1)
                                }
                            },
                            on_move_top = {
                                if (current_index > 0) {
                                    on_move_action_row(row, 0)
                                }
                            },
                            on_move_bottom = {
                                if (current_index < last_index) {
                                    on_move_action_row(row, last_index)
                                }
                            },
                            on_remove = { on_remove_action_row(row) }
                        )
                    }
                }
            }
        }
        item {
            CollapsibleSectionCard(
                title = stringResource(R.string.profile_settings_title),
                summary = stringResource(R.string.profile_settings_summary),
                initially_expanded = false,
                expanded = profile_settings_expanded,
                on_expanded_change = on_profile_settings_expanded_change,
                is_critical = false
            ) {
                CollapsibleInlineSection(
                    title = stringResource(R.string.profile_settings_message_title),
                    summary = stringResource(R.string.profile_settings_message_summary),
                    initially_expanded = true,
                    expanded = message_settings_expanded,
                    on_expanded_change = on_message_settings_expanded_change
                ) {
                    OutlinedTextField(
                        value = notify_target_input,
                        onValueChange = on_notify_target_change,
                        label = { Text(text = stringResource(R.string.profile_notify_target_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = message_template_input,
                        onValueChange = on_message_template_change,
                        label = { Text(text = stringResource(R.string.profile_message_template_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    Text(
                        text = stringResource(R.string.profile_message_template_variables_hint),
                        style = MaterialTheme.typography.bodySmall
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(MESSAGE_TEMPLATE_VARIABLES) { token ->
                            AssistChip(
                                onClick = {
                                    val suffix = if (message_template_input.isBlank() || message_template_input.endsWith(" ")) "" else " "
                                    on_message_template_change(message_template_input + suffix + token)
                                },
                                label = { Text(token) }
                            )
                        }
                    }
                }

                CollapsibleInlineSection(
                    title = stringResource(R.string.profile_settings_location_lookup_title),
                    summary = stringResource(R.string.profile_settings_location_lookup_summary),
                    initially_expanded = false,
                    expanded = location_lookup_settings_expanded,
                    on_expanded_change = on_location_lookup_settings_expanded_change
                ) {
                    OutlinedTextField(
                        value = opencellid_api_key_input,
                        onValueChange = on_opencellid_api_key_change,
                        label = { Text(text = stringResource(R.string.profile_opencellid_api_key_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                CollapsibleInlineSection(
                    title = stringResource(R.string.profile_settings_timeouts_title),
                    summary = stringResource(R.string.profile_settings_timeouts_summary),
                    initially_expanded = false,
                    expanded = timeouts_settings_expanded,
                    on_expanded_change = on_timeouts_settings_expanded_change
                ) {
                    TimeoutField(
                        label = stringResource(R.string.profile_cancel_window_label),
                        value = cancel_window_input,
                        error_message = cancel_window_error,
                        on_value_change = on_cancel_window_change
                    )
                    TimeoutField(
                        label = stringResource(R.string.profile_location_timeout_label),
                        value = location_timeout_input,
                        error_message = location_timeout_error,
                        on_value_change = on_location_timeout_change
                    )
                    TimeoutField(
                        label = stringResource(R.string.profile_sms_timeout_label),
                        value = sms_timeout_input,
                        error_message = sms_timeout_error,
                        on_value_change = on_sms_timeout_change
                    )
                    TimeoutField(
                        label = stringResource(R.string.profile_intent_timeout_label),
                        value = intent_timeout_input,
                        error_message = intent_timeout_error,
                        on_value_change = on_intent_timeout_change
                    )
                }

                CollapsibleInlineSection(
                    title = stringResource(R.string.profile_settings_safety_title),
                    summary = stringResource(R.string.profile_settings_safety_summary),
                    initially_expanded = false,
                    expanded = safety_settings_expanded,
                    on_expanded_change = on_safety_settings_expanded_change
                ) {
                    Text(
                        text = stringResource(R.string.profile_safety_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    ToggleField(
                        title = stringResource(R.string.profile_destructive_enabled_label),
                        checked = destructive_actions_enabled,
                        on_checked_change = on_destructive_actions_toggle
                    )
                    ToggleField(
                        title = stringResource(R.string.profile_triggers_enabled_label),
                        checked = triggers_enabled,
                        on_checked_change = on_triggers_toggle
                    )
                    ToggleField(
                        title = stringResource(R.string.profile_self_uninstall_enabled_label),
                        checked = self_uninstall_enabled,
                        on_checked_change = on_self_uninstall_toggle
                    )
                }
            }
        }
        item {
            ProfileBackupSectionCard(
                backup_in_progress = backup_in_progress,
                backup_status_message = backup_status_message,
                backup_error_message = backup_error_message,
                on_export_backup = on_export_backup,
                on_restore_backup = on_restore_backup
            )
        }
        if (validation_issues.isNotEmpty()) {
            item {
                ProfileValidationIssuesCard(
                    issues = validation_issues,
                    on_focus_issue = on_focus_validation_issue
                )
            }
        }
    }
}

private val MESSAGE_TEMPLATE_VARIABLES = listOf(
    "{timestamp}",
    "{lat}",
    "{lon}",
    "{maps_url}",
    "{trigger}",
    "{altitude}",
    "{accuracy}",
    "{cell_id}",
    "{cell_radio}",
    "{cell_area}",
    "{cell_pci}",
    "{cell_mcc}",
    "{cell_mnc}",
    "{battery}",
    "{locale}",
    "{app_version}"
)
