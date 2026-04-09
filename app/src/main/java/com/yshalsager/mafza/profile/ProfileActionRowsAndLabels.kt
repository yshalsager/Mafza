package com.yshalsager.mafza.profile

import com.yshalsager.mafza.R
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ActionPolicy
import com.yshalsager.mafza.core.contracts.ActionPolicyKeys

internal fun build_profile_action_rows(
    sms_recipients: List<String>,
    message_app_bindings: List<EditableMessageBinding>,
    intent_actions: List<EditableIntentAction>,
    uninstall_packages: List<String>,
    delete_targets: List<EditableDeleteTarget>,
    advanced_shell_commands: List<EditableShellCommand>,
    self_uninstall_enabled: Boolean
): List<ProfileActionRow> {
    var order = 1
    val rows = mutableListOf<ProfileActionRow>()
    sms_recipients.forEachIndexed { index, recipient ->
        rows += SmsRecipientActionRow(
            row_id = "sms:$index",
            ui_order = order++,
            item_index = index,
            recipient = recipient
        )
    }
    message_app_bindings.forEachIndexed { index, binding ->
        rows += MessageBindingActionRow(
            row_id = "binding:${binding.binding_id}",
            ui_order = order++,
            item_index = index,
            binding = binding
        )
    }
    intent_actions.forEachIndexed { index, intent_action ->
        rows += IntentActionRow(
            row_id = "intent:${intent_action.id}",
            ui_order = order++,
            item_index = index,
            intent_action = intent_action
        )
    }
    uninstall_packages.forEachIndexed { index, package_name ->
        rows += UninstallPackageActionRow(
            row_id = "uninstall:$index",
            ui_order = order++,
            item_index = index,
            package_name = package_name
        )
    }
    delete_targets.forEachIndexed { index, target ->
        rows += DeleteTargetActionRow(
            row_id = "delete:$index",
            ui_order = order++,
            item_index = index,
            target = target
        )
    }
    advanced_shell_commands.forEachIndexed { index, command ->
        rows += ShellCommandActionRow(
            row_id = "shell:${command.id}",
            ui_order = order++,
            item_index = index,
            command = command
        )
    }
    if (self_uninstall_enabled) {
        rows += SelfUninstallActionRow(ui_order = order)
    }
    return rows
}

internal fun profile_action_types_for_group(group: ProfileActionGroup): List<ProfileActionRowType> {
    return when (group) {
        ProfileActionGroup.COMMUNICATION -> listOf(ProfileActionRowType.SMS_RECIPIENT)
        ProfileActionGroup.APP_INTENT -> listOf(
            ProfileActionRowType.MESSAGE_BINDING,
            ProfileActionRowType.INTENT_ACTION
        )
        ProfileActionGroup.DESTRUCTIVE -> listOf(
            ProfileActionRowType.UNINSTALL_PACKAGE,
            ProfileActionRowType.DELETE_TARGET,
            ProfileActionRowType.SHELL_COMMAND
        )
        ProfileActionGroup.FINALIZE -> listOf(ProfileActionRowType.SELF_UNINSTALL)
    }
}

internal fun profile_action_group_label_res(group: ProfileActionGroup): Int {
    return when (group) {
        ProfileActionGroup.COMMUNICATION -> R.string.profile_action_group_communication
        ProfileActionGroup.APP_INTENT -> R.string.profile_action_group_app_intent
        ProfileActionGroup.DESTRUCTIVE -> R.string.profile_action_group_destructive
        ProfileActionGroup.FINALIZE -> R.string.profile_action_group_finalize
    }
}

internal fun profile_action_row_type_label_res(row_type: ProfileActionRowType): Int {
    return when (row_type) {
        ProfileActionRowType.SMS_RECIPIENT -> R.string.profile_action_type_sms_recipient
        ProfileActionRowType.MESSAGE_BINDING -> R.string.profile_action_type_message_binding
        ProfileActionRowType.INTENT_ACTION -> R.string.profile_action_type_launch_intent
        ProfileActionRowType.UNINSTALL_PACKAGE -> R.string.profile_action_type_uninstall_package
        ProfileActionRowType.DELETE_TARGET -> R.string.profile_action_type_delete_target
        ProfileActionRowType.SHELL_COMMAND -> R.string.profile_action_type_shell_command
        ProfileActionRowType.SELF_UNINSTALL -> R.string.profile_action_type_self_uninstall
    }
}

internal fun profile_action_row_lane_res(row_type: ProfileActionRowType): Int {
    return when (row_type) {
        ProfileActionRowType.SMS_RECIPIENT -> R.string.profile_action_lane_sms
        ProfileActionRowType.MESSAGE_BINDING -> R.string.profile_action_lane_notify
        ProfileActionRowType.INTENT_ACTION -> R.string.profile_action_lane_notify
        ProfileActionRowType.UNINSTALL_PACKAGE -> R.string.profile_action_lane_destructive
        ProfileActionRowType.DELETE_TARGET -> R.string.profile_action_lane_destructive
        ProfileActionRowType.SHELL_COMMAND -> R.string.profile_action_lane_destructive
        ProfileActionRowType.SELF_UNINSTALL -> R.string.profile_action_lane_finalize
    }
}

@Composable
internal fun profile_action_row_summary(row: ProfileActionRow): String {
    return when (row) {
        is SmsRecipientActionRow -> row.recipient.trim().ifEmpty { stringResource(R.string.profile_action_row_summary_no_number_set) }
        is MessageBindingActionRow -> row.binding.package_name.trim().ifEmpty { stringResource(R.string.profile_action_row_summary_no_package_set) }
        is IntentActionRow -> row.intent_action.label.trim().ifEmpty {
            row.intent_action.intent_action.trim().ifEmpty { stringResource(R.string.profile_action_row_summary_no_intent_set) }
        }
        is UninstallPackageActionRow -> row.package_name.trim().ifEmpty { stringResource(R.string.profile_action_row_summary_no_package_set) }
        is DeleteTargetActionRow -> {
            val path_summary = row.target.path.trim()
            if (path_summary.isNotEmpty()) {
                path_summary
            } else {
                row.target.content_uri.trim().ifEmpty { stringResource(R.string.profile_action_row_summary_no_path_set) }
            }
        }
        is ShellCommandActionRow -> row.command.label.trim().ifEmpty { stringResource(R.string.profile_action_row_summary_no_label_set) }
        is SelfUninstallActionRow -> stringResource(R.string.profile_action_row_summary_self_uninstall)
    }
}

internal fun action_row_current_index(row: ProfileActionRow): Int {
    return when (row) {
        is SmsRecipientActionRow -> row.item_index
        is MessageBindingActionRow -> row.item_index
        is IntentActionRow -> row.item_index
        is UninstallPackageActionRow -> row.item_index
        is DeleteTargetActionRow -> row.item_index
        is ShellCommandActionRow -> row.item_index
        is SelfUninstallActionRow -> 0
    }
}

internal fun action_row_last_index(
    row: ProfileActionRow,
    sms_recipients: List<String>,
    message_app_bindings: List<EditableMessageBinding>,
    intent_actions: List<EditableIntentAction>,
    uninstall_packages: List<String>,
    delete_targets: List<EditableDeleteTarget>,
    advanced_shell_commands: List<EditableShellCommand>
): Int {
    return when (row) {
        is SmsRecipientActionRow -> sms_recipients.lastIndex
        is MessageBindingActionRow -> message_app_bindings.lastIndex
        is IntentActionRow -> intent_actions.lastIndex
        is UninstallPackageActionRow -> uninstall_packages.lastIndex
        is DeleteTargetActionRow -> delete_targets.lastIndex
        is ShellCommandActionRow -> advanced_shell_commands.lastIndex
        is SelfUninstallActionRow -> 0
    }
}

internal fun manageable_action_ids(): List<ActionId> {
    return listOf(
        ActionId.SEND_SMS,
        ActionId.UNINSTALL_APPS,
        ActionId.DELETE_PATHS,
        ActionId.ADVANCED_SHELL_COMMANDS,
        ActionId.SELF_UNINSTALL
    )
}

internal fun extract_editable_action_policy_rows(action_policies: List<ActionPolicy>): List<EditableActionPolicyRow> {
    val rows = manageable_action_ids().mapNotNull { action_id ->
        val policy_key = ActionPolicyKeys.for_action(action_id)
        val policy = action_policies.firstOrNull { it.policy_key == policy_key } ?: return@mapNotNull null
        EditableActionPolicyRow(
            action_id = action_id,
            policy_key = policy.policy_key,
            enabled = policy.enabled,
            required = policy.required,
            continue_on_failure = policy.continue_on_failure,
            execution_order = policy.execution_order
        )
    }
    return normalize_action_policy_row_orders(rows.sortedBy { it.execution_order })
}

internal fun create_action_policy_row(action_id: ActionId, execution_order: Int): EditableActionPolicyRow {
    return EditableActionPolicyRow(
        action_id = action_id,
        policy_key = ActionPolicyKeys.for_action(action_id),
        enabled = true,
        required = false,
        continue_on_failure = true,
        execution_order = execution_order
    )
}

internal fun normalize_action_policy_row_orders(rows: List<EditableActionPolicyRow>): List<EditableActionPolicyRow> {
    return rows.mapIndexed { index, row ->
        row.copy(execution_order = index + 1)
    }
}

internal fun move_action_policy_row(
    rows: List<EditableActionPolicyRow>,
    from_index: Int,
    to_index: Int
): List<EditableActionPolicyRow> {
    if (from_index !in rows.indices || to_index !in rows.indices) return rows
    val mutable_rows = rows.toMutableList()
    val moving_row = mutable_rows.removeAt(from_index)
    mutable_rows.add(to_index, moving_row)
    return normalize_action_policy_row_orders(mutable_rows)
}

internal fun action_type_label_res(action_id: ActionId): Int {
    return when (action_id) {
        ActionId.SEND_SMS -> R.string.profile_action_type_send_sms
        ActionId.UNINSTALL_APPS -> R.string.profile_action_type_uninstall_apps
        ActionId.DELETE_PATHS -> R.string.profile_action_type_delete_paths
        ActionId.ADVANCED_SHELL_COMMANDS -> R.string.profile_action_type_advanced_shell
        ActionId.SELF_UNINSTALL -> R.string.profile_action_type_self_uninstall
        ActionId.NOTIFY_MESSAGE_APP -> R.string.profile_action_type_notify_message_app
        ActionId.LAUNCH_INTENT -> R.string.profile_action_type_launch_intent
    }
}

internal fun test_intent_action(
    context: Context,
    intent_action: EditableIntentAction
): String {
    val resolved_intent_action = intent_action.intent_action.trim().ifEmpty { Intent.ACTION_VIEW }
    val intent = Intent(resolved_intent_action).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val raw_data_uri = intent_action.data_uri.trim()
    if (raw_data_uri.isNotEmpty()) {
        val parsed_uri = runCatching { Uri.parse(raw_data_uri) }.getOrNull()
            ?: return "invalid_data_uri"
        intent.data = parsed_uri
    }

    val package_name = intent_action.package_name.trim().ifEmpty { null }
    val activity_name = intent_action.activity_name.trim().ifEmpty { null }
    if (package_name != null) intent.`package` = package_name
    if (package_name != null && activity_name != null) {
        intent.component = ComponentName(package_name, activity_name)
    }

    return if (intent.resolveActivity(context.packageManager) != null) {
        "resolvable"
    } else {
        "unresolvable"
    }
}

internal fun intent_test_status_res(status: String): Int {
    return when (status) {
        "resolvable" -> R.string.profile_intent_action_test_resolvable
        "unresolvable" -> R.string.profile_intent_action_test_unresolvable
        "invalid_data_uri" -> R.string.profile_intent_action_test_invalid_data_uri
        else -> R.string.profile_intent_action_test_unknown
    }
}

internal fun message_binding_test_status_res(status: String): Int {
    return when (status) {
        "ready" -> R.string.profile_message_binding_test_ready
        "missing_package" -> R.string.profile_message_binding_test_missing_package
        "unavailable", "provider_unavailable" -> R.string.profile_message_binding_test_unavailable
        "no_provider" -> R.string.profile_message_binding_test_no_provider
        "preflight_failed" -> R.string.profile_message_binding_test_failed
        else -> R.string.profile_message_binding_test_unknown
    }
}
