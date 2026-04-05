package com.yshalsager.mafza.preflight

import android.content.ContextWrapper
import com.yshalsager.mafza.emergency.providers.ActionProviderRegistry
import com.yshalsager.mafza.core.contracts.ActionBinding
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ActionPolicy
import com.yshalsager.mafza.core.contracts.DeleteTarget
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.IntentActionSpec
import com.yshalsager.mafza.core.contracts.ShellCommandSpec
import com.yshalsager.mafza.shizuku.ShizukuPermissionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreflightValidatorTest {
    @Test
    fun `dry run stays ready when only live environment checks fail`() {
        val validator = validator(
            has_location_permission = false,
            has_sms_permission = false
        )
        val profile = EmergencyProfile(
            sms_recipients = listOf("+20123456789"),
            destructive_actions_enabled = true
        )

        val report = validator.validate(
            profile = profile,
            shizuku_permission_state = ShizukuPermissionState(
                is_running = false,
                is_permission_granted = false
            )
        )

        assertFalse(report.live_ready)
        assertTrue(report.dry_run_ready)
        assertTrue(report.live_blocking_issues.contains("missing_location_permission"))
        assertTrue(report.live_blocking_issues.contains("missing_send_sms_permission"))
        assertTrue(report.live_blocking_issues.contains("shizuku_permission_required_for_destructive_actions"))
    }

    @Test
    fun `duplicate execution order in same branch blocks both live and dry run`() {
        val validator = validator()
        val profile = EmergencyProfile(
            sms_recipients = listOf("+20123456789"),
            action_policies = listOf(
                ActionPolicy(
                    action_id = ActionId.NOTIFY_MESSAGE_APP,
                    enabled = true,
                    required = false,
                    continue_on_failure = true,
                    execution_order = 1
                ),
                ActionPolicy(
                    action_id = ActionId.LAUNCH_INTENT,
                    enabled = true,
                    required = false,
                    continue_on_failure = true,
                    execution_order = 1
                )
            )
        )

        val report = validator.validate(profile, ShizukuPermissionState())

        assertFalse(report.live_ready)
        assertFalse(report.dry_run_ready)
        assertTrue(report.live_blocking_issues.contains("action_policy_duplicate_execution_order"))
        assertTrue(report.dry_run_blocking_issues.contains("action_policy_duplicate_execution_order"))
    }

    @Test
    fun `duplicate bindings for disabled action do not block preflight`() {
        val validator = validator()
        val profile = EmergencyProfile(
            sms_recipients = listOf("+20123456789"),
            action_bindings = listOf(
                ActionBinding(
                    action_id = ActionId.NOTIFY_MESSAGE_APP,
                    package_name = "com.example.one",
                    activity_name = null,
                    enabled = true
                ),
                ActionBinding(
                    action_id = ActionId.NOTIFY_MESSAGE_APP,
                    package_name = "com.example.two",
                    activity_name = null,
                    enabled = true
                )
            ),
            action_policies = listOf(
                ActionPolicy(
                    action_id = ActionId.NOTIFY_MESSAGE_APP,
                    enabled = false,
                    required = false,
                    continue_on_failure = true,
                    execution_order = 1
                )
            )
        )

        val report = validator.validate(profile, ShizukuPermissionState())

        assertTrue(report.live_ready)
        assertTrue(report.dry_run_ready)
        assertFalse(report.live_blocking_issues.contains("duplicate_enabled_action_bindings"))
        assertFalse(report.dry_run_blocking_issues.contains("duplicate_enabled_action_bindings"))
    }

    @Test
    fun `invalid intent does not block when launch intent action is disabled`() {
        val validator = validator()
        val profile = EmergencyProfile(
            sms_recipients = listOf("+20123456789"),
            intent_actions = listOf(
                IntentActionSpec(
                    id = "",
                    label = "Broken intent",
                    intent_action = null,
                    data_uri = null,
                    mime_type = null,
                    categories = emptyList(),
                    package_name = null,
                    activity_name = null,
                    extras_json = null,
                    flags = emptyList(),
                    continue_on_failure = true,
                    enabled = true
                )
            ),
            action_policies = listOf(
                ActionPolicy(
                    action_id = ActionId.LAUNCH_INTENT,
                    enabled = false,
                    required = false,
                    continue_on_failure = true,
                    execution_order = 1
                )
            )
        )

        val report = validator.validate(profile, ShizukuPermissionState())

        assertTrue(report.live_ready)
        assertTrue(report.dry_run_ready)
        assertFalse(report.live_blocking_issues.contains("intent_action_missing_id"))
        assertFalse(report.dry_run_blocking_issues.contains("intent_action_missing_id"))
    }

    @Test
    fun `required unavailable binding blocks live`() {
        val validator = validator(binding_available = false)
        val profile = EmergencyProfile(
            sms_recipients = listOf("+20123456789"),
            action_bindings = listOf(
                ActionBinding(
                    action_id = ActionId.NOTIFY_MESSAGE_APP,
                    package_name = "com.example.app",
                    activity_name = null,
                    enabled = true
                )
            ),
            action_policies = listOf(
                ActionPolicy(
                    action_id = ActionId.NOTIFY_MESSAGE_APP,
                    enabled = true,
                    required = true,
                    continue_on_failure = true,
                    execution_order = 1
                )
            )
        )

        val report = validator.validate(profile, ShizukuPermissionState())

        assertFalse(report.live_ready)
        assertTrue(report.live_blocking_issues.contains("required_binding_unavailable_notify_message_app"))
    }

    @Test
    fun `invalid delete target blocks both live and dry run`() {
        val validator = validator()
        val profile = EmergencyProfile(
            sms_recipients = listOf("+20123456789"),
            delete_allowlist = listOf(DeleteTarget(path = "/", recursive = true))
        )

        val report = validator.validate(profile, ShizukuPermissionState())

        assertFalse(report.live_ready)
        assertFalse(report.dry_run_ready)
        assertTrue(report.live_blocking_issues.contains("invalid_delete_allowlist"))
        assertTrue(report.dry_run_blocking_issues.contains("invalid_delete_allowlist"))
    }

    @Test
    fun `invalid advanced shell command blocks both live and dry run`() {
        val validator = validator()
        val profile = EmergencyProfile(
            sms_recipients = listOf("+20123456789"),
            advanced_shell_commands = listOf(
                ShellCommandSpec(
                    id = "cmd_1",
                    label = "invalid",
                    argv = emptyList(),
                    raw_shell = null,
                    timeout_seconds = 10,
                    continue_on_failure = true,
                    enabled = true
                )
            )
        )

        val report = validator.validate(profile, ShizukuPermissionState())

        assertFalse(report.live_ready)
        assertFalse(report.dry_run_ready)
        assertTrue(report.live_blocking_issues.contains("invalid_advanced_shell_commands"))
        assertTrue(report.dry_run_blocking_issues.contains("invalid_advanced_shell_commands"))
    }

    @Test
    fun `missing recipients blocks both live and dry run`() {
        val validator = validator()
        val report = validator.validate(EmergencyProfile(), ShizukuPermissionState())

        assertFalse(report.live_ready)
        assertFalse(report.dry_run_ready)
        assertTrue(report.live_blocking_issues.contains("at_least_one_sms_recipient_required"))
        assertTrue(report.dry_run_blocking_issues.contains("at_least_one_sms_recipient_required"))
    }

    private fun validator(
        has_location_permission: Boolean = true,
        has_sms_permission: Boolean = true,
        binding_available: Boolean = true
    ): PreflightValidator {
        return PreflightValidator(
            app_context = FakeContext(),
            action_provider_registry = ActionProviderRegistry(providers = emptyList()),
            has_location_permission_checker = { has_location_permission },
            has_sms_permission_checker = { has_sms_permission },
            binding_available_checker = { binding_available },
            intent_resolver = { true }
        )
    }

    private class FakeContext : ContextWrapper(null)
}
