package com.yshalsager.mafza.core.contracts

import kotlinx.serialization.Serializable

@Serializable
enum class ActionId {
    NOTIFY_MESSAGE_APP,
    LAUNCH_INTENT,
    UNINSTALL_APPS,
    DELETE_PATHS,
    ADVANCED_SHELL_COMMANDS
}

@Serializable
data class DeleteTarget(
    val path: String,
    val recursive: Boolean
)

@Serializable
data class ActionBinding(
    val action_id: ActionId,
    val package_name: String,
    val activity_name: String?,
    val enabled: Boolean
)

@Serializable
data class ActionPolicy(
    val action_id: ActionId,
    val enabled: Boolean,
    val required: Boolean,
    val continue_on_failure: Boolean,
    val execution_order: Int
)

@Serializable
data class ShellCommandSpec(
    val id: String,
    val label: String,
    val argv: List<String>,
    val raw_shell: String?,
    val timeout_seconds: Int,
    val continue_on_failure: Boolean,
    val enabled: Boolean
)

@Serializable
data class IntentActionSpec(
    val id: String,
    val label: String,
    val intent_action: String?,
    val data_uri: String?,
    val mime_type: String?,
    val categories: List<String>,
    val package_name: String?,
    val activity_name: String?,
    val extras_json: String?,
    val flags: List<String>,
    val timeout_seconds: Int,
    val continue_on_failure: Boolean,
    val enabled: Boolean
)

@Serializable
data class EmergencyProfile(
    val sms_recipients: List<String> = emptyList(),
    val notify_target: String = "",
    val message_template: String = "",
    val cancel_window_seconds: Int = 2,
    val uninstall_allowlist: List<String> = emptyList(),
    val delete_allowlist: List<DeleteTarget> = emptyList(),
    val self_uninstall_enabled: Boolean = false,
    val default_mode: ExecutionMode = ExecutionMode.LIVE,
    val action_bindings: List<ActionBinding> = emptyList(),
    val action_policies: List<ActionPolicy> = emptyList(),
    val intent_actions: List<IntentActionSpec> = emptyList(),
    val advanced_shell_commands: List<ShellCommandSpec> = emptyList(),
    val destructive_actions_enabled: Boolean = false,
    val triggers_enabled: Boolean = true
)
