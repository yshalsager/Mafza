package com.yshalsager.mafza.profile

import com.yshalsager.mafza.core.contracts.ActionBinding
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ActionPolicy
import com.yshalsager.mafza.core.contracts.ActionPolicyKeys
import com.yshalsager.mafza.core.contracts.DeleteTarget
import com.yshalsager.mafza.core.contracts.IntentActionSpec
import com.yshalsager.mafza.core.contracts.ShellCommandSpec
import java.util.UUID

internal fun to_editable_intent_action(
    intent_action: IntentActionSpec,
    action_policies: List<ActionPolicy>,
    default_execution_order: Int
): EditableIntentAction {
    val normalized_id = intent_action.id.trim().ifEmpty { UUID.randomUUID().toString() }
    val intent_policy_key = ActionPolicyKeys.for_intent(normalized_id)
    val direct_policy = action_policies.firstOrNull { it.policy_key == intent_policy_key }
    val resolved_policy = direct_policy
        ?: action_policies.firstOrNull { it.policy_key == ActionPolicyKeys.for_action(ActionId.LAUNCH_INTENT) }

    return EditableIntentAction(
        id = normalized_id,
        label = intent_action.label,
        intent_action = intent_action.intent_action.orEmpty(),
        data_uri = intent_action.data_uri.orEmpty(),
        package_name = intent_action.package_name.orEmpty(),
        activity_name = intent_action.activity_name.orEmpty(),
        timeout_seconds = intent_action.timeout_seconds.toString(),
        continue_on_failure = intent_action.continue_on_failure,
        enabled = intent_action.enabled,
        policy_enabled = resolved_policy?.enabled ?: true,
        policy_required = resolved_policy?.required ?: false,
        policy_continue_on_failure = resolved_policy?.continue_on_failure ?: true,
        policy_execution_order = (resolved_policy?.execution_order ?: default_execution_order).toString(),
        policy_mode = if (direct_policy != null) ProfilePolicyMode.OVERRIDE else ProfilePolicyMode.INHERIT_DEFAULT
    )
}

internal fun create_empty_intent_action(default_execution_order: Int): EditableIntentAction {
    return EditableIntentAction(
        id = UUID.randomUUID().toString(),
        label = "",
        intent_action = "android.intent.action.VIEW",
        data_uri = "",
        package_name = "",
        activity_name = "",
        timeout_seconds = "20",
        continue_on_failure = true,
        enabled = true,
        policy_enabled = true,
        policy_required = false,
        policy_continue_on_failure = true,
        policy_execution_order = default_execution_order.toString(),
        policy_mode = ProfilePolicyMode.INHERIT_DEFAULT
    )
}

internal fun normalize_intent_action_orders(intent_actions: List<EditableIntentAction>): List<EditableIntentAction> {
    return intent_actions.mapIndexed { index, intent_action ->
        intent_action.copy(policy_execution_order = (index + 1).toString())
    }
}

internal fun move_intent_action(
    intent_actions: List<EditableIntentAction>,
    from_index: Int,
    to_index: Int
): List<EditableIntentAction> {
    if (from_index !in intent_actions.indices || to_index !in intent_actions.indices) return intent_actions
    val mutable_intent_actions = intent_actions.toMutableList()
    val moving_intent_action = mutable_intent_actions.removeAt(from_index)
    mutable_intent_actions.add(to_index, moving_intent_action)
    return normalize_intent_action_orders(mutable_intent_actions)
}

internal fun normalize_message_binding_orders(bindings: List<EditableMessageBinding>): List<EditableMessageBinding> {
    return bindings.mapIndexed { index, binding ->
        binding.copy(policy_execution_order = (index + 1).toString())
    }
}

internal fun move_message_binding(
    bindings: List<EditableMessageBinding>,
    from_index: Int,
    to_index: Int
): List<EditableMessageBinding> {
    if (from_index !in bindings.indices || to_index !in bindings.indices) return bindings
    val mutable_bindings = bindings.toMutableList()
    val moving_binding = mutable_bindings.removeAt(from_index)
    mutable_bindings.add(to_index, moving_binding)
    return normalize_message_binding_orders(mutable_bindings)
}

internal fun to_editable_message_binding(
    binding: ActionBinding,
    action_policies: List<ActionPolicy>,
    default_execution_order: Int
): EditableMessageBinding {
    val binding_id = binding.binding_id.trim().ifEmpty { UUID.randomUUID().toString() }
    val binding_policy_key = "binding:${binding.action_id.name.lowercase()}:$binding_id"
    val direct_policy = action_policies.firstOrNull { it.policy_key == binding_policy_key }
    val resolved_policy = direct_policy
        ?: action_policies.firstOrNull { it.policy_key == ActionPolicyKeys.for_action(binding.action_id) }

    return EditableMessageBinding(
        binding_id = binding_id,
        package_name = binding.package_name,
        activity_name = binding.activity_name.orEmpty(),
        enabled = binding.enabled,
        policy_enabled = resolved_policy?.enabled ?: true,
        policy_required = resolved_policy?.required ?: false,
        policy_continue_on_failure = resolved_policy?.continue_on_failure ?: true,
        policy_execution_order = (resolved_policy?.execution_order ?: default_execution_order).toString(),
        policy_mode = if (direct_policy != null) ProfilePolicyMode.OVERRIDE else ProfilePolicyMode.INHERIT_DEFAULT
    )
}

internal fun create_empty_message_binding(default_execution_order: Int): EditableMessageBinding {
    return EditableMessageBinding(
        binding_id = UUID.randomUUID().toString(),
        package_name = "",
        activity_name = "",
        enabled = true,
        policy_enabled = true,
        policy_required = false,
        policy_continue_on_failure = true,
        policy_execution_order = default_execution_order.toString(),
        policy_mode = ProfilePolicyMode.INHERIT_DEFAULT
    )
}

internal fun to_editable_delete_target(target: DeleteTarget): EditableDeleteTarget {
    return EditableDeleteTarget(
        path = target.path,
        recursive = target.recursive
    )
}

internal fun create_empty_delete_target(): EditableDeleteTarget {
    return EditableDeleteTarget(
        path = "",
        recursive = false
    )
}

internal fun to_editable_shell_command(command: ShellCommandSpec): EditableShellCommand {
    return EditableShellCommand(
        id = command.id.trim().ifEmpty { UUID.randomUUID().toString() },
        label = command.label,
        raw_shell = command.raw_shell.orEmpty(),
        argv_multiline = command.argv.joinToString("\n"),
        timeout_seconds = command.timeout_seconds.toString(),
        continue_on_failure = command.continue_on_failure,
        enabled = command.enabled
    )
}

internal fun create_empty_shell_command(): EditableShellCommand {
    return EditableShellCommand(
        id = UUID.randomUUID().toString(),
        label = "",
        raw_shell = "",
        argv_multiline = "",
        timeout_seconds = "15",
        continue_on_failure = true,
        enabled = true
    )
}

internal fun build_profile_action_bindings(
    existing_bindings: List<ActionBinding>,
    message_app_bindings: List<EditableMessageBinding>
): List<ActionBinding> {
    val non_message_bindings = existing_bindings.filter { it.action_id != ActionId.NOTIFY_MESSAGE_APP }
    val updated_message_bindings = message_app_bindings.map { editable ->
        ActionBinding(
            action_id = ActionId.NOTIFY_MESSAGE_APP,
            binding_id = editable.binding_id.trim().ifEmpty { UUID.randomUUID().toString() },
            package_name = editable.package_name.trim(),
            activity_name = editable.activity_name.trim().ifEmpty { null },
            enabled = editable.enabled
        )
    }
    return non_message_bindings + updated_message_bindings
}

internal fun build_profile_intent_actions(intent_actions: List<EditableIntentAction>): List<IntentActionSpec> {
    return intent_actions.map { intent_action ->
        IntentActionSpec(
            id = intent_action.id.trim().ifEmpty { UUID.randomUUID().toString() },
            label = intent_action.label.trim(),
            intent_action = intent_action.intent_action.trim().ifEmpty { null },
            data_uri = intent_action.data_uri.trim().ifEmpty { null },
            mime_type = null,
            categories = emptyList(),
            package_name = intent_action.package_name.trim().ifEmpty { null },
            activity_name = intent_action.activity_name.trim().ifEmpty { null },
            extras_json = null,
            flags = emptyList(),
            timeout_seconds = parse_int_or_fallback(
                value = intent_action.timeout_seconds,
                fallback = 20,
                min_value = PROFILE_MIN_STEP_TIMEOUT_SECONDS,
                max_value = PROFILE_MAX_INTENT_TIMEOUT_SECONDS
            ),
            continue_on_failure = intent_action.continue_on_failure,
            enabled = intent_action.enabled
        )
    }
}

internal fun build_profile_delete_targets(delete_targets: List<EditableDeleteTarget>): List<DeleteTarget> {
    return delete_targets
        .map { target ->
            DeleteTarget(
                path = target.path.trim(),
                recursive = target.recursive
            )
        }
        .filter { target -> target.path.isNotEmpty() }
}

internal fun build_profile_shell_commands(commands: List<EditableShellCommand>): List<ShellCommandSpec> {
    return commands.map { command ->
        ShellCommandSpec(
            id = command.id.trim().ifEmpty { UUID.randomUUID().toString() },
            label = command.label.trim(),
            argv = split_multiline_values(command.argv_multiline),
            raw_shell = command.raw_shell.trim().ifEmpty { null },
            timeout_seconds = parse_int_or_fallback(
                value = command.timeout_seconds,
                fallback = 15,
                min_value = PROFILE_MIN_STEP_TIMEOUT_SECONDS,
                max_value = PROFILE_MAX_ADVANCED_SHELL_TIMEOUT_SECONDS
            ),
            continue_on_failure = command.continue_on_failure,
            enabled = command.enabled
        )
    }.filter { command ->
        command.raw_shell != null || command.argv.isNotEmpty()
    }
}

internal fun build_profile_action_policies(
    existing_policies: List<ActionPolicy>,
    message_app_bindings: List<EditableMessageBinding>,
    action_policy_rows: List<EditableActionPolicyRow>,
    removed_action_policy_map: Map<String, ActionId>,
    intent_actions: List<EditableIntentAction>,
    removed_intent_policy_keys: Set<String>
): List<ActionPolicy> {
    val managed_action_policy_keys = manageable_action_ids().map(ActionPolicyKeys::for_action).toSet()
    val notify_default_policy_key = ActionPolicyKeys.for_action(ActionId.NOTIFY_MESSAGE_APP)
    val intent_default_policy_key = ActionPolicyKeys.for_action(ActionId.LAUNCH_INTENT)
    val reserved_default_policy_keys = setOf(notify_default_policy_key, intent_default_policy_key)
    val kept_policies = existing_policies.filterNot { policy ->
        policy.policy_key.startsWith("binding:${ActionId.NOTIFY_MESSAGE_APP.name.lowercase()}:") ||
            policy.policy_key in managed_action_policy_keys ||
            policy.policy_key in reserved_default_policy_keys ||
            policy.policy_key.startsWith("intent:")
    }
    val action_type_policies = action_policy_rows.map { row ->
        ActionPolicy(
            action_id = row.action_id,
            policy_key = row.policy_key,
            enabled = row.enabled,
            required = row.required,
            continue_on_failure = row.continue_on_failure,
            execution_order = row.execution_order
        )
    }
    val removed_action_type_policies = removed_action_policy_map
        .entries
        .filter { entry ->
            entry.key in managed_action_policy_keys &&
                action_type_policies.none { action_type_policy -> action_type_policy.policy_key == entry.key }
        }
        .sortedBy { it.key }
        .mapIndexed { index, entry ->
            ActionPolicy(
                action_id = entry.value,
                policy_key = entry.key,
                enabled = false,
                required = false,
                continue_on_failure = true,
                execution_order = 10_000 + index
            )
        }
    val binding_policies = message_app_bindings
        .filter { it.policy_mode == ProfilePolicyMode.OVERRIDE }
        .mapIndexed { index, binding ->
            ActionPolicy(
                action_id = ActionId.NOTIFY_MESSAGE_APP,
                policy_key = "binding:${ActionId.NOTIFY_MESSAGE_APP.name.lowercase()}:${binding.binding_id.trim()}",
                enabled = binding.policy_enabled,
                required = binding.policy_required,
                continue_on_failure = binding.policy_continue_on_failure,
                execution_order = parse_int_or_fallback(
                    value = binding.policy_execution_order,
                    fallback = index + 1,
                    min_value = PROFILE_MIN_POLICY_ORDER,
                    max_value = PROFILE_MAX_POLICY_ORDER
                )
            )
        }
    val intent_policies = intent_actions
        .filter { it.policy_mode == ProfilePolicyMode.OVERRIDE }
        .mapIndexed { index, intent_action ->
            ActionPolicy(
                action_id = ActionId.LAUNCH_INTENT,
                policy_key = ActionPolicyKeys.for_intent(intent_action.id),
                enabled = intent_action.policy_enabled,
                required = intent_action.policy_required,
                continue_on_failure = intent_action.policy_continue_on_failure,
                execution_order = parse_int_or_fallback(
                    value = intent_action.policy_execution_order,
                    fallback = index + 1,
                    min_value = PROFILE_MIN_POLICY_ORDER,
                    max_value = PROFILE_MAX_POLICY_ORDER
                )
            )
        }
    val notify_default_policy = existing_policies.firstOrNull { policy ->
        policy.policy_key == notify_default_policy_key
    } ?: ActionPolicy(
        action_id = ActionId.NOTIFY_MESSAGE_APP,
        policy_key = notify_default_policy_key,
        enabled = true,
        required = false,
        continue_on_failure = true,
        execution_order = 100
    )
    val intent_default_policy = existing_policies.firstOrNull { policy ->
        policy.policy_key == intent_default_policy_key
    } ?: ActionPolicy(
        action_id = ActionId.LAUNCH_INTENT,
        policy_key = intent_default_policy_key,
        enabled = true,
        required = false,
        continue_on_failure = true,
        execution_order = 120
    )
    val removed_intent_policies = removed_intent_policy_keys
        .filter { removed_intent_policy_key ->
            intent_policies.none { intent_policy -> intent_policy.policy_key == removed_intent_policy_key }
        }
        .sorted()
        .mapIndexed { index, removed_intent_policy_key ->
            ActionPolicy(
                action_id = ActionId.LAUNCH_INTENT,
                policy_key = removed_intent_policy_key,
                enabled = false,
                required = false,
                continue_on_failure = true,
                execution_order = 20_000 + index
            )
        }
    return kept_policies +
        action_type_policies +
        removed_action_type_policies +
        notify_default_policy +
        intent_default_policy +
        binding_policies +
        intent_policies +
        removed_intent_policies
}
