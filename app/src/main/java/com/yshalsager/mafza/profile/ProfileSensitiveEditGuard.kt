package com.yshalsager.mafza.profile

import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ActionPolicy
import com.yshalsager.mafza.core.contracts.EmergencyProfile

private val SENSITIVE_ACTION_IDS = setOf(
    ActionId.UNINSTALL_APPS,
    ActionId.DELETE_PATHS,
    ActionId.ADVANCED_SHELL_COMMANDS,
    ActionId.SELF_UNINSTALL
)

internal fun has_sensitive_profile_changes(
    original_profile: EmergencyProfile,
    updated_profile: EmergencyProfile
): Boolean {
    if (original_profile.destructive_actions_enabled != updated_profile.destructive_actions_enabled) return true
    if (original_profile.triggers_enabled != updated_profile.triggers_enabled) return true
    if (original_profile.self_uninstall_enabled != updated_profile.self_uninstall_enabled) return true
    if (original_profile.uninstall_allowlist != updated_profile.uninstall_allowlist) return true
    if (original_profile.delete_allowlist != updated_profile.delete_allowlist) return true
    if (original_profile.advanced_shell_commands != updated_profile.advanced_shell_commands) return true
    return sensitive_policies(original_profile.action_policies) != sensitive_policies(updated_profile.action_policies)
}

private fun sensitive_policies(action_policies: List<ActionPolicy>): List<ActionPolicy> = action_policies
    .filter { it.action_id in SENSITIVE_ACTION_IDS }
    .sortedBy { it.policy_key }
