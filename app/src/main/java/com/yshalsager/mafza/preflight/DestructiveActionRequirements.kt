package com.yshalsager.mafza.preflight

import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ActionPolicy
import com.yshalsager.mafza.core.contracts.ActionPolicyKeys
import com.yshalsager.mafza.core.contracts.EmergencyProfile

internal fun requires_shizuku_for_live_destructive_actions(profile: EmergencyProfile): Boolean {
    if (!profile.destructive_actions_enabled) return false

    val uninstall_requires_shizuku = is_action_enabled(profile.action_policies, ActionId.UNINSTALL_APPS) &&
        profile.uninstall_allowlist.any { package_name -> package_name.trim().isNotEmpty() }
    val advanced_shell_requires_shizuku = is_action_enabled(profile.action_policies, ActionId.ADVANCED_SHELL_COMMANDS) &&
        profile.advanced_shell_commands.any { command ->
            if (!command.enabled) return@any false
            val has_argv = command.argv.any { argv_item -> argv_item.trim().isNotEmpty() }
            val has_allowed_raw_shell = command.allow_raw_shell && !command.raw_shell.isNullOrBlank()
            has_argv || has_allowed_raw_shell
        }
    val self_uninstall_requires_shizuku = is_action_enabled(profile.action_policies, ActionId.SELF_UNINSTALL) &&
        profile.self_uninstall_enabled

    return uninstall_requires_shizuku ||
        advanced_shell_requires_shizuku ||
        self_uninstall_requires_shizuku
}

private fun is_action_enabled(
    action_policies: List<ActionPolicy>,
    action_id: ActionId
): Boolean {
    val action_policy_key = ActionPolicyKeys.for_action(action_id)
    val direct_policy = action_policies.singleOrNull { it.policy_key == action_policy_key }
    return direct_policy?.enabled ?: true
}
