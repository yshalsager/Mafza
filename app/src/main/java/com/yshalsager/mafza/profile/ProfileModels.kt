package com.yshalsager.mafza.profile

import com.yshalsager.mafza.R
import com.yshalsager.mafza.core.contracts.ActionId

internal const val PROFILE_MIN_CANCEL_WINDOW_SECONDS = 1
internal const val PROFILE_MAX_CANCEL_WINDOW_SECONDS = 30
internal const val PROFILE_MIN_STEP_TIMEOUT_SECONDS = 1
internal const val PROFILE_MAX_LOCATION_TIMEOUT_SECONDS = 120
internal const val PROFILE_MAX_SMS_TIMEOUT_SECONDS = 120
internal const val PROFILE_MAX_INTENT_TIMEOUT_SECONDS = 180
internal const val PROFILE_MAX_ADVANCED_SHELL_TIMEOUT_SECONDS = 120
internal const val PROFILE_MIN_POLICY_ORDER = 1
internal const val PROFILE_MAX_POLICY_ORDER = 1_000
internal val PROFILE_PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

internal enum class AppRoute(
    val route: String,
    val label_res_id: Int
) {
    HOME(route = "home", label_res_id = R.string.nav_home),
    PROFILE(route = "profile", label_res_id = R.string.nav_profile)
}

internal enum class ProfilePolicyMode {
    INHERIT_DEFAULT,
    OVERRIDE
}

internal enum class ProfileActionGroup {
    COMMUNICATION,
    APP_INTENT,
    DESTRUCTIVE,
    FINALIZE
}

internal enum class ProfileActionRowType {
    SMS_RECIPIENT,
    MESSAGE_BINDING,
    INTENT_ACTION,
    UNINSTALL_PACKAGE,
    DELETE_TARGET,
    SHELL_COMMAND,
    SELF_UNINSTALL
}

internal sealed interface ProfileActionRow {
    val row_id: String
    val row_type: ProfileActionRowType
    val group: ProfileActionGroup
    val ui_order: Int
    val policy_mode: ProfilePolicyMode
}

internal data class SmsRecipientActionRow(
    override val row_id: String,
    override val ui_order: Int,
    val item_index: Int,
    val recipient: String
) : ProfileActionRow {
    override val row_type: ProfileActionRowType = ProfileActionRowType.SMS_RECIPIENT
    override val group: ProfileActionGroup = ProfileActionGroup.COMMUNICATION
    override val policy_mode: ProfilePolicyMode = ProfilePolicyMode.INHERIT_DEFAULT
}

internal data class MessageBindingActionRow(
    override val row_id: String,
    override val ui_order: Int,
    val item_index: Int,
    val binding: EditableMessageBinding
) : ProfileActionRow {
    override val row_type: ProfileActionRowType = ProfileActionRowType.MESSAGE_BINDING
    override val group: ProfileActionGroup = ProfileActionGroup.APP_INTENT
    override val policy_mode: ProfilePolicyMode = binding.policy_mode
}

internal data class IntentActionRow(
    override val row_id: String,
    override val ui_order: Int,
    val item_index: Int,
    val intent_action: EditableIntentAction
) : ProfileActionRow {
    override val row_type: ProfileActionRowType = ProfileActionRowType.INTENT_ACTION
    override val group: ProfileActionGroup = ProfileActionGroup.APP_INTENT
    override val policy_mode: ProfilePolicyMode = intent_action.policy_mode
}

internal data class UninstallPackageActionRow(
    override val row_id: String,
    override val ui_order: Int,
    val item_index: Int,
    val package_name: String
) : ProfileActionRow {
    override val row_type: ProfileActionRowType = ProfileActionRowType.UNINSTALL_PACKAGE
    override val group: ProfileActionGroup = ProfileActionGroup.DESTRUCTIVE
    override val policy_mode: ProfilePolicyMode = ProfilePolicyMode.INHERIT_DEFAULT
}

internal data class DeleteTargetActionRow(
    override val row_id: String,
    override val ui_order: Int,
    val item_index: Int,
    val target: EditableDeleteTarget
) : ProfileActionRow {
    override val row_type: ProfileActionRowType = ProfileActionRowType.DELETE_TARGET
    override val group: ProfileActionGroup = ProfileActionGroup.DESTRUCTIVE
    override val policy_mode: ProfilePolicyMode = ProfilePolicyMode.INHERIT_DEFAULT
}

internal data class ShellCommandActionRow(
    override val row_id: String,
    override val ui_order: Int,
    val item_index: Int,
    val command: EditableShellCommand
) : ProfileActionRow {
    override val row_type: ProfileActionRowType = ProfileActionRowType.SHELL_COMMAND
    override val group: ProfileActionGroup = ProfileActionGroup.DESTRUCTIVE
    override val policy_mode: ProfilePolicyMode = ProfilePolicyMode.INHERIT_DEFAULT
}

internal data class SelfUninstallActionRow(
    override val row_id: String = "self_uninstall",
    override val ui_order: Int
) : ProfileActionRow {
    override val row_type: ProfileActionRowType = ProfileActionRowType.SELF_UNINSTALL
    override val group: ProfileActionGroup = ProfileActionGroup.FINALIZE
    override val policy_mode: ProfilePolicyMode = ProfilePolicyMode.INHERIT_DEFAULT
}

internal data class EditableMessageBinding(
    val binding_id: String,
    val package_name: String,
    val activity_name: String,
    val enabled: Boolean,
    val policy_enabled: Boolean,
    val policy_required: Boolean,
    val policy_continue_on_failure: Boolean,
    val policy_execution_order: String,
    val policy_mode: ProfilePolicyMode
)

internal data class EditableActionPolicyRow(
    val action_id: ActionId,
    val policy_key: String,
    val enabled: Boolean,
    val required: Boolean,
    val continue_on_failure: Boolean,
    val execution_order: Int
)

internal data class EditableIntentAction(
    val id: String,
    val label: String,
    val intent_action: String,
    val data_uri: String,
    val package_name: String,
    val activity_name: String,
    val timeout_seconds: String,
    val continue_on_failure: Boolean,
    val enabled: Boolean,
    val policy_enabled: Boolean,
    val policy_required: Boolean,
    val policy_continue_on_failure: Boolean,
    val policy_execution_order: String,
    val policy_mode: ProfilePolicyMode
)

internal data class EditableDeleteTarget(
    val path: String,
    val recursive: Boolean
)

internal data class EditableShellCommand(
    val id: String,
    val label: String,
    val raw_shell: String,
    val argv_multiline: String,
    val timeout_seconds: String,
    val continue_on_failure: Boolean,
    val enabled: Boolean
)

internal data class ExecutionPreviewItem(
    val execution_order: Int,
    val label: String,
    val enabled: Boolean
)

internal data class LaunchableAppOption(
    val label: String,
    val package_name: String,
    val activity_name: String
)

internal data class InstalledPackageOption(
    val label: String,
    val package_name: String
)
