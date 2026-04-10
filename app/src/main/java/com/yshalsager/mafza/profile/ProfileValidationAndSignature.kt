package com.yshalsager.mafza.profile

import com.yshalsager.mafza.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.EmergencyProfile

internal fun normalize_string_items(items: List<String>): List<String> {
    return items
        .map(String::trim)
        .filter { it.isNotEmpty() }
        .distinct()
}

internal fun normalize_sms_recipients(recipients: List<String>): List<String> {
    return normalize_string_items(recipients)
}

internal fun split_multiline_values(input: String): List<String> {
    return input
        .split('\n')
        .map(String::trim)
        .filter { it.isNotEmpty() }
}

internal fun build_profile_editor_signature(
    sms_recipients: List<String>,
    notify_target_input: String,
    message_template_input: String,
    cancel_window_input: String,
    location_timeout_input: String,
    opencellid_api_key_input: String,
    sms_timeout_input: String,
    intent_timeout_input: String,
    action_policy_rows: List<EditableActionPolicyRow>,
    telegram_bot_actions: List<EditableTelegramBotAction>,
    message_app_bindings: List<EditableMessageBinding>,
    intent_actions: List<EditableIntentAction>,
    uninstall_packages: List<String>,
    delete_targets: List<EditableDeleteTarget>,
    advanced_shell_commands: List<EditableShellCommand>,
    removed_action_policy_map: Map<String, ActionId>,
    removed_telegram_policy_keys: Set<String>,
    removed_intent_policy_keys: Set<String>,
    destructive_actions_enabled: Boolean,
    triggers_enabled: Boolean,
    self_uninstall_enabled: Boolean
): String {
    val removed_action_policy_signature = removed_action_policy_map.entries
        .sortedBy { it.key }
        .joinToString("|") { "${it.key}:${it.value.name}" }
    val removed_telegram_policy_signature = removed_telegram_policy_keys.sorted().joinToString("|")
    val removed_intent_policy_signature = removed_intent_policy_keys.sorted().joinToString("|")

    return listOf(
        sms_recipients.joinToString("\u001f"),
        notify_target_input,
        message_template_input,
        cancel_window_input,
        location_timeout_input,
        opencellid_api_key_input,
        sms_timeout_input,
        intent_timeout_input,
        action_policy_rows.joinToString("\u001e"),
        telegram_bot_actions.joinToString("\u001e"),
        message_app_bindings.joinToString("\u001e"),
        intent_actions.joinToString("\u001e"),
        uninstall_packages.joinToString("\u001f"),
        delete_targets.joinToString("\u001e"),
        advanced_shell_commands.joinToString("\u001e"),
        removed_action_policy_signature,
        removed_telegram_policy_signature,
        removed_intent_policy_signature,
        destructive_actions_enabled.toString(),
        triggers_enabled.toString(),
        self_uninstall_enabled.toString()
    ).joinToString("\u001d")
}

internal fun build_profile_editor_signature_from_profile(profile: EmergencyProfile): String {
    val editable_message_bindings = profile.action_bindings
        .filter { it.action_id == ActionId.NOTIFY_MESSAGE_APP }
        .mapIndexed { index, binding ->
            to_editable_message_binding(
                binding = binding,
                action_policies = profile.action_policies,
                default_execution_order = index + 1
            )
        }
    val editable_intent_actions = profile.intent_actions.mapIndexed { index, intent_action ->
        to_editable_intent_action(
            intent_action = intent_action,
            action_policies = profile.action_policies,
            default_execution_order = index + 1
        )
    }
    val editable_telegram_bot_actions = profile.telegram_bot_actions.mapIndexed { index, telegram_action ->
        to_editable_telegram_bot_action(
            telegram_bot_action = telegram_action,
            action_policies = profile.action_policies,
            default_execution_order = index + 1
        )
    }

    return build_profile_editor_signature(
        sms_recipients = profile.sms_recipients,
        notify_target_input = profile.notify_target,
        message_template_input = profile.message_template,
        cancel_window_input = profile.cancel_window_seconds.toString(),
        location_timeout_input = profile.location_timeout_seconds.toString(),
        opencellid_api_key_input = profile.opencellid_api_key,
        sms_timeout_input = profile.sms_timeout_seconds.toString(),
        intent_timeout_input = profile.intent_timeout_seconds.toString(),
        action_policy_rows = extract_editable_action_policy_rows(profile.action_policies),
        telegram_bot_actions = editable_telegram_bot_actions,
        message_app_bindings = editable_message_bindings,
        intent_actions = editable_intent_actions,
        uninstall_packages = profile.uninstall_allowlist,
        delete_targets = profile.delete_allowlist.map(::to_editable_delete_target),
        advanced_shell_commands = profile.advanced_shell_commands.map(::to_editable_shell_command),
        removed_action_policy_map = emptyMap(),
        removed_telegram_policy_keys = emptySet(),
        removed_intent_policy_keys = emptySet(),
        destructive_actions_enabled = profile.destructive_actions_enabled,
        triggers_enabled = profile.triggers_enabled,
        self_uninstall_enabled = profile.self_uninstall_enabled
    )
}

internal fun delete_target_input_valid(target: EditableDeleteTarget): Boolean {
    val raw_path = target.path.trim()
    val raw_content_uri = target.content_uri.trim()
    val has_path = raw_path.isNotEmpty()
    val has_content_uri = raw_content_uri.isNotEmpty()
    if (has_path == has_content_uri) return false
    if (has_content_uri) return delete_target_content_uri_valid(raw_content_uri)
    return delete_target_path_valid(raw_path)
}

@Composable
internal fun delete_target_error_message(path: String, content_uri: String): String? {
    val trimmed_path = path.trim()
    val trimmed_content_uri = content_uri.trim()
    val has_path = trimmed_path.isNotEmpty()
    val has_content_uri = trimmed_content_uri.isNotEmpty()
    if (!has_path && !has_content_uri) return null
    if (has_path && has_content_uri) return stringResource(R.string.profile_delete_target_error_source_conflict)
    if (has_content_uri) {
        return if (delete_target_content_uri_valid(trimmed_content_uri)) {
            null
        } else {
            stringResource(R.string.profile_delete_target_error_content_uri)
        }
    }
    val path_file = java.io.File(trimmed_path)
    val canonical_path = runCatching { path_file.canonicalPath }.getOrNull()
        ?: return stringResource(R.string.profile_delete_target_error_canonical)
    if (canonical_path != trimmed_path) return stringResource(R.string.profile_delete_target_error_canonical)
    if (canonical_path == "/") return stringResource(R.string.profile_delete_target_error_root)
    if (java.nio.file.Files.isSymbolicLink(path_file.toPath())) {
        return stringResource(R.string.profile_delete_target_error_symlink)
    }
    return null
}

private fun delete_target_path_valid(raw_path: String): Boolean {
    if (raw_path.isEmpty()) return false
    if (!raw_path.startsWith("/")) return false
    val path_file = java.io.File(raw_path)
    val canonical_path = runCatching { path_file.canonicalPath }.getOrNull() ?: return false
    if (canonical_path != raw_path) return false
    if (canonical_path == "/") return false
    if (java.nio.file.Files.isSymbolicLink(path_file.toPath())) return false
    return true
}

private fun delete_target_content_uri_valid(raw_content_uri: String): Boolean {
    if (raw_content_uri.isEmpty()) return false
    if (!raw_content_uri.startsWith("content://", ignoreCase = true)) return false

    val without_scheme = raw_content_uri.substring(10)
    val authority = without_scheme.substringBefore('/').trim()
    if (authority.isEmpty()) return false

    val normalized_content_uri = raw_content_uri.lowercase()
    return normalized_content_uri.contains("/tree/") || normalized_content_uri.contains("/document/")
}

@Composable
internal fun int_range_error(
    value: String,
    min_value: Int,
    max_value: Int
): String? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return stringResource(R.string.profile_validation_number_required)
    val parsed = trimmed.toIntOrNull() ?: return stringResource(R.string.profile_validation_number_required)
    return if (parsed in min_value..max_value) {
        null
    } else {
        stringResource(R.string.profile_validation_range, min_value, max_value)
    }
}

internal fun is_int_in_range(
    value: String,
    min_value: Int,
    max_value: Int
): Boolean {
    val parsed = value.trim().toIntOrNull() ?: return false
    return parsed in min_value..max_value
}

internal fun parse_order_or_fallback(
    value: String,
    fallback: Int
): Int {
    return value.trim().toIntOrNull() ?: fallback
}

internal fun parse_int_or_fallback(
    value: String,
    fallback: Int,
    min_value: Int,
    max_value: Int
): Int {
    val parsed = value.trim().toIntOrNull() ?: return fallback
    return parsed.coerceIn(min_value, max_value)
}
