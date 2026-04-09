package com.yshalsager.mafza.preflight

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.yshalsager.mafza.emergency.providers.ActionProviderRegistry
import com.yshalsager.mafza.core.contracts.ActionBinding
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ActionPolicy
import com.yshalsager.mafza.core.contracts.ActionPolicyKeys
import com.yshalsager.mafza.core.contracts.DeleteTarget
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.IntentActionSpec
import com.yshalsager.mafza.core.contracts.StepBranch
import com.yshalsager.mafza.shizuku.ShizukuPermissionState
import java.io.File
import java.nio.file.Files

data class PreflightReport(
    val live_ready: Boolean,
    val dry_run_ready: Boolean,
    val live_blocking_issues: List<String>,
    val dry_run_blocking_issues: List<String>,
    val warnings: List<String>
)

class PreflightValidator(
    private val app_context: Context,
    private val action_provider_registry: ActionProviderRegistry,
    private val has_location_permission_checker: (() -> Boolean)? = null,
    private val has_sms_permission_checker: (() -> Boolean)? = null,
    private val has_phone_state_permission_checker: (() -> Boolean)? = null,
    private val binding_available_checker: ((ActionBinding) -> Boolean)? = null,
    private val intent_resolver: ((IntentActionSpec) -> Boolean)? = null
) {
    fun validate(
        profile: EmergencyProfile,
        shizuku_permission_state: ShizukuPermissionState
    ): PreflightReport {
        val live_blocking_issues = mutableListOf<String>()
        val dry_run_blocking_issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val recipients = profile.sms_recipients.map(String::trim).filter { it.isNotEmpty() }
        val sms_required = is_action_required(profile.action_policies, ActionId.SEND_SMS)
        val sms_enabled = is_action_enabled(profile.action_policies, ActionId.SEND_SMS)
        if (sms_required && recipients.isEmpty()) {
            live_blocking_issues += "at_least_one_sms_recipient_required"
            dry_run_blocking_issues += "at_least_one_sms_recipient_required"
        }

        if (!has_unique_execution_order_by_branch(profile.action_policies)) {
            live_blocking_issues += "action_policy_duplicate_execution_order"
            dry_run_blocking_issues += "action_policy_duplicate_execution_order"
        }
        if (!action_policy_keys_valid(profile.action_policies)) {
            live_blocking_issues += "action_policy_invalid_or_duplicate_policy_key"
            dry_run_blocking_issues += "action_policy_invalid_or_duplicate_policy_key"
        }

        if (!uninstall_allowlist_valid(profile.uninstall_allowlist)) {
            live_blocking_issues += "invalid_uninstall_allowlist"
            dry_run_blocking_issues += "invalid_uninstall_allowlist"
        }
        if (!delete_allowlist_valid(profile.delete_allowlist)) {
            live_blocking_issues += "invalid_delete_allowlist"
            dry_run_blocking_issues += "invalid_delete_allowlist"
        }
        if (!advanced_shell_commands_valid(profile)) {
            live_blocking_issues += "invalid_advanced_shell_commands"
            dry_run_blocking_issues += "invalid_advanced_shell_commands"
        }

        if (!has_location_permission()) {
            live_blocking_issues += "missing_location_permission"
        }
        if (!has_phone_state_permission()) {
            live_blocking_issues += "missing_read_phone_state_permission"
        }
        if (sms_enabled && recipients.isNotEmpty() && !has_sms_permission()) {
            live_blocking_issues += "missing_send_sms_permission"
        }
        val shizuku_required_for_live = requires_shizuku_for_live_destructive_actions(profile)
        if (shizuku_required_for_live && (!shizuku_permission_state.is_running || !shizuku_permission_state.is_permission_granted)) {
            live_blocking_issues += "shizuku_permission_required_for_destructive_actions"
        }

        validate_action_bindings(profile, live_blocking_issues, dry_run_blocking_issues, warnings)
        validate_intent_actions(profile, live_blocking_issues, dry_run_blocking_issues, warnings)

        return PreflightReport(
            live_ready = live_blocking_issues.isEmpty(),
            dry_run_ready = dry_run_blocking_issues.isEmpty(),
            live_blocking_issues = live_blocking_issues.distinct(),
            dry_run_blocking_issues = dry_run_blocking_issues.distinct(),
            warnings = warnings.distinct()
        )
    }

    private fun validate_action_bindings(
        profile: EmergencyProfile,
        live_blocking_issues: MutableList<String>,
        dry_run_blocking_issues: MutableList<String>,
        warnings: MutableList<String>
    ) {
        val enabled_bindings = profile.action_bindings.filter { binding ->
            binding.enabled && is_action_enabled(
                action_policies = profile.action_policies,
                action_id = binding.action_id
            )
        }
        val has_missing_binding_id = enabled_bindings.any { it.binding_id.trim().isEmpty() }
        if (has_missing_binding_id) {
            live_blocking_issues += "action_binding_missing_binding_id"
            dry_run_blocking_issues += "action_binding_missing_binding_id"
        }
        val has_duplicate_binding_id = enabled_bindings
            .map { it.action_id to it.binding_id.trim().lowercase() }
            .filter { (_, binding_id) -> binding_id.isNotEmpty() }
            .groupBy { it }
            .values
            .any { it.size > 1 }
        if (has_duplicate_binding_id) {
            live_blocking_issues += "action_binding_duplicate_binding_id"
            dry_run_blocking_issues += "action_binding_duplicate_binding_id"
        }

        enabled_bindings.forEach { binding ->
            if (binding.binding_id.trim().isEmpty()) return@forEach
            val binding_policy_key = ActionPolicyKeys.for_binding(binding)
            if (!is_action_enabled(profile.action_policies, binding.action_id, binding_policy_key)) return@forEach

            if (!PACKAGE_NAME_REGEX.matches(binding.package_name)) {
                live_blocking_issues += "invalid_binding_package_${binding.action_id.name.lowercase()}"
                dry_run_blocking_issues += "invalid_binding_package_${binding.action_id.name.lowercase()}"
                return@forEach
            }

            val binding_available = is_binding_available(binding)
            if (binding_available) return@forEach

            if (is_action_required(
                    action_policies = profile.action_policies,
                    action_id = binding.action_id,
                    policy_key = binding_policy_key
                )
            ) {
                live_blocking_issues += "required_binding_unavailable_${binding.action_id.name.lowercase()}"
            } else {
                warnings += "optional_binding_unavailable_${binding.action_id.name.lowercase()}"
            }
        }
    }

    private fun validate_intent_actions(
        profile: EmergencyProfile,
        live_blocking_issues: MutableList<String>,
        dry_run_blocking_issues: MutableList<String>,
        warnings: MutableList<String>
    ) {
        val enabled_intents = profile.intent_actions.filter { intent_action ->
            intent_action.enabled && is_action_enabled(
                action_policies = profile.action_policies,
                action_id = ActionId.LAUNCH_INTENT,
                policy_key = ActionPolicyKeys.for_intent(intent_action.id)
            )
        }
        enabled_intents.forEach { intent_action ->
            if (intent_action.id.isBlank()) {
                live_blocking_issues += "intent_action_missing_id"
                dry_run_blocking_issues += "intent_action_missing_id"
                return@forEach
            }
            if (!intent_action_structurally_valid(intent_action)) {
                live_blocking_issues += "invalid_intent_action_${intent_action.id}"
                dry_run_blocking_issues += "invalid_intent_action_${intent_action.id}"
                return@forEach
            }

            val resolvable = is_intent_resolvable(intent_action)
            if (resolvable) return@forEach

            if (is_action_required(
                    action_policies = profile.action_policies,
                    action_id = ActionId.LAUNCH_INTENT,
                    policy_key = ActionPolicyKeys.for_intent(intent_action.id)
                )
            ) {
                live_blocking_issues += "required_intent_unresolvable_${intent_action.id}"
            } else {
                warnings += "optional_intent_unresolvable_${intent_action.id}"
            }
        }
    }

    private fun has_location_permission(): Boolean {
        val override_checker = has_location_permission_checker
        if (override_checker != null) return override_checker()

        val has_fine = ContextCompat.checkSelfPermission(app_context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val has_coarse = ContextCompat.checkSelfPermission(app_context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return has_fine || has_coarse
    }

    private fun has_sms_permission(): Boolean {
        val override_checker = has_sms_permission_checker
        if (override_checker != null) return override_checker()

        return ContextCompat.checkSelfPermission(app_context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun has_phone_state_permission(): Boolean {
        val override_checker = has_phone_state_permission_checker
        if (override_checker != null) return override_checker()

        return ContextCompat.checkSelfPermission(app_context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }

    private fun is_binding_available(binding: ActionBinding): Boolean {
        val override_checker = binding_available_checker
        if (override_checker != null) return override_checker(binding)

        val provider = action_provider_registry.provider_for(binding.action_id) ?: return false
        return provider.isAvailable(binding)
    }

    private fun is_intent_resolvable(intent_action_spec: IntentActionSpec): Boolean {
        val override_resolver = intent_resolver
        if (override_resolver != null) return override_resolver(intent_action_spec)

        val intent = Intent(intent_action_spec.intent_action?.ifBlank { null } ?: Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val parsed_uri = intent_action_spec.data_uri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (parsed_uri != null && !intent_action_spec.mime_type.isNullOrBlank()) {
            intent.setDataAndType(parsed_uri, intent_action_spec.mime_type)
        } else if (parsed_uri != null) {
            intent.data = parsed_uri
        } else if (!intent_action_spec.mime_type.isNullOrBlank()) {
            intent.type = intent_action_spec.mime_type
        }
        intent_action_spec.categories.forEach { category ->
            if (category.isNotBlank()) intent.addCategory(category)
        }
        val package_name = intent_action_spec.package_name?.ifBlank { null }
        val activity_name = intent_action_spec.activity_name?.ifBlank { null }
        if (package_name != null) intent.`package` = package_name
        if (package_name != null && activity_name != null) {
            intent.component = ComponentName(package_name, activity_name)
        }
        return intent.resolveActivity(app_context.packageManager) != null
    }

    private fun intent_action_structurally_valid(intent_action_spec: IntentActionSpec): Boolean {
        val data_uri = intent_action_spec.data_uri?.trim().orEmpty()
        if (data_uri.isNotEmpty() && runCatching { Uri.parse(data_uri) }.getOrNull() == null) return false
        return true
    }

    private fun has_unique_execution_order_by_branch(action_policies: List<ActionPolicy>): Boolean {
        val enabled_policies = action_policies.filter { it.enabled }
        val duplicates_exist = enabled_policies
            .groupBy { action_policy -> action_id_branch(action_policy.action_id) }
            .values
            .any { policies_for_branch ->
                policies_for_branch
                    .groupBy { it.execution_order }
                    .any { (_, same_order_policies) -> same_order_policies.size > 1 }
            }
        return !duplicates_exist
    }

    private fun action_id_branch(action_id: ActionId): StepBranch {
        return when (action_id) {
            ActionId.SEND_SMS, ActionId.NOTIFY_MESSAGE_APP, ActionId.LAUNCH_INTENT -> StepBranch.NOTIFY
            ActionId.UNINSTALL_APPS, ActionId.DELETE_PATHS, ActionId.ADVANCED_SHELL_COMMANDS -> StepBranch.DESTRUCTIVE
            ActionId.SELF_UNINSTALL -> StepBranch.FINALIZE
        }
    }

    private fun uninstall_allowlist_valid(allowlist: List<String>): Boolean {
        val packages = allowlist.map(String::trim).filter { it.isNotEmpty() }
        return packages.all { PACKAGE_NAME_REGEX.matches(it) }
    }

    private fun delete_allowlist_valid(allowlist: List<DeleteTarget>): Boolean {
        return allowlist.all(::delete_target_valid)
    }

    private fun delete_target_valid(target: DeleteTarget): Boolean {
        val has_path = target.path.trim().isNotEmpty()
        val has_content_uri = target.content_uri?.trim().isNullOrEmpty().not()
        if (has_path == has_content_uri) return false
        if (has_content_uri) return delete_content_uri_target_valid(target)
        return delete_path_target_valid(target)
    }

    private fun delete_path_target_valid(target: DeleteTarget): Boolean {
        val raw_path = target.path.trim()
        if (raw_path.isEmpty()) return false
        if (!raw_path.startsWith("/")) return false

        val path_file = File(raw_path)
        val canonical_path = runCatching { path_file.canonicalPath }.getOrNull() ?: return false
        if (canonical_path != raw_path) return false
        if (canonical_path == "/") return false
        if (Files.isSymbolicLink(path_file.toPath())) return false
        return true
    }

    private fun delete_content_uri_target_valid(target: DeleteTarget): Boolean {
        val raw_content_uri = target.content_uri?.trim().orEmpty()
        if (raw_content_uri.isEmpty()) return false
        if (!raw_content_uri.startsWith("content://", ignoreCase = true)) return false

        val without_scheme = raw_content_uri.substring(10)
        val authority = without_scheme.substringBefore('/').trim()
        if (authority.isEmpty()) return false

        val normalized_content_uri = raw_content_uri.lowercase()
        return normalized_content_uri.contains("/tree/") || normalized_content_uri.contains("/document/")
    }

    private fun advanced_shell_commands_valid(profile: EmergencyProfile): Boolean {
        return profile.advanced_shell_commands
            .filter { it.enabled }
            .all { command ->
                val has_argv = command.argv.map(String::trim).any { it.isNotEmpty() }
                val has_raw_shell = !command.raw_shell.isNullOrBlank()
                has_argv || has_raw_shell
            }
    }

    private fun is_action_required(
        action_policies: List<ActionPolicy>,
        action_id: ActionId,
        policy_key: String? = null
    ): Boolean {
        return resolve_action_policy(
            action_policies = action_policies,
            action_id = action_id,
            policy_key = policy_key
        )?.required ?: false
    }

    private fun is_action_enabled(
        action_policies: List<ActionPolicy>,
        action_id: ActionId,
        policy_key: String? = null
    ): Boolean {
        return resolve_action_policy(
            action_policies = action_policies,
            action_id = action_id,
            policy_key = policy_key
        )?.enabled ?: true
    }

    private fun resolve_action_policy(
        action_policies: List<ActionPolicy>,
        action_id: ActionId,
        policy_key: String?
    ): ActionPolicy? {
        val normalized_policy_key = policy_key?.trim().orEmpty()
        if (normalized_policy_key.isNotEmpty()) {
            unique_policy_by_key(action_policies, normalized_policy_key)?.let { return it }
        }

        val action_policy_key = ActionPolicyKeys.for_action(action_id)
        unique_policy_by_key(action_policies, action_policy_key)?.let { return it }
        return null
    }

    private fun unique_policy_by_key(action_policies: List<ActionPolicy>, policy_key: String): ActionPolicy? {
        val matches = action_policies.filter { it.policy_key == policy_key }
        return matches.singleOrNull()
    }

    private fun action_policy_keys_valid(action_policies: List<ActionPolicy>): Boolean {
        val normalized_keys = action_policies.map { it.policy_key.trim() }
        if (normalized_keys.any { it.isEmpty() }) return false
        return normalized_keys.distinct().size == normalized_keys.size
    }

    companion object {
        private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    }
}
