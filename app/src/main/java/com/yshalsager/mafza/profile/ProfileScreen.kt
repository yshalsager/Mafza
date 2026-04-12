package com.yshalsager.mafza.profile

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yshalsager.mafza.R
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ActionPolicyKeys
import com.yshalsager.mafza.core.contracts.BackupPreview
import com.yshalsager.mafza.core.contracts.BackupService
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.data.backup.BACKUP_FILE_EXTENSION
import com.yshalsager.mafza.core.data.profile.EncryptedProfileStore
import com.yshalsager.mafza.emergency.providers.ActionProviderRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    profile: EmergencyProfile,
    profile_store: EncryptedProfileStore,
    app_context: Context,
    action_provider_registry: ActionProviderRegistry,
    backup_service: BackupService,
    add_action_nonce: Int,
    on_add_action_nonce_consumed: () -> Unit,
    on_backup_restore_complete: () -> Unit
) {
    val app_scope = rememberCoroutineScope()

    var sms_recipients by remember(profile) {
        mutableStateOf(profile.sms_recipients)
    }
    var notify_target_input by remember(profile) { mutableStateOf(profile.notify_target) }
    var message_template_input by remember(profile) { mutableStateOf(profile.message_template) }
    var cancel_window_input by remember(profile) { mutableStateOf(profile.cancel_window_seconds.toString()) }
    var location_timeout_input by remember(profile) { mutableStateOf(profile.location_timeout_seconds.toString()) }
    var opencellid_api_key_input by remember(profile) { mutableStateOf(profile.opencellid_api_key) }
    var sms_timeout_input by remember(profile) { mutableStateOf(profile.sms_timeout_seconds.toString()) }
    var intent_timeout_input by remember(profile) { mutableStateOf(profile.intent_timeout_seconds.toString()) }
    var action_policy_rows by remember(profile) {
        mutableStateOf(
            extract_editable_action_policy_rows(profile.action_policies)
        )
    }
    var removed_action_policy_map by remember(profile) {
        mutableStateOf<Map<String, ActionId>>(emptyMap())
    }
    var message_app_bindings by remember(profile) {
        mutableStateOf(
            profile.action_bindings
                .filter { it.action_id == ActionId.NOTIFY_MESSAGE_APP }
                .mapIndexed { index, binding ->
                    to_editable_message_binding(
                        binding = binding,
                        action_policies = profile.action_policies,
                        default_execution_order = index + 1
                    )
                }
        )
    }
    var telegram_bot_actions by remember(profile) {
        mutableStateOf(
            profile.telegram_bot_actions.mapIndexed { index, telegram_action ->
                to_editable_telegram_bot_action(
                    telegram_bot_action = telegram_action,
                    action_policies = profile.action_policies,
                    default_execution_order = index + 1
                )
            }
        )
    }
    var intent_actions by remember(profile) {
        mutableStateOf(
            profile.intent_actions.mapIndexed { index, intent_action ->
                to_editable_intent_action(
                    intent_action = intent_action,
                    action_policies = profile.action_policies,
                    default_execution_order = index + 1
                )
            }
        )
    }
    var uninstall_packages by remember(profile) {
        mutableStateOf(profile.uninstall_allowlist)
    }
    var delete_targets by remember(profile) {
        mutableStateOf(profile.delete_allowlist.map(::to_editable_delete_target))
    }
    var advanced_shell_commands by remember(profile) {
        mutableStateOf(profile.advanced_shell_commands.map(::to_editable_shell_command))
    }
    var removed_telegram_policy_keys by remember(profile) {
        mutableStateOf(setOf<String>())
    }
    var removed_intent_policy_keys by remember(profile) {
        mutableStateOf(setOf<String>())
    }
    var destructive_actions_enabled by remember(profile) { mutableStateOf(profile.destructive_actions_enabled) }
    var triggers_enabled by remember(profile) { mutableStateOf(profile.triggers_enabled) }
    var self_uninstall_enabled by remember(profile) { mutableStateOf(profile.self_uninstall_enabled) }
    var save_error_message by remember { mutableStateOf<String?>(null) }
    var saved_successfully by remember { mutableStateOf(false) }
    var is_saving by remember { mutableStateOf(false) }
    var show_destructive_enable_confirm by remember { mutableStateOf(false) }
    var show_self_uninstall_enable_confirm by remember { mutableStateOf(false) }
    var sms_contact_picker_index by remember { mutableStateOf<Int?>(null) }
    var sms_contact_picker_error_res_id by remember { mutableStateOf<Int?>(null) }
    var message_binding_picker_index by remember { mutableStateOf<Int?>(null) }
    var uninstall_package_picker_index by remember { mutableStateOf<Int?>(null) }
    var show_add_action_sheet by remember { mutableStateOf(false) }
    var add_action_group by remember { mutableStateOf<ProfileActionGroup?>(null) }
    var recent_add_action_types by remember { mutableStateOf<List<ProfileActionRowType>>(emptyList()) }
    var pending_sensitive_save by remember { mutableStateOf<(() -> Unit)?>(null) }
    var editing_action_row_id by remember { mutableStateOf<String?>(null) }
    var show_reorder_helper by remember { mutableStateOf(false) }
    var profile_settings_expanded by remember { mutableStateOf(false) }
    var message_settings_expanded by remember { mutableStateOf(false) }
    var location_lookup_settings_expanded by remember { mutableStateOf(false) }
    var timeouts_settings_expanded by remember { mutableStateOf(false) }
    var safety_settings_expanded by remember { mutableStateOf(false) }
    var backup_in_progress by remember { mutableStateOf(false) }
    var backup_status_message by remember { mutableStateOf<String?>(null) }
    var backup_error_message by remember { mutableStateOf<String?>(null) }
    var show_export_backup_dialog by remember { mutableStateOf(false) }
    var show_restore_passphrase_dialog by remember { mutableStateOf(false) }
    var show_restore_confirm_dialog by remember { mutableStateOf(false) }
    var pending_export_passphrase by remember { mutableStateOf<CharArray?>(null) }
    var pending_export_include_history by remember { mutableStateOf(false) }
    var pending_restore_uri by remember { mutableStateOf<Uri?>(null) }
    var restore_passphrase by remember { mutableStateOf<CharArray?>(null) }
    var restore_preview by remember { mutableStateOf<BackupPreview?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            pending_export_passphrase?.fill('\u0000')
            restore_passphrase?.fill('\u0000')
        }
    }
    val group_expansion = remember {
        mutableStateMapOf(
            ProfileActionGroup.COMMUNICATION to true,
            ProfileActionGroup.APP_INTENT to true,
            ProfileActionGroup.DESTRUCTIVE to true,
            ProfileActionGroup.FINALIZE to true
        )
    }
    val profile_list_state = rememberLazyListState()
    val message_share_apps = remember(app_context) { query_message_share_apps(app_context) }
    val message_binding_picker_options = remember(message_share_apps) {
        message_share_apps.map { option ->
            AppChooserOption(
                label = option.label,
                package_name = option.package_name,
                activity_name = option.activity_name
            )
        }
    }
    val installed_packages = remember(app_context) { query_installed_app_packages(app_context) }
    val uninstall_package_picker_options = remember(installed_packages) {
        installed_packages.map { option ->
            AppChooserOption(
                label = option.label,
                package_name = option.package_name
            )
        }
    }
    val keyguard_manager = remember(app_context) {
        app_context.getSystemService(KeyguardManager::class.java)
    }
    val sensitive_auth_launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pending_save = pending_sensitive_save
        pending_sensitive_save = null
        if (result.resultCode == Activity.RESULT_OK) {
            pending_save?.invoke()
            return@rememberLauncherForActivityResult
        }
        if (pending_save != null) {
            saved_successfully = false
            save_error_message = app_context.getString(R.string.profile_sensitive_auth_cancelled)
        }
    }
    val launch_sensitive_auth_for_save: () -> Boolean = launch@{
        if (keyguard_manager?.isDeviceSecure != true) {
            saved_successfully = false
            save_error_message = app_context.getString(R.string.profile_sensitive_auth_no_secure_lock)
            return@launch false
        }
        val intent = keyguard_manager.createConfirmDeviceCredentialIntent(
            app_context.getString(R.string.profile_sensitive_auth_title),
            app_context.getString(R.string.profile_sensitive_auth_subtitle)
        )
        if (intent == null) {
            saved_successfully = false
            save_error_message = app_context.getString(R.string.profile_sensitive_auth_unavailable)
            return@launch false
        }
        sensitive_auth_launcher.launch(intent)
        true
    }

    LaunchedEffect(add_action_nonce) {
        if (add_action_nonce <= 0) return@LaunchedEffect
        add_action_group = null
        show_add_action_sheet = true
        on_add_action_nonce_consumed()
    }

    val sms_contact_picker_launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contact_uri ->
        val target_index = sms_contact_picker_index
        sms_contact_picker_index = null
        if (contact_uri == null || target_index == null || target_index !in sms_recipients.indices) return@rememberLauncherForActivityResult

        val picked_number = resolve_contact_phone_number(app_context, contact_uri)
        if (picked_number.isNullOrBlank()) {
            sms_contact_picker_error_res_id = R.string.profile_sms_contact_pick_no_phone
            return@rememberLauncherForActivityResult
        }

        sms_recipients = sms_recipients.mapIndexed { index, recipient ->
            if (index == target_index) picked_number else recipient
        }
        sms_contact_picker_error_res_id = null
        saved_successfully = false
        save_error_message = null
    }

    val export_backup_launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { output_uri ->
        val passphrase = pending_export_passphrase
        val include_history = pending_export_include_history
        pending_export_passphrase = null
        pending_export_include_history = false
        if (output_uri == null || passphrase == null) {
            passphrase?.fill('\u0000')
            return@rememberLauncherForActivityResult
        }
        app_scope.launch {
            backup_in_progress = true
            backup_status_message = null
            backup_error_message = null
            runCatching {
                withContext(Dispatchers.IO) {
                    backup_service.exportEncryptedBackup(
                        output_uri = output_uri,
                        passphrase = passphrase,
                        includeHistory = include_history
                    )
                }
            }.onSuccess {
                backup_status_message = app_context.getString(R.string.profile_backup_export_success)
                backup_error_message = null
            }.onFailure { error ->
                backup_status_message = null
                backup_error_message = app_context.getString(
                    R.string.profile_backup_export_failed,
                    error.message ?: "unknown"
                )
            }
            passphrase.fill('\u0000')
            backup_in_progress = false
        }
    }

    val restore_backup_picker_launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { selected_uri ->
        if (selected_uri == null) {
            restore_passphrase?.fill('\u0000')
            restore_passphrase = null
            pending_restore_uri = null
            restore_preview = null
            show_restore_confirm_dialog = false
            return@rememberLauncherForActivityResult
        }
        pending_restore_uri = selected_uri
        val passphrase = restore_passphrase
        if (passphrase == null) {
            backup_error_message = app_context.getString(R.string.profile_backup_passphrase_required)
            return@rememberLauncherForActivityResult
        }
        app_scope.launch {
            backup_in_progress = true
            backup_status_message = null
            backup_error_message = null
            runCatching {
                withContext(Dispatchers.IO) {
                    backup_service.readBackupPreview(
                        uri = selected_uri,
                        passphrase = passphrase
                    )
                }
            }.onSuccess { preview ->
                restore_preview = preview
                show_restore_confirm_dialog = true
            }.onFailure { error ->
                restore_preview = null
                show_restore_confirm_dialog = false
                backup_error_message = app_context.getString(
                    R.string.profile_backup_restore_failed,
                    error.message ?: "unknown"
                )
            }
            backup_in_progress = false
        }
    }

    val trigger_backup_export: (CharArray, Boolean) -> Unit = { passphrase, include_history ->
        pending_export_passphrase?.fill('\u0000')
        pending_export_passphrase = passphrase
        pending_export_include_history = include_history
        backup_status_message = null
        backup_error_message = null
        val suggested_name = build_backup_file_name()
        export_backup_launcher.launch(suggested_name)
    }
    val trigger_backup_restore: () -> Unit = {
        backup_status_message = null
        backup_error_message = null
        restore_preview = null
        show_restore_confirm_dialog = false
        show_restore_passphrase_dialog = true
    }

    val cancel_window_error = int_range_error(cancel_window_input, PROFILE_MIN_CANCEL_WINDOW_SECONDS, PROFILE_MAX_CANCEL_WINDOW_SECONDS)
    val location_timeout_error = int_range_error(location_timeout_input, PROFILE_MIN_STEP_TIMEOUT_SECONDS, PROFILE_MAX_LOCATION_TIMEOUT_SECONDS)
    val sms_timeout_error = int_range_error(sms_timeout_input, PROFILE_MIN_STEP_TIMEOUT_SECONDS, PROFILE_MAX_SMS_TIMEOUT_SECONDS)
    val intent_timeout_error = int_range_error(intent_timeout_input, PROFILE_MIN_STEP_TIMEOUT_SECONDS, PROFILE_MAX_INTENT_TIMEOUT_SECONDS)
    val normalized_sms_recipients = normalize_sms_recipients(sms_recipients)
    val normalized_uninstall_packages = normalize_string_items(uninstall_packages)
    val configured_delete_targets = build_profile_delete_targets(delete_targets)
    val configured_shell_commands = build_profile_shell_commands(advanced_shell_commands)
    val sms_action_enabled = action_policy_rows.any { row ->
        row.action_id == ActionId.SEND_SMS && row.enabled
    }
    val uninstall_action_enabled = action_policy_rows.any { row ->
        row.action_id == ActionId.UNINSTALL_APPS && row.enabled
    }
    val delete_action_enabled = action_policy_rows.any { row ->
        row.action_id == ActionId.DELETE_PATHS && row.enabled
    }
    val shell_action_enabled = action_policy_rows.any { row ->
        row.action_id == ActionId.ADVANCED_SHELL_COMMANDS && row.enabled
    }
    val validation_result = build_profile_validation_result(
        cancel_window_error = cancel_window_error,
        location_timeout_error = location_timeout_error,
        sms_timeout_error = sms_timeout_error,
        intent_timeout_error = intent_timeout_error,
        sms_action_enabled = sms_action_enabled,
        uninstall_action_enabled = uninstall_action_enabled,
        delete_action_enabled = delete_action_enabled,
        shell_action_enabled = shell_action_enabled,
        normalized_sms_recipients = normalized_sms_recipients,
        normalized_uninstall_packages = normalized_uninstall_packages,
        configured_delete_targets = configured_delete_targets,
        configured_shell_commands = configured_shell_commands,
        uninstall_packages = uninstall_packages,
        delete_targets = delete_targets,
        advanced_shell_commands = advanced_shell_commands,
        telegram_bot_actions = telegram_bot_actions,
        message_app_bindings = message_app_bindings,
        intent_actions = intent_actions
    )
    val validation_issues = validation_result.issues
    val first_invalid_uninstall_package_index = validation_result.first_invalid_uninstall_package_index
    val first_invalid_delete_target_index = validation_result.first_invalid_delete_target_index
    val first_invalid_shell_timeout_index = validation_result.first_invalid_shell_timeout_index
    val first_invalid_shell_payload_index = validation_result.first_invalid_shell_payload_index
    val first_invalid_binding_package_index = validation_result.first_invalid_binding_package_index
    val first_invalid_binding_policy_order_index = validation_result.first_invalid_binding_policy_order_index
    val first_invalid_telegram_config_index = validation_result.first_invalid_telegram_config_index
    val first_invalid_telegram_timeout_index = validation_result.first_invalid_telegram_timeout_index
    val first_invalid_telegram_policy_order_index = validation_result.first_invalid_telegram_policy_order_index
    val first_invalid_intent_timeout_index = validation_result.first_invalid_intent_timeout_index
    val first_invalid_intent_policy_order_index = validation_result.first_invalid_intent_policy_order_index
    val can_save = validation_issues.isEmpty() && !is_saving
    val current_editor_signature = build_profile_editor_signature(
        sms_recipients = sms_recipients,
        notify_target_input = notify_target_input,
        message_template_input = message_template_input,
        cancel_window_input = cancel_window_input,
        location_timeout_input = location_timeout_input,
        opencellid_api_key_input = opencellid_api_key_input,
        sms_timeout_input = sms_timeout_input,
        intent_timeout_input = intent_timeout_input,
        action_policy_rows = action_policy_rows,
        telegram_bot_actions = telegram_bot_actions,
        message_app_bindings = message_app_bindings,
        intent_actions = intent_actions,
        uninstall_packages = uninstall_packages,
        delete_targets = delete_targets,
        advanced_shell_commands = advanced_shell_commands,
        removed_action_policy_map = removed_action_policy_map,
        removed_telegram_policy_keys = removed_telegram_policy_keys,
        removed_intent_policy_keys = removed_intent_policy_keys,
        destructive_actions_enabled = destructive_actions_enabled,
        triggers_enabled = triggers_enabled,
        self_uninstall_enabled = self_uninstall_enabled
    )
    var baseline_editor_signature by remember(profile) {
        mutableStateOf(build_profile_editor_signature_from_profile(profile))
    }
    val has_unsaved_changes = current_editor_signature != baseline_editor_signature
    val mark_profile_dirty: () -> Unit = {
        saved_successfully = false
        save_error_message = null
    }
    val ensure_action_policy_row: (ActionId) -> Unit = { action_id ->
        val action_policy_key = ActionPolicyKeys.for_action(action_id)
        val existing_index = action_policy_rows.indexOfFirst { it.policy_key == action_policy_key }
        if (existing_index >= 0) {
            val existing_row = action_policy_rows[existing_index]
            if (!existing_row.enabled || existing_row.required) {
                action_policy_rows = action_policy_rows.mapIndexed { index, row ->
                    if (index == existing_index) {
                        row.copy(enabled = true, required = false)
                    } else {
                        row
                    }
                }
            }
        } else {
            action_policy_rows = normalize_action_policy_row_orders(
                action_policy_rows + create_action_policy_row(action_id, action_policy_rows.size + 1)
            )
            removed_action_policy_map = removed_action_policy_map - action_policy_key
        }
    }
    val disable_action_policy_if_empty: (ActionId, Boolean) -> Unit = { action_id, has_items ->
        if (!has_items) {
            val action_policy_key = ActionPolicyKeys.for_action(action_id)
            val existing_index = action_policy_rows.indexOfFirst { it.policy_key == action_policy_key }
            if (existing_index >= 0) {
                action_policy_rows = action_policy_rows.mapIndexed { index, row ->
                    if (index == existing_index) {
                        row.copy(enabled = false, required = false)
                    } else {
                        row
                    }
                }
            }
        }
    }
    val action_rows = build_profile_action_rows(
        sms_recipients = sms_recipients,
        telegram_bot_actions = telegram_bot_actions,
        message_app_bindings = message_app_bindings,
        intent_actions = intent_actions,
        uninstall_packages = uninstall_packages,
        delete_targets = delete_targets,
        advanced_shell_commands = advanced_shell_commands,
        self_uninstall_enabled = self_uninstall_enabled
    )
    val has_actions = action_rows.isNotEmpty()
    val action_rows_by_group = action_rows.groupBy { it.group }
    val editing_action_row = action_rows.firstOrNull { it.row_id == editing_action_row_id }
    val group_header_item_indices = run {
        var item_index = 3 + if (show_reorder_helper) 1 else 0
        buildMap {
            ProfileActionGroup.entries.forEach { group ->
                put(group, item_index)
                item_index += 1
                if (group_expansion[group] == true) {
                    val group_size = action_rows_by_group[group].orEmpty().size
                    item_index += if (group_size == 0) 1 else group_size
                }
            }
        }
    }
    val settings_item_index = run {
        var item_index = 3 + if (show_reorder_helper) 1 else 0
        ProfileActionGroup.entries.forEach { group ->
            item_index += 1
            if (group_expansion[group] == true) {
                val group_size = action_rows_by_group[group].orEmpty().size
                item_index += if (group_size == 0) 1 else group_size
            }
        }
        item_index
    }
    val scroll_to_item_if_available: (Int) -> Unit = { target_index ->
        app_scope.launch {
            val total_items = profile_list_state.layoutInfo.totalItemsCount
            if (total_items <= 0) return@launch
            profile_list_state.animateScrollToItem(target_index.coerceIn(0, total_items - 1))
        }
    }
    val focus_validation_issue: (ProfileValidationIssueKey) -> Unit = { issue_key ->
        show_add_action_sheet = false
        when (issue_key) {
            ProfileValidationIssueKey.CANCEL_WINDOW,
            ProfileValidationIssueKey.LOCATION_TIMEOUT,
            ProfileValidationIssueKey.SMS_TIMEOUT,
            ProfileValidationIssueKey.INTENT_TIMEOUT -> {
                profile_settings_expanded = true
                timeouts_settings_expanded = true
                scroll_to_item_if_available(settings_item_index)
            }
            ProfileValidationIssueKey.SMS_RECIPIENTS_REQUIRED -> {
                group_expansion[ProfileActionGroup.COMMUNICATION] = true
                if (sms_recipients.isNotEmpty()) {
                    editing_action_row_id = "sms:0"
                } else {
                    scroll_to_item_if_available(group_header_item_indices[ProfileActionGroup.COMMUNICATION] ?: 0)
                }
            }
            ProfileValidationIssueKey.UNINSTALL_REQUIRED -> {
                group_expansion[ProfileActionGroup.DESTRUCTIVE] = true
                if (uninstall_packages.isNotEmpty()) {
                    editing_action_row_id = "uninstall:0"
                } else {
                    scroll_to_item_if_available(group_header_item_indices[ProfileActionGroup.DESTRUCTIVE] ?: 0)
                }
            }
            ProfileValidationIssueKey.UNINSTALL_INVALID -> {
                group_expansion[ProfileActionGroup.DESTRUCTIVE] = true
                if (first_invalid_uninstall_package_index >= 0) {
                    editing_action_row_id = "uninstall:$first_invalid_uninstall_package_index"
                }
            }
            ProfileValidationIssueKey.DELETE_REQUIRED -> {
                group_expansion[ProfileActionGroup.DESTRUCTIVE] = true
                if (delete_targets.isNotEmpty()) {
                    editing_action_row_id = "delete:0"
                } else {
                    scroll_to_item_if_available(group_header_item_indices[ProfileActionGroup.DESTRUCTIVE] ?: 0)
                }
            }
            ProfileValidationIssueKey.DELETE_INVALID -> {
                group_expansion[ProfileActionGroup.DESTRUCTIVE] = true
                if (first_invalid_delete_target_index >= 0) {
                    editing_action_row_id = "delete:$first_invalid_delete_target_index"
                }
            }
            ProfileValidationIssueKey.SHELL_REQUIRED -> {
                group_expansion[ProfileActionGroup.DESTRUCTIVE] = true
                if (advanced_shell_commands.isNotEmpty()) {
                    editing_action_row_id = "shell:${advanced_shell_commands.first().id}"
                } else {
                    scroll_to_item_if_available(group_header_item_indices[ProfileActionGroup.DESTRUCTIVE] ?: 0)
                }
            }
            ProfileValidationIssueKey.SHELL_TIMEOUT_INVALID -> {
                group_expansion[ProfileActionGroup.DESTRUCTIVE] = true
                val target_command = advanced_shell_commands.getOrNull(first_invalid_shell_timeout_index)
                if (target_command != null) {
                    editing_action_row_id = "shell:${target_command.id}"
                }
            }
            ProfileValidationIssueKey.SHELL_PAYLOAD_INVALID -> {
                group_expansion[ProfileActionGroup.DESTRUCTIVE] = true
                val target_command = advanced_shell_commands.getOrNull(first_invalid_shell_payload_index)
                if (target_command != null) {
                    editing_action_row_id = "shell:${target_command.id}"
                }
            }
            ProfileValidationIssueKey.BINDING_PACKAGE_INVALID -> {
                group_expansion[ProfileActionGroup.APP_INTENT] = true
                val target_binding = message_app_bindings.getOrNull(first_invalid_binding_package_index)
                if (target_binding != null) {
                    editing_action_row_id = "binding:${target_binding.binding_id}"
                }
            }
            ProfileValidationIssueKey.BINDING_ORDER_INVALID -> {
                group_expansion[ProfileActionGroup.APP_INTENT] = true
                val target_binding = message_app_bindings.getOrNull(first_invalid_binding_policy_order_index)
                if (target_binding != null) {
                    editing_action_row_id = "binding:${target_binding.binding_id}"
                }
            }
            ProfileValidationIssueKey.TELEGRAM_CONFIG_INVALID -> {
                group_expansion[ProfileActionGroup.COMMUNICATION] = true
                val target_action = telegram_bot_actions.getOrNull(first_invalid_telegram_config_index)
                if (target_action != null) {
                    editing_action_row_id = "telegram:${target_action.id}"
                }
            }
            ProfileValidationIssueKey.TELEGRAM_TIMEOUT_INVALID -> {
                group_expansion[ProfileActionGroup.COMMUNICATION] = true
                val target_action = telegram_bot_actions.getOrNull(first_invalid_telegram_timeout_index)
                if (target_action != null) {
                    editing_action_row_id = "telegram:${target_action.id}"
                }
            }
            ProfileValidationIssueKey.TELEGRAM_ORDER_INVALID -> {
                group_expansion[ProfileActionGroup.COMMUNICATION] = true
                val target_action = telegram_bot_actions.getOrNull(first_invalid_telegram_policy_order_index)
                if (target_action != null) {
                    editing_action_row_id = "telegram:${target_action.id}"
                }
            }
            ProfileValidationIssueKey.INTENT_STEP_TIMEOUT_INVALID -> {
                group_expansion[ProfileActionGroup.APP_INTENT] = true
                val target_intent = intent_actions.getOrNull(first_invalid_intent_timeout_index)
                if (target_intent != null) {
                    editing_action_row_id = "intent:${target_intent.id}"
                }
            }
            ProfileValidationIssueKey.INTENT_ORDER_INVALID -> {
                group_expansion[ProfileActionGroup.APP_INTENT] = true
                val target_intent = intent_actions.getOrNull(first_invalid_intent_policy_order_index)
                if (target_intent != null) {
                    editing_action_row_id = "intent:${target_intent.id}"
                }
            }
        }
    }
    val move_action_row: (ProfileActionRow, Int) -> Unit = { row, target_index ->
        when (row) {
            is SmsRecipientActionRow -> {
                val bounded_index = target_index.coerceIn(0, sms_recipients.lastIndex)
                sms_recipients = move_string_item(sms_recipients, row.item_index, bounded_index)
                sms_contact_picker_error_res_id = null
            }
            is TelegramBotActionRow -> {
                val bounded_index = target_index.coerceIn(0, telegram_bot_actions.lastIndex)
                telegram_bot_actions = move_telegram_bot_action(telegram_bot_actions, row.item_index, bounded_index)
            }
            is MessageBindingActionRow -> {
                val bounded_index = target_index.coerceIn(0, message_app_bindings.lastIndex)
                message_app_bindings = move_message_binding(message_app_bindings, row.item_index, bounded_index)
            }
            is IntentActionRow -> {
                val bounded_index = target_index.coerceIn(0, intent_actions.lastIndex)
                intent_actions = move_intent_action(intent_actions, row.item_index, bounded_index)
            }
            is UninstallPackageActionRow -> {
                val bounded_index = target_index.coerceIn(0, uninstall_packages.lastIndex)
                uninstall_packages = move_string_item(uninstall_packages, row.item_index, bounded_index)
            }
            is DeleteTargetActionRow -> {
                val bounded_index = target_index.coerceIn(0, delete_targets.lastIndex)
                delete_targets = move_item(delete_targets, row.item_index, bounded_index)
            }
            is ShellCommandActionRow -> {
                val bounded_index = target_index.coerceIn(0, advanced_shell_commands.lastIndex)
                advanced_shell_commands = move_item(advanced_shell_commands, row.item_index, bounded_index)
            }
            is SelfUninstallActionRow -> Unit
        }
        show_reorder_helper = true
        mark_profile_dirty()
    }
    val remove_action_row: (ProfileActionRow) -> Unit = { row ->
        when (row) {
            is SmsRecipientActionRow -> {
                val updated_sms_recipients = sms_recipients.filterIndexed { index, _ -> index != row.item_index }
                sms_recipients = updated_sms_recipients
                sms_contact_picker_error_res_id = null
                disable_action_policy_if_empty(ActionId.SEND_SMS, updated_sms_recipients.isNotEmpty())
            }
            is TelegramBotActionRow -> {
                val target_action = telegram_bot_actions.getOrNull(row.item_index)
                if (target_action != null) {
                    removed_telegram_policy_keys = removed_telegram_policy_keys + ActionPolicyKeys.for_telegram_bot(target_action.id)
                }
                telegram_bot_actions = normalize_telegram_bot_action_orders(
                    telegram_bot_actions.filterIndexed { index, _ -> index != row.item_index }
                )
            }
            is MessageBindingActionRow -> {
                message_app_bindings = normalize_message_binding_orders(
                    message_app_bindings.filterIndexed { index, _ -> index != row.item_index }
                )
            }
            is IntentActionRow -> {
                val target_intent = intent_actions.getOrNull(row.item_index)
                if (target_intent != null) {
                    removed_intent_policy_keys = removed_intent_policy_keys + ActionPolicyKeys.for_intent(target_intent.id)
                }
                intent_actions = normalize_intent_action_orders(
                    intent_actions.filterIndexed { index, _ -> index != row.item_index }
                )
            }
            is UninstallPackageActionRow -> {
                val updated_uninstall_packages = uninstall_packages.filterIndexed { index, _ -> index != row.item_index }
                uninstall_packages = updated_uninstall_packages
                disable_action_policy_if_empty(ActionId.UNINSTALL_APPS, updated_uninstall_packages.isNotEmpty())
            }
            is DeleteTargetActionRow -> {
                val updated_delete_targets = delete_targets.filterIndexed { index, _ -> index != row.item_index }
                delete_targets = updated_delete_targets
                disable_action_policy_if_empty(ActionId.DELETE_PATHS, updated_delete_targets.isNotEmpty())
            }
            is ShellCommandActionRow -> {
                val updated_shell_commands = advanced_shell_commands.filterIndexed { index, _ -> index != row.item_index }
                advanced_shell_commands = updated_shell_commands
                disable_action_policy_if_empty(ActionId.ADVANCED_SHELL_COMMANDS, updated_shell_commands.isNotEmpty())
            }
            is SelfUninstallActionRow -> {
                self_uninstall_enabled = false
            }
        }
        if (editing_action_row_id == row.row_id) editing_action_row_id = null
        mark_profile_dirty()
    }
    val add_action_type: (ProfileActionRowType) -> Unit = { row_type ->
        when (row_type) {
            ProfileActionRowType.SMS_RECIPIENT -> {
                ensure_action_policy_row(ActionId.SEND_SMS)
                sms_recipients = sms_recipients + ""
                editing_action_row_id = "sms:${sms_recipients.lastIndex}"
            }
            ProfileActionRowType.TELEGRAM_BOT_ACTION -> {
                telegram_bot_actions = telegram_bot_actions + create_empty_telegram_bot_action(
                    default_execution_order = telegram_bot_actions.size + 1
                )
                editing_action_row_id = "telegram:${telegram_bot_actions.last().id}"
            }
            ProfileActionRowType.MESSAGE_BINDING -> {
                message_app_bindings = message_app_bindings + create_empty_message_binding(
                    default_execution_order = message_app_bindings.size + 1
                )
                editing_action_row_id = "binding:${message_app_bindings.last().binding_id}"
            }
            ProfileActionRowType.INTENT_ACTION -> {
                intent_actions = intent_actions + create_empty_intent_action(intent_actions.size + 1)
                editing_action_row_id = "intent:${intent_actions.last().id}"
            }
            ProfileActionRowType.UNINSTALL_PACKAGE -> {
                ensure_action_policy_row(ActionId.UNINSTALL_APPS)
                uninstall_packages = uninstall_packages + ""
                editing_action_row_id = "uninstall:${uninstall_packages.lastIndex}"
            }
            ProfileActionRowType.DELETE_TARGET -> {
                ensure_action_policy_row(ActionId.DELETE_PATHS)
                delete_targets = delete_targets + create_empty_delete_target()
                editing_action_row_id = "delete:${delete_targets.lastIndex}"
            }
            ProfileActionRowType.SHELL_COMMAND -> {
                ensure_action_policy_row(ActionId.ADVANCED_SHELL_COMMANDS)
                advanced_shell_commands = advanced_shell_commands + create_empty_shell_command()
                editing_action_row_id = "shell:${advanced_shell_commands.last().id}"
            }
            ProfileActionRowType.SELF_UNINSTALL -> {
                ensure_action_policy_row(ActionId.SELF_UNINSTALL)
                self_uninstall_enabled = true
                editing_action_row_id = "self_uninstall"
            }
        }
        recent_add_action_types = (listOf(row_type) + recent_add_action_types)
            .distinct()
            .take(4)
        add_action_group = null
        show_add_action_sheet = false
        mark_profile_dirty()
    }
    val action_rule_for: (ActionId) -> EditableActionPolicyRow = { action_id ->
        action_policy_rows.firstOrNull { it.action_id == action_id }
            ?: create_action_policy_row(action_id, action_policy_rows.size + 1)
    }
    val update_action_rule: (ActionId, EditableActionPolicyRow) -> Unit = { action_id, updated_row ->
        val existing_index = action_policy_rows.indexOfFirst { it.action_id == action_id }
        action_policy_rows = if (existing_index >= 0) {
            action_policy_rows.mapIndexed { index, row ->
                if (index == existing_index) updated_row.copy(policy_key = ActionPolicyKeys.for_action(action_id)) else row
            }
        } else {
            normalize_action_policy_row_orders(
                action_policy_rows + updated_row.copy(
                    action_id = action_id,
                    policy_key = ActionPolicyKeys.for_action(action_id),
                    execution_order = action_policy_rows.size + 1
                )
            )
        }
        mark_profile_dirty()
    }
    val discard_changes: () -> Unit = {
        sms_recipients = profile.sms_recipients
        notify_target_input = profile.notify_target
        message_template_input = profile.message_template
        cancel_window_input = profile.cancel_window_seconds.toString()
        location_timeout_input = profile.location_timeout_seconds.toString()
        opencellid_api_key_input = profile.opencellid_api_key
        sms_timeout_input = profile.sms_timeout_seconds.toString()
        intent_timeout_input = profile.intent_timeout_seconds.toString()
        action_policy_rows = extract_editable_action_policy_rows(profile.action_policies)
        removed_action_policy_map = emptyMap()
        message_app_bindings = profile.action_bindings
            .filter { it.action_id == ActionId.NOTIFY_MESSAGE_APP }
            .mapIndexed { index, binding ->
                to_editable_message_binding(
                    binding = binding,
                    action_policies = profile.action_policies,
                    default_execution_order = index + 1
                )
            }
        telegram_bot_actions = profile.telegram_bot_actions.mapIndexed { index, telegram_action ->
            to_editable_telegram_bot_action(
                telegram_bot_action = telegram_action,
                action_policies = profile.action_policies,
                default_execution_order = index + 1
            )
        }
        intent_actions = profile.intent_actions.mapIndexed { index, intent_action ->
            to_editable_intent_action(
                intent_action = intent_action,
                action_policies = profile.action_policies,
                default_execution_order = index + 1
            )
        }
        uninstall_packages = profile.uninstall_allowlist
        delete_targets = profile.delete_allowlist.map(::to_editable_delete_target)
        advanced_shell_commands = profile.advanced_shell_commands.map(::to_editable_shell_command)
        removed_telegram_policy_keys = emptySet()
        removed_intent_policy_keys = emptySet()
        destructive_actions_enabled = profile.destructive_actions_enabled
        triggers_enabled = profile.triggers_enabled
        self_uninstall_enabled = profile.self_uninstall_enabled
        sms_contact_picker_error_res_id = null
        show_add_action_sheet = false
        add_action_group = null
        editing_action_row_id = null
        show_reorder_helper = false
        profile_settings_expanded = false
        message_settings_expanded = false
        location_lookup_settings_expanded = false
        timeouts_settings_expanded = false
        safety_settings_expanded = false
        message_binding_picker_index = null
        uninstall_package_picker_index = null
        save_error_message = null
        saved_successfully = false
        backup_status_message = null
        backup_error_message = null
        show_export_backup_dialog = false
        show_restore_passphrase_dialog = false
        show_restore_confirm_dialog = false
        pending_export_passphrase?.fill('\u0000')
        pending_export_passphrase = null
        pending_export_include_history = false
        pending_restore_uri = null
        restore_passphrase?.fill('\u0000')
        restore_passphrase = null
        restore_preview = null
        baseline_editor_signature = build_profile_editor_signature_from_profile(profile)
    }
    val persist_profile: (EmergencyProfile) -> Unit = { updated_profile ->
        app_scope.launch {
            is_saving = true
            saved_successfully = false
            save_error_message = null
            runCatching {
                profile_store.write_profile(updated_profile)
            }.onSuccess {
                saved_successfully = true
                save_error_message = null
                baseline_editor_signature = build_profile_editor_signature_from_profile(updated_profile)
            }.onFailure { error ->
                saved_successfully = false
                save_error_message = error.message ?: "unknown_error"
            }
            is_saving = false
        }
    }
    val save_changes: () -> Unit = save_changes@{
        if (is_saving || validation_issues.isNotEmpty()) return@save_changes
        val updated_profile = profile.copy(
            sms_recipients = normalized_sms_recipients,
            notify_target = notify_target_input.trim(),
            message_template = message_template_input,
            cancel_window_seconds = parse_int_or_fallback(
                value = cancel_window_input,
                fallback = profile.cancel_window_seconds,
                min_value = PROFILE_MIN_CANCEL_WINDOW_SECONDS,
                max_value = PROFILE_MAX_CANCEL_WINDOW_SECONDS
            ),
            location_timeout_seconds = parse_int_or_fallback(
                value = location_timeout_input,
                fallback = profile.location_timeout_seconds,
                min_value = PROFILE_MIN_STEP_TIMEOUT_SECONDS,
                max_value = PROFILE_MAX_LOCATION_TIMEOUT_SECONDS
            ),
            opencellid_api_key = opencellid_api_key_input.trim(),
            sms_timeout_seconds = parse_int_or_fallback(
                value = sms_timeout_input,
                fallback = profile.sms_timeout_seconds,
                min_value = PROFILE_MIN_STEP_TIMEOUT_SECONDS,
                max_value = PROFILE_MAX_SMS_TIMEOUT_SECONDS
            ),
            intent_timeout_seconds = parse_int_or_fallback(
                value = intent_timeout_input,
                fallback = profile.intent_timeout_seconds,
                min_value = PROFILE_MIN_STEP_TIMEOUT_SECONDS,
                max_value = PROFILE_MAX_INTENT_TIMEOUT_SECONDS
            ),
            uninstall_allowlist = normalized_uninstall_packages,
            delete_allowlist = configured_delete_targets,
            advanced_shell_commands = configured_shell_commands,
            action_bindings = build_profile_action_bindings(
                existing_bindings = profile.action_bindings,
                message_app_bindings = message_app_bindings
            ),
            telegram_bot_actions = build_profile_telegram_bot_actions(telegram_bot_actions),
            intent_actions = build_profile_intent_actions(intent_actions),
            action_policies = build_profile_action_policies(
                existing_policies = profile.action_policies,
                message_app_bindings = message_app_bindings,
                action_policy_rows = action_policy_rows,
                removed_action_policy_map = removed_action_policy_map,
                telegram_bot_actions = telegram_bot_actions,
                removed_telegram_policy_keys = removed_telegram_policy_keys,
                intent_actions = intent_actions,
                removed_intent_policy_keys = removed_intent_policy_keys
            ),
            destructive_actions_enabled = destructive_actions_enabled,
            triggers_enabled = triggers_enabled,
            self_uninstall_enabled = self_uninstall_enabled
        )
        if (has_sensitive_profile_changes(profile, updated_profile)) {
            pending_sensitive_save = { persist_profile(updated_profile) }
            if (!launch_sensitive_auth_for_save()) {
                pending_sensitive_save = null
            }
            return@save_changes
        }
        persist_profile(updated_profile)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ProfileContentList(
            modifier = Modifier.weight(1f),
            profile_list_state = profile_list_state,
            action_policy_rows = action_policy_rows,
            telegram_bot_actions = telegram_bot_actions,
            message_app_bindings = message_app_bindings,
            intent_actions = intent_actions,
            show_reorder_helper = show_reorder_helper,
            group_expansion = group_expansion,
            on_toggle_group = { group ->
                group_expansion[group] = !(group_expansion[group] ?: true)
            },
            action_rows_by_group = action_rows_by_group,
            sms_recipients = sms_recipients,
            uninstall_packages = uninstall_packages,
            delete_targets = delete_targets,
            advanced_shell_commands = advanced_shell_commands,
            has_actions = has_actions,
            on_add_first_action = {
                add_action_group = null
                show_add_action_sheet = true
            },
            on_edit_action_row = { row_id -> editing_action_row_id = row_id },
            on_move_action_row = move_action_row,
            on_remove_action_row = remove_action_row,
            profile_settings_expanded = profile_settings_expanded,
            on_profile_settings_expanded_change = { expanded -> profile_settings_expanded = expanded },
            message_settings_expanded = message_settings_expanded,
            on_message_settings_expanded_change = { expanded -> message_settings_expanded = expanded },
            location_lookup_settings_expanded = location_lookup_settings_expanded,
            on_location_lookup_settings_expanded_change = { expanded -> location_lookup_settings_expanded = expanded },
            timeouts_settings_expanded = timeouts_settings_expanded,
            on_timeouts_settings_expanded_change = { expanded -> timeouts_settings_expanded = expanded },
            safety_settings_expanded = safety_settings_expanded,
            on_safety_settings_expanded_change = { expanded -> safety_settings_expanded = expanded },
            notify_target_input = notify_target_input,
            on_notify_target_change = {
                notify_target_input = it
                mark_profile_dirty()
            },
            message_template_input = message_template_input,
            on_message_template_change = {
                message_template_input = it
                mark_profile_dirty()
            },
            cancel_window_input = cancel_window_input,
            cancel_window_error = cancel_window_error,
            on_cancel_window_change = {
                cancel_window_input = it
                mark_profile_dirty()
            },
            location_timeout_input = location_timeout_input,
            location_timeout_error = location_timeout_error,
            on_location_timeout_change = {
                location_timeout_input = it
                mark_profile_dirty()
            },
            opencellid_api_key_input = opencellid_api_key_input,
            on_opencellid_api_key_change = {
                opencellid_api_key_input = it
                mark_profile_dirty()
            },
            sms_timeout_input = sms_timeout_input,
            sms_timeout_error = sms_timeout_error,
            on_sms_timeout_change = {
                sms_timeout_input = it
                mark_profile_dirty()
            },
            intent_timeout_input = intent_timeout_input,
            intent_timeout_error = intent_timeout_error,
            on_intent_timeout_change = {
                intent_timeout_input = it
                mark_profile_dirty()
            },
            destructive_actions_enabled = destructive_actions_enabled,
            on_destructive_actions_toggle = {
                if (it && !destructive_actions_enabled) {
                    show_destructive_enable_confirm = true
                } else {
                    destructive_actions_enabled = it
                }
                mark_profile_dirty()
            },
            triggers_enabled = triggers_enabled,
            on_triggers_toggle = {
                triggers_enabled = it
                mark_profile_dirty()
            },
            self_uninstall_enabled = self_uninstall_enabled,
            on_self_uninstall_toggle = {
                if (it && !self_uninstall_enabled) {
                    show_self_uninstall_enable_confirm = true
                } else {
                    self_uninstall_enabled = it
                }
                mark_profile_dirty()
            },
            backup_in_progress = backup_in_progress,
            backup_status_message = backup_status_message,
            backup_error_message = backup_error_message,
            on_export_backup = { show_export_backup_dialog = true },
            on_restore_backup = trigger_backup_restore,
            validation_issues = validation_issues,
            on_focus_validation_issue = focus_validation_issue
        )
        ProfileSaveBar(
            has_unsaved_changes = has_unsaved_changes,
            is_saving = is_saving,
            can_save = can_save,
            saved_successfully = saved_successfully,
            save_error_message = save_error_message,
            on_discard = discard_changes,
            on_save = save_changes
        )
    }

    ProfileAddActionSheet(
        show = show_add_action_sheet,
        selected_group = add_action_group,
        recent_action_types = recent_add_action_types,
        self_uninstall_enabled = self_uninstall_enabled,
        on_dismiss = {
            show_add_action_sheet = false
            add_action_group = null
        },
        on_select_group = { group ->
            add_action_group = group
        },
        on_select_action_type = { row_type ->
            add_action_type(row_type)
        },
        on_back_to_groups = {
            add_action_group = null
        }
    )

    ProfileActionEditorSheet(
        app_context = app_context,
        action_provider_registry = action_provider_registry,
        editing_action_row = editing_action_row,
        sms_recipients = sms_recipients,
        sms_contact_picker_error_res_id = sms_contact_picker_error_res_id,
        telegram_bot_actions = telegram_bot_actions,
        message_app_bindings = message_app_bindings,
        intent_actions = intent_actions,
        uninstall_packages = uninstall_packages,
        delete_targets = delete_targets,
        advanced_shell_commands = advanced_shell_commands,
        self_uninstall_enabled = self_uninstall_enabled,
        on_dismiss = { editing_action_row_id = null },
        on_mark_profile_dirty = mark_profile_dirty,
        on_update_sms_recipients = { sms_recipients = it },
        on_update_sms_contact_picker_error = { sms_contact_picker_error_res_id = it },
        on_pick_sms_contact = { index ->
            sms_contact_picker_index = index
            sms_contact_picker_launcher.launch(null)
        },
        on_update_telegram_bot_actions = { telegram_bot_actions = it },
        on_update_message_app_bindings = { message_app_bindings = it },
        on_open_message_binding_picker = { index -> message_binding_picker_index = index },
        on_update_intent_actions = { intent_actions = it },
        on_update_uninstall_packages = { uninstall_packages = it },
        on_open_uninstall_package_picker = { index -> uninstall_package_picker_index = index },
        on_update_delete_targets = { delete_targets = it },
        on_update_advanced_shell_commands = { advanced_shell_commands = it },
        on_toggle_self_uninstall_enabled = { enabled ->
            if (enabled && !self_uninstall_enabled) {
                show_self_uninstall_enable_confirm = true
            } else {
                self_uninstall_enabled = enabled
            }
        },
        action_rule_for = action_rule_for,
        on_update_action_rule = update_action_rule
    )
    MessageBindingPickerDialog(
        picker_index = message_binding_picker_index,
        bindings = message_app_bindings,
        options = message_binding_picker_options,
        on_update_bindings = { message_app_bindings = it },
        on_dismiss = { message_binding_picker_index = null },
        on_mark_profile_dirty = mark_profile_dirty
    )

    UninstallPackagePickerDialog(
        picker_index = uninstall_package_picker_index,
        uninstall_packages = uninstall_packages,
        options = uninstall_package_picker_options,
        on_update_packages = { uninstall_packages = it },
        on_dismiss = { uninstall_package_picker_index = null },
        on_mark_profile_dirty = mark_profile_dirty
    )

    DestructiveEnableConfirmDialog(
        show = show_destructive_enable_confirm,
        on_confirm = {
            destructive_actions_enabled = true
            saved_successfully = false
            save_error_message = null
            show_destructive_enable_confirm = false
        },
        on_dismiss = { show_destructive_enable_confirm = false }
    )

    SelfUninstallEnableConfirmDialog(
        show = show_self_uninstall_enable_confirm,
        on_confirm = {
            self_uninstall_enabled = true
            saved_successfully = false
            save_error_message = null
            show_self_uninstall_enable_confirm = false
        },
        on_dismiss = { show_self_uninstall_enable_confirm = false }
    )

    ExportBackupDialog(
        show = show_export_backup_dialog,
        on_dismiss = { show_export_backup_dialog = false },
        on_confirm_export = { passphrase, include_history ->
            show_export_backup_dialog = false
            trigger_backup_export(passphrase, include_history)
        }
    )

    RestoreBackupPassphraseDialog(
        show = show_restore_passphrase_dialog,
        on_dismiss = {
            show_restore_passphrase_dialog = false
            restore_passphrase?.fill('\u0000')
            restore_passphrase = null
        },
        on_confirm_passphrase = { passphrase ->
            show_restore_passphrase_dialog = false
            restore_passphrase?.fill('\u0000')
            restore_passphrase = passphrase
            restore_backup_picker_launcher.launch(arrayOf("*/*"))
        }
    )

    RestoreBackupConfirmDialog(
        show = show_restore_confirm_dialog,
        preview = restore_preview,
        in_progress = backup_in_progress,
        on_dismiss = {
            if (backup_in_progress) return@RestoreBackupConfirmDialog
            show_restore_confirm_dialog = false
            restore_preview = null
            pending_restore_uri = null
            restore_passphrase?.fill('\u0000')
            restore_passphrase = null
        },
        on_confirm_restore = {
            if (backup_in_progress) return@RestoreBackupConfirmDialog
            val backup_uri = pending_restore_uri
            val passphrase = restore_passphrase
            if (backup_uri == null || passphrase == null) {
                show_restore_confirm_dialog = false
                restore_preview = null
                pending_restore_uri = null
                restore_passphrase?.fill('\u0000')
                restore_passphrase = null
                backup_error_message = app_context.getString(R.string.profile_backup_restore_missing_state)
                return@RestoreBackupConfirmDialog
            }
            show_restore_confirm_dialog = false
            backup_in_progress = true
            backup_status_message = null
            backup_error_message = null
            app_scope.launch {
                val restore_result = withContext(Dispatchers.IO) {
                    backup_service.restoreEncryptedBackup(
                        uri = backup_uri,
                        passphrase = passphrase
                    )
                }
                if (restore_result.success) {
                    backup_status_message = app_context.getString(
                        R.string.profile_backup_restore_success,
                        restore_result.restored_history_count
                    )
                    backup_error_message = null
                    on_backup_restore_complete()
                } else {
                    backup_status_message = null
                    backup_error_message = app_context.getString(
                        R.string.profile_backup_restore_failed,
                        restore_result.error_message ?: "unknown"
                    )
                }
                restore_preview = null
                pending_restore_uri = null
                passphrase.fill('\u0000')
                restore_passphrase = null
                backup_in_progress = false
            }
        }
    )
}

private fun build_backup_file_name(now: Instant = Instant.now()): String {
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())
    return "mafza-${formatter.format(now)}$BACKUP_FILE_EXTENSION"
}
