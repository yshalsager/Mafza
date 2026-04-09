package com.yshalsager.mafza.preflight

import android.content.ContextWrapper
import com.yshalsager.mafza.emergency.providers.ActionProviderRegistry
import com.yshalsager.mafza.emergency.telegram.TelegramBotCheckResult
import com.yshalsager.mafza.emergency.telegram.TelegramBotClient
import com.yshalsager.mafza.emergency.telegram.TelegramBotSendResult
import com.yshalsager.mafza.core.contracts.ActionBinding
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ActionPolicy
import com.yshalsager.mafza.core.contracts.ActionPolicyKeys
import com.yshalsager.mafza.core.contracts.DeleteTarget
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.IntentActionSpec
import com.yshalsager.mafza.core.contracts.ShellCommandSpec
import com.yshalsager.mafza.core.contracts.TelegramBotActionSpec
import com.yshalsager.mafza.shizuku.ShizukuPermissionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreflightValidatorTest {
    @Test
    fun `required telegram bot action blocks live when bot is unreachable`() {
        val validator = validator(
            telegram_bot_client = FakeTelegramBotClient(ready = false)
        )
        val telegram_action = TelegramBotActionSpec(
            id = "telegram_primary",
            label = "Primary",
            bot_token = "123456:abcdefghijklmnopqrstuvwxyzABCDE",
            chat_id = "-1001234567890",
            enabled = true
        )
        val profile = EmergencyProfile(
            telegram_bot_actions = listOf(telegram_action),
            action_policies = listOf(
                ActionPolicy(
                    action_id = ActionId.NOTIFY_TELEGRAM_BOT,
                    policy_key = ActionPolicyKeys.for_telegram_bot(telegram_action.id),
                    enabled = true,
                    required = true,
                    continue_on_failure = true,
                    execution_order = 1
                )
            )
        )

        val report = validator.validate(profile, ShizukuPermissionState())

        assertFalse(report.live_ready)
        assertTrue(report.live_blocking_issues.contains("required_telegram_bot_unavailable_telegram_primary"))
    }

    @Test
    fun `optional telegram bot action does not block live when bot is unreachable`() {
        val validator = validator(
            telegram_bot_client = FakeTelegramBotClient(ready = false)
        )
        val telegram_action = TelegramBotActionSpec(
            id = "telegram_backup",
            label = "Backup",
            bot_token = "123456:abcdefghijklmnopqrstuvwxyzABCDE",
            chat_id = "@backup_channel",
            enabled = true
        )
        val profile = EmergencyProfile(
            telegram_bot_actions = listOf(telegram_action),
            action_policies = listOf(
                ActionPolicy(
                    action_id = ActionId.NOTIFY_TELEGRAM_BOT,
                    policy_key = ActionPolicyKeys.for_telegram_bot(telegram_action.id),
                    enabled = true,
                    required = false,
                    continue_on_failure = true,
                    execution_order = 1
                )
            )
        )

        val report = validator.validate(profile, ShizukuPermissionState())

        assertTrue(report.live_ready)
        assertTrue(report.warnings.contains("optional_telegram_bot_unavailable_telegram_backup"))
    }

    @Test
    fun `invalid telegram bot config blocks live and dry run`() {
        val validator = validator()
        val profile = EmergencyProfile(
            telegram_bot_actions = listOf(
                TelegramBotActionSpec(
                    id = "telegram_invalid",
                    label = "Invalid",
                    bot_token = "bad-token",
                    chat_id = "",
                    enabled = true
                )
            ),
            action_policies = listOf(
                ActionPolicy(
                    action_id = ActionId.NOTIFY_TELEGRAM_BOT,
                    policy_key = ActionPolicyKeys.for_telegram_bot("telegram_invalid"),
                    enabled = true,
                    required = true,
                    continue_on_failure = true,
                    execution_order = 1
                )
            )
        )

        val report = validator.validate(profile, ShizukuPermissionState())

        assertFalse(report.live_ready)
        assertFalse(report.dry_run_ready)
        assertTrue(report.live_blocking_issues.contains("invalid_telegram_bot_config_telegram_invalid"))
        assertTrue(report.dry_run_blocking_issues.contains("invalid_telegram_bot_config_telegram_invalid"))
    }

    @Test
    fun `dry run stays ready when only live environment checks fail`() {
        val validator = validator(
            has_location_permission = false,
            has_sms_permission = false,
            has_phone_state_permission = false
        )
        val profile = EmergencyProfile(
            sms_recipients = listOf("+20123456789"),
            destructive_actions_enabled = true,
            uninstall_allowlist = listOf("com.example.target")
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
        assertTrue(report.live_blocking_issues.contains("missing_read_phone_state_permission"))
        assertTrue(report.live_blocking_issues.contains("missing_send_sms_permission"))
        assertTrue(report.live_blocking_issues.contains("shizuku_permission_required_for_destructive_actions"))
    }

    @Test
    fun `missing phone-state permission blocks live only`() {
        val validator = validator(
            has_phone_state_permission = false
        )
        val profile = EmergencyProfile(
            sms_recipients = listOf("+20123456789")
        )

        val report = validator.validate(profile, ShizukuPermissionState())

        assertFalse(report.live_ready)
        assertTrue(report.dry_run_ready)
        assertTrue(report.live_blocking_issues.contains("missing_read_phone_state_permission"))
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
    fun `required policy can target one binding when multiple bindings exist`() {
        val validator = validator(binding_available = false)
        val primary_binding = ActionBinding(
            action_id = ActionId.NOTIFY_MESSAGE_APP,
            binding_id = "primary",
            package_name = "com.example.one",
            activity_name = null,
            enabled = true
        )
        val secondary_binding = ActionBinding(
            action_id = ActionId.NOTIFY_MESSAGE_APP,
            binding_id = "secondary",
            package_name = "com.example.two",
            activity_name = null,
            enabled = true
        )
        val profile = EmergencyProfile(
            sms_recipients = listOf("+20123456789"),
            action_bindings = listOf(primary_binding, secondary_binding),
            action_policies = listOf(
                ActionPolicy(
                    action_id = ActionId.NOTIFY_MESSAGE_APP,
                    policy_key = ActionPolicyKeys.for_binding(primary_binding),
                    enabled = true,
                    required = true,
                    continue_on_failure = true,
                    execution_order = 1
                ),
                ActionPolicy(
                    action_id = ActionId.NOTIFY_MESSAGE_APP,
                    policy_key = ActionPolicyKeys.for_binding(secondary_binding),
                    enabled = true,
                    required = false,
                    continue_on_failure = true,
                    execution_order = 2
                )
            )
        )

        val report = validator.validate(profile, ShizukuPermissionState())

        assertFalse(report.live_ready)
        assertTrue(report.live_blocking_issues.contains("required_binding_unavailable_notify_message_app"))
        assertFalse(report.live_blocking_issues.contains("duplicate_enabled_action_bindings"))
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
                    binding_id = "message_app_1",
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
    fun `missing binding id blocks both live and dry run`() {
        val validator = validator()
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
                    required = false,
                    continue_on_failure = true,
                    execution_order = 1
                )
            )
        )

        val report = validator.validate(profile, ShizukuPermissionState())

        assertFalse(report.live_ready)
        assertFalse(report.dry_run_ready)
        assertTrue(report.live_blocking_issues.contains("action_binding_missing_binding_id"))
        assertTrue(report.dry_run_blocking_issues.contains("action_binding_missing_binding_id"))
    }

    @Test
    fun `duplicate binding id blocks both live and dry run`() {
        val validator = validator()
        val profile = EmergencyProfile(
            sms_recipients = listOf("+20123456789"),
            action_bindings = listOf(
                ActionBinding(
                    action_id = ActionId.NOTIFY_MESSAGE_APP,
                    binding_id = "same_id",
                    package_name = "com.example.one",
                    activity_name = null,
                    enabled = true
                ),
                ActionBinding(
                    action_id = ActionId.NOTIFY_MESSAGE_APP,
                    binding_id = "same_id",
                    package_name = "com.example.two",
                    activity_name = null,
                    enabled = true
                )
            ),
            action_policies = listOf(
                ActionPolicy(
                    action_id = ActionId.NOTIFY_MESSAGE_APP,
                    policy_key = "binding:notify_message_app:same_id",
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
        assertTrue(report.live_blocking_issues.contains("action_binding_duplicate_binding_id"))
        assertTrue(report.dry_run_blocking_issues.contains("action_binding_duplicate_binding_id"))
    }

    @Test
    fun `duplicate policy keys block both live and dry run`() {
        val validator = validator()
        val profile = EmergencyProfile(
            sms_recipients = listOf("+20123456789"),
            action_policies = listOf(
                ActionPolicy(
                    action_id = ActionId.NOTIFY_MESSAGE_APP,
                    policy_key = "binding:notify_message_app:dup",
                    enabled = true,
                    required = false,
                    continue_on_failure = true,
                    execution_order = 1
                ),
                ActionPolicy(
                    action_id = ActionId.NOTIFY_MESSAGE_APP,
                    policy_key = "binding:notify_message_app:dup",
                    enabled = true,
                    required = true,
                    continue_on_failure = true,
                    execution_order = 2
                )
            )
        )

        val report = validator.validate(profile, ShizukuPermissionState())

        assertFalse(report.live_ready)
        assertFalse(report.dry_run_ready)
        assertTrue(report.live_blocking_issues.contains("action_policy_invalid_or_duplicate_policy_key"))
        assertTrue(report.dry_run_blocking_issues.contains("action_policy_invalid_or_duplicate_policy_key"))
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
    fun `saf delete target does not require shizuku permission`() {
        val validator = validator()
        val profile = EmergencyProfile(
            sms_recipients = listOf("+20123456789"),
            destructive_actions_enabled = true,
            delete_allowlist = listOf(
                DeleteTarget(
                    path = "",
                    content_uri = "content://com.android.externalstorage.documents/tree/primary%3ADownload",
                    recursive = true
                )
            )
        )

        val report = validator.validate(
            profile = profile,
            shizuku_permission_state = ShizukuPermissionState(
                is_running = false,
                is_permission_granted = false
            )
        )

        assertTrue(report.live_ready)
        assertFalse(report.live_blocking_issues.contains("shizuku_permission_required_for_destructive_actions"))
    }

    @Test
    fun `non saf content uri delete target blocks both live and dry run`() {
        val validator = validator()
        val profile = EmergencyProfile(
            sms_recipients = listOf("+20123456789"),
            delete_allowlist = listOf(
                DeleteTarget(
                    path = "",
                    content_uri = "content://com.example.provider/items/123",
                    recursive = false
                )
            )
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
    fun `missing recipients do not block when sms is optional`() {
        val validator = validator()
        val report = validator.validate(EmergencyProfile(), ShizukuPermissionState())

        assertTrue(report.live_ready)
        assertTrue(report.dry_run_ready)
        assertFalse(report.live_blocking_issues.contains("at_least_one_sms_recipient_required"))
        assertFalse(report.dry_run_blocking_issues.contains("at_least_one_sms_recipient_required"))
    }

    @Test
    fun `missing recipients block when sms action is required`() {
        val validator = validator()
        val report = validator.validate(
            EmergencyProfile(
                action_policies = listOf(
                    ActionPolicy(
                        action_id = ActionId.SEND_SMS,
                        enabled = true,
                        required = true,
                        continue_on_failure = true,
                        execution_order = 1
                    )
                )
            ),
            ShizukuPermissionState()
        )

        assertFalse(report.live_ready)
        assertFalse(report.dry_run_ready)
        assertTrue(report.live_blocking_issues.contains("at_least_one_sms_recipient_required"))
        assertTrue(report.dry_run_blocking_issues.contains("at_least_one_sms_recipient_required"))
    }

    private fun validator(
        has_location_permission: Boolean = true,
        has_sms_permission: Boolean = true,
        has_phone_state_permission: Boolean = true,
        binding_available: Boolean = true,
        telegram_bot_client: TelegramBotClient = FakeTelegramBotClient(ready = true)
    ): PreflightValidator {
        return PreflightValidator(
            app_context = FakeContext(),
            action_provider_registry = ActionProviderRegistry(providers = emptyList()),
            telegram_bot_client = telegram_bot_client,
            has_location_permission_checker = { has_location_permission },
            has_sms_permission_checker = { has_sms_permission },
            has_phone_state_permission_checker = { has_phone_state_permission },
            binding_available_checker = { binding_available },
            intent_resolver = { true }
        )
    }

    private class FakeContext : ContextWrapper(null)

    private class FakeTelegramBotClient(
        private val ready: Boolean
    ) : TelegramBotClient {
        override fun check_bot(bot_token: String, timeout_seconds: Int): TelegramBotCheckResult {
            return TelegramBotCheckResult(
                ready = ready,
                details = if (ready) "telegram_get_me_ok" else "telegram_unavailable"
            )
        }

        override fun send_message(
            bot_token: String,
            chat_id: String,
            text: String,
            timeout_seconds: Int
        ): TelegramBotSendResult {
            return TelegramBotSendResult(
                success = ready,
                details = if (ready) "telegram_ok" else "telegram_unavailable"
            )
        }
    }
}
