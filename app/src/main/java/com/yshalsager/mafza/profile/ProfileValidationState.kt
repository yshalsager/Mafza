package com.yshalsager.mafza.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yshalsager.mafza.R
import com.yshalsager.mafza.core.contracts.DeleteTarget
import com.yshalsager.mafza.core.contracts.ShellCommandSpec

internal data class ProfileValidationResult(
    val issues: List<ProfileValidationIssue>,
    val first_invalid_uninstall_package_index: Int,
    val first_invalid_delete_target_index: Int,
    val first_invalid_shell_timeout_index: Int,
    val first_invalid_shell_payload_index: Int,
    val first_invalid_binding_package_index: Int,
    val first_invalid_binding_policy_order_index: Int,
    val first_invalid_telegram_config_index: Int,
    val first_invalid_telegram_timeout_index: Int,
    val first_invalid_telegram_policy_order_index: Int,
    val first_invalid_intent_timeout_index: Int,
    val first_invalid_intent_policy_order_index: Int
)

@Composable
internal fun build_profile_validation_result(
    cancel_window_error: String?,
    location_timeout_error: String?,
    sms_timeout_error: String?,
    intent_timeout_error: String?,
    sms_action_enabled: Boolean,
    uninstall_action_enabled: Boolean,
    delete_action_enabled: Boolean,
    shell_action_enabled: Boolean,
    normalized_sms_recipients: List<String>,
    normalized_uninstall_packages: List<String>,
    configured_delete_targets: List<DeleteTarget>,
    configured_shell_commands: List<ShellCommandSpec>,
    uninstall_packages: List<String>,
    delete_targets: List<EditableDeleteTarget>,
    advanced_shell_commands: List<EditableShellCommand>,
    telegram_bot_actions: List<EditableTelegramBotAction>,
    message_app_bindings: List<EditableMessageBinding>,
    intent_actions: List<EditableIntentAction>
): ProfileValidationResult {
    val invalid_uninstall_package_count = uninstall_packages.count { package_name ->
        val trimmed = package_name.trim()
        trimmed.isNotEmpty() && !PROFILE_PACKAGE_NAME_REGEX.matches(trimmed)
    }
    val invalid_delete_target_count = delete_targets.count { target ->
        (target.path.isNotBlank() || target.content_uri.isNotBlank()) && !delete_target_input_valid(target)
    }
    val invalid_shell_timeout_count = advanced_shell_commands.count { command ->
        !is_int_in_range(command.timeout_seconds, PROFILE_MIN_STEP_TIMEOUT_SECONDS, PROFILE_MAX_ADVANCED_SHELL_TIMEOUT_SECONDS)
    }
    val invalid_shell_command_payload_count = advanced_shell_commands.count { command ->
        command.enabled && command.raw_shell.trim().isEmpty() && split_multiline_values(command.argv_multiline).isEmpty()
    }
    val invalid_binding_package_count = message_app_bindings.count { binding ->
        binding.enabled && binding.package_name.trim().isEmpty()
    }
    val invalid_binding_policy_order_count = message_app_bindings.count { binding ->
        binding.policy_mode == ProfilePolicyMode.OVERRIDE &&
            !is_int_in_range(binding.policy_execution_order, PROFILE_MIN_POLICY_ORDER, PROFILE_MAX_POLICY_ORDER)
    }
    val invalid_telegram_config_count = telegram_bot_actions.count { action ->
        action.enabled && (action.bot_token.trim().isEmpty() || action.chat_id.trim().isEmpty())
    }
    val invalid_telegram_timeout_count = telegram_bot_actions.count { action ->
        !is_int_in_range(action.timeout_seconds, PROFILE_MIN_STEP_TIMEOUT_SECONDS, PROFILE_MAX_TELEGRAM_TIMEOUT_SECONDS)
    }
    val invalid_telegram_policy_order_count = telegram_bot_actions.count { action ->
        action.policy_mode == ProfilePolicyMode.OVERRIDE &&
            !is_int_in_range(action.policy_execution_order, PROFILE_MIN_POLICY_ORDER, PROFILE_MAX_POLICY_ORDER)
    }
    val invalid_intent_timeout_count = intent_actions.count { intent_action ->
        !is_int_in_range(intent_action.timeout_seconds, PROFILE_MIN_STEP_TIMEOUT_SECONDS, PROFILE_MAX_INTENT_TIMEOUT_SECONDS)
    }
    val invalid_intent_policy_order_count = intent_actions.count { intent_action ->
        intent_action.policy_mode == ProfilePolicyMode.OVERRIDE &&
            !is_int_in_range(intent_action.policy_execution_order, PROFILE_MIN_POLICY_ORDER, PROFILE_MAX_POLICY_ORDER)
    }

    val first_invalid_uninstall_package_index = uninstall_packages.indexOfFirst { package_name ->
        val trimmed = package_name.trim()
        trimmed.isNotEmpty() && !PROFILE_PACKAGE_NAME_REGEX.matches(trimmed)
    }
    val first_invalid_delete_target_index = delete_targets.indexOfFirst { target ->
        (target.path.isNotBlank() || target.content_uri.isNotBlank()) && !delete_target_input_valid(target)
    }
    val first_invalid_shell_timeout_index = advanced_shell_commands.indexOfFirst { command ->
        !is_int_in_range(command.timeout_seconds, PROFILE_MIN_STEP_TIMEOUT_SECONDS, PROFILE_MAX_ADVANCED_SHELL_TIMEOUT_SECONDS)
    }
    val first_invalid_shell_payload_index = advanced_shell_commands.indexOfFirst { command ->
        command.enabled && command.raw_shell.trim().isEmpty() && split_multiline_values(command.argv_multiline).isEmpty()
    }
    val first_invalid_binding_package_index = message_app_bindings.indexOfFirst { binding ->
        binding.enabled && binding.package_name.trim().isEmpty()
    }
    val first_invalid_binding_policy_order_index = message_app_bindings.indexOfFirst { binding ->
        binding.policy_mode == ProfilePolicyMode.OVERRIDE &&
            !is_int_in_range(binding.policy_execution_order, PROFILE_MIN_POLICY_ORDER, PROFILE_MAX_POLICY_ORDER)
    }
    val first_invalid_telegram_config_index = telegram_bot_actions.indexOfFirst { action ->
        action.enabled && (action.bot_token.trim().isEmpty() || action.chat_id.trim().isEmpty())
    }
    val first_invalid_telegram_timeout_index = telegram_bot_actions.indexOfFirst { action ->
        !is_int_in_range(action.timeout_seconds, PROFILE_MIN_STEP_TIMEOUT_SECONDS, PROFILE_MAX_TELEGRAM_TIMEOUT_SECONDS)
    }
    val first_invalid_telegram_policy_order_index = telegram_bot_actions.indexOfFirst { action ->
        action.policy_mode == ProfilePolicyMode.OVERRIDE &&
            !is_int_in_range(action.policy_execution_order, PROFILE_MIN_POLICY_ORDER, PROFILE_MAX_POLICY_ORDER)
    }
    val first_invalid_intent_timeout_index = intent_actions.indexOfFirst { intent_action ->
        !is_int_in_range(intent_action.timeout_seconds, PROFILE_MIN_STEP_TIMEOUT_SECONDS, PROFILE_MAX_INTENT_TIMEOUT_SECONDS)
    }
    val first_invalid_intent_policy_order_index = intent_actions.indexOfFirst { intent_action ->
        intent_action.policy_mode == ProfilePolicyMode.OVERRIDE &&
            !is_int_in_range(intent_action.policy_execution_order, PROFILE_MIN_POLICY_ORDER, PROFILE_MAX_POLICY_ORDER)
    }

    val issues = buildList {
        if (cancel_window_error != null) {
            add(
                ProfileValidationIssue(
                    key = ProfileValidationIssueKey.CANCEL_WINDOW,
                    message = stringResource(R.string.profile_validation_cancel_window)
                )
            )
        }
        if (location_timeout_error != null) {
            add(
                ProfileValidationIssue(
                    key = ProfileValidationIssueKey.LOCATION_TIMEOUT,
                    message = stringResource(R.string.profile_validation_location_timeout)
                )
            )
        }
        if (sms_timeout_error != null) {
            add(
                ProfileValidationIssue(
                    key = ProfileValidationIssueKey.SMS_TIMEOUT,
                    message = stringResource(R.string.profile_validation_sms_timeout)
                )
            )
        }
        if (intent_timeout_error != null) {
            add(
                ProfileValidationIssue(
                    key = ProfileValidationIssueKey.INTENT_TIMEOUT,
                    message = stringResource(R.string.profile_validation_intent_timeout)
                )
            )
        }
        if (sms_action_enabled && normalized_sms_recipients.isEmpty()) {
            add(
                ProfileValidationIssue(
                    key = ProfileValidationIssueKey.SMS_RECIPIENTS_REQUIRED,
                    message = stringResource(R.string.profile_validation_sms_recipients_required)
                )
            )
        }
        if (uninstall_action_enabled && normalized_uninstall_packages.isEmpty()) {
            add(
                ProfileValidationIssue(
                    key = ProfileValidationIssueKey.UNINSTALL_REQUIRED,
                    message = stringResource(R.string.profile_validation_uninstall_required)
                )
            )
        }
        if (invalid_uninstall_package_count > 0) {
            add(
                ProfileValidationIssue(
                    key = ProfileValidationIssueKey.UNINSTALL_INVALID,
                    message = stringResource(R.string.profile_validation_uninstall_invalid, invalid_uninstall_package_count)
                )
            )
        }
        if (delete_action_enabled && configured_delete_targets.isEmpty()) {
            add(
                ProfileValidationIssue(
                    key = ProfileValidationIssueKey.DELETE_REQUIRED,
                    message = stringResource(R.string.profile_validation_delete_required)
                )
            )
        }
        if (invalid_delete_target_count > 0) {
            add(
                ProfileValidationIssue(
                    key = ProfileValidationIssueKey.DELETE_INVALID,
                    message = stringResource(R.string.profile_validation_delete_invalid, invalid_delete_target_count)
                )
            )
        }
        if (shell_action_enabled && configured_shell_commands.none { it.enabled }) {
            add(
                ProfileValidationIssue(
                    key = ProfileValidationIssueKey.SHELL_REQUIRED,
                    message = stringResource(R.string.profile_validation_shell_required)
                )
            )
        }
        if (invalid_shell_timeout_count > 0) {
            add(
                ProfileValidationIssue(
                    key = ProfileValidationIssueKey.SHELL_TIMEOUT_INVALID,
                    message = stringResource(R.string.profile_validation_shell_timeout, invalid_shell_timeout_count)
                )
            )
        }
        if (invalid_shell_command_payload_count > 0) {
            add(
                ProfileValidationIssue(
                    key = ProfileValidationIssueKey.SHELL_PAYLOAD_INVALID,
                    message = stringResource(R.string.profile_validation_shell_payload, invalid_shell_command_payload_count)
                )
            )
        }
        if (invalid_binding_package_count > 0) {
            add(
                ProfileValidationIssue(
                    key = ProfileValidationIssueKey.BINDING_PACKAGE_INVALID,
                    message = stringResource(R.string.profile_validation_binding_package, invalid_binding_package_count)
                )
            )
        }
        if (invalid_binding_policy_order_count > 0) {
            add(
                ProfileValidationIssue(
                    key = ProfileValidationIssueKey.BINDING_ORDER_INVALID,
                    message = stringResource(R.string.profile_validation_binding_order, invalid_binding_policy_order_count)
                )
            )
        }
        if (invalid_telegram_config_count > 0) {
            add(
                ProfileValidationIssue(
                    key = ProfileValidationIssueKey.TELEGRAM_CONFIG_INVALID,
                    message = stringResource(R.string.profile_validation_telegram_config, invalid_telegram_config_count)
                )
            )
        }
        if (invalid_telegram_timeout_count > 0) {
            add(
                ProfileValidationIssue(
                    key = ProfileValidationIssueKey.TELEGRAM_TIMEOUT_INVALID,
                    message = stringResource(R.string.profile_validation_telegram_timeout, invalid_telegram_timeout_count)
                )
            )
        }
        if (invalid_telegram_policy_order_count > 0) {
            add(
                ProfileValidationIssue(
                    key = ProfileValidationIssueKey.TELEGRAM_ORDER_INVALID,
                    message = stringResource(R.string.profile_validation_telegram_order, invalid_telegram_policy_order_count)
                )
            )
        }
        if (invalid_intent_timeout_count > 0) {
            add(
                ProfileValidationIssue(
                    key = ProfileValidationIssueKey.INTENT_STEP_TIMEOUT_INVALID,
                    message = stringResource(R.string.profile_validation_intent_step_timeout, invalid_intent_timeout_count)
                )
            )
        }
        if (invalid_intent_policy_order_count > 0) {
            add(
                ProfileValidationIssue(
                    key = ProfileValidationIssueKey.INTENT_ORDER_INVALID,
                    message = stringResource(R.string.profile_validation_intent_order, invalid_intent_policy_order_count)
                )
            )
        }
    }

    return ProfileValidationResult(
        issues = issues,
        first_invalid_uninstall_package_index = first_invalid_uninstall_package_index,
        first_invalid_delete_target_index = first_invalid_delete_target_index,
        first_invalid_shell_timeout_index = first_invalid_shell_timeout_index,
        first_invalid_shell_payload_index = first_invalid_shell_payload_index,
        first_invalid_binding_package_index = first_invalid_binding_package_index,
        first_invalid_binding_policy_order_index = first_invalid_binding_policy_order_index,
        first_invalid_telegram_config_index = first_invalid_telegram_config_index,
        first_invalid_telegram_timeout_index = first_invalid_telegram_timeout_index,
        first_invalid_telegram_policy_order_index = first_invalid_telegram_policy_order_index,
        first_invalid_intent_timeout_index = first_invalid_intent_timeout_index,
        first_invalid_intent_policy_order_index = first_invalid_intent_policy_order_index
    )
}
