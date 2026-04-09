package com.yshalsager.mafza.core.contracts

import kotlinx.serialization.Serializable

@Serializable
enum class ActionId {
    SEND_SMS,
    NOTIFY_MESSAGE_APP,
    LAUNCH_INTENT,
    UNINSTALL_APPS,
    DELETE_PATHS,
    ADVANCED_SHELL_COMMANDS,
    SELF_UNINSTALL
}

@Serializable
data class DeleteTarget(
    val path: String,
    val content_uri: String? = null,
    val recursive: Boolean
)

@Serializable
data class ActionBinding(
    val action_id: ActionId,
    val binding_id: String = "",
    val package_name: String,
    val activity_name: String?,
    val enabled: Boolean
)

@Serializable
data class ActionPolicy(
    val action_id: ActionId,
    val policy_key: String = ActionPolicyKeys.for_action(action_id),
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
    val timeout_seconds: Int = 20,
    val continue_on_failure: Boolean,
    val enabled: Boolean
)

@Serializable
data class EmergencyProfile(
    val sms_recipients: List<String> = emptyList(),
    val notify_target: String = "",
    val message_template: String = "",
    val cancel_window_seconds: Int = 2,
    val location_timeout_seconds: Int = 8,
    val sms_timeout_seconds: Int = 10,
    val intent_timeout_seconds: Int = 20,
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

object ActionPolicyKeys {
    fun for_action(action_id: ActionId): String = "action:${action_id.name.lowercase()}"

    fun for_binding(action_binding: ActionBinding): String {
        val normalized_binding_id = action_binding.binding_id.trim()
        if (normalized_binding_id.isEmpty()) return for_action(action_binding.action_id)
        return "binding:${action_binding.action_id.name.lowercase()}:$normalized_binding_id"
    }

    fun for_intent(intent_action_id: String): String = "intent:${intent_action_id.trim().lowercase()}"
}
