package com.yshalsager.mafza.profile

import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ActionPolicy
import com.yshalsager.mafza.core.contracts.ActionPolicyKeys
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.ShellCommandSpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileSensitiveEditGuardTest {
    @Test
    fun `returns false when only non-sensitive fields change`() {
        val original_profile = EmergencyProfile(
            sms_recipients = listOf("+201000000001"),
            message_template = "Initial"
        )
        val updated_profile = original_profile.copy(
            sms_recipients = listOf("+201000000002"),
            message_template = "Updated"
        )

        val has_sensitive_changes = has_sensitive_profile_changes(
            original_profile = original_profile,
            updated_profile = updated_profile
        )

        assertFalse(has_sensitive_changes)
    }

    @Test
    fun `returns true when global safety toggles change`() {
        val original_profile = EmergencyProfile(
            destructive_actions_enabled = false,
            triggers_enabled = true
        )
        val updated_profile = original_profile.copy(
            destructive_actions_enabled = true,
            triggers_enabled = false
        )

        val has_sensitive_changes = has_sensitive_profile_changes(
            original_profile = original_profile,
            updated_profile = updated_profile
        )

        assertTrue(has_sensitive_changes)
    }

    @Test
    fun `returns true when advanced shell commands change`() {
        val original_profile = EmergencyProfile(
            advanced_shell_commands = listOf(
                ShellCommandSpec(
                    id = "wipe",
                    label = "Wipe",
                    argv = listOf("rm", "-rf", "/data/local/tmp/test"),
                    raw_shell = null,
                    timeout_seconds = 10,
                    continue_on_failure = false,
                    enabled = true
                )
            )
        )
        val updated_profile = original_profile.copy(
            advanced_shell_commands = original_profile.advanced_shell_commands.map {
                it.copy(timeout_seconds = 15)
            }
        )

        val has_sensitive_changes = has_sensitive_profile_changes(
            original_profile = original_profile,
            updated_profile = updated_profile
        )

        assertTrue(has_sensitive_changes)
    }

    @Test
    fun `ignores sensitive policy list ordering while comparing`() {
        val uninstall_policy = ActionPolicy(
            action_id = ActionId.UNINSTALL_APPS,
            policy_key = ActionPolicyKeys.for_action(ActionId.UNINSTALL_APPS),
            enabled = true,
            required = false,
            continue_on_failure = true,
            execution_order = 1
        )
        val shell_policy = ActionPolicy(
            action_id = ActionId.ADVANCED_SHELL_COMMANDS,
            policy_key = ActionPolicyKeys.for_action(ActionId.ADVANCED_SHELL_COMMANDS),
            enabled = true,
            required = false,
            continue_on_failure = true,
            execution_order = 2
        )
        val original_profile = EmergencyProfile(
            action_policies = listOf(uninstall_policy, shell_policy)
        )
        val updated_profile = original_profile.copy(
            action_policies = listOf(shell_policy, uninstall_policy)
        )

        val has_sensitive_changes = has_sensitive_profile_changes(
            original_profile = original_profile,
            updated_profile = updated_profile
        )

        assertFalse(has_sensitive_changes)
    }
}
