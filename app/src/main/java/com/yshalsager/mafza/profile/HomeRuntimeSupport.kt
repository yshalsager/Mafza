package com.yshalsager.mafza.profile

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yshalsager.mafza.R
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.TriggerSource
import com.yshalsager.mafza.core.data.profile.EncryptedProfileStore
import com.yshalsager.mafza.emergency.EmergencyExecutionService
import com.yshalsager.mafza.emergency.EmergencyServiceContract
import com.yshalsager.mafza.preflight.PreflightReport
import com.yshalsager.mafza.shizuku.ShizukuPermissionState

internal suspend fun set_destructive_actions_enabled(
    profile_store: EncryptedProfileStore,
    enabled: Boolean
) {
    val current_profile = profile_store.read_profile()
    if (current_profile.destructive_actions_enabled == enabled) return
    profile_store.write_profile(
        current_profile.copy(destructive_actions_enabled = enabled)
    )
}

internal fun start_emergency_run(
    app_context: Context,
    mode: ExecutionMode
) {
    val service_intent = Intent(app_context, EmergencyExecutionService::class.java).apply {
        action = EmergencyServiceContract.ACTION_START_RUN
        putExtra(EmergencyServiceContract.EXTRA_TRIGGER_SOURCE, TriggerSource.MANUAL_IN_APP.name)
        putExtra(EmergencyServiceContract.EXTRA_EXECUTION_MODE, mode.name)
    }
    ContextCompat.startForegroundService(app_context, service_intent)
}

internal fun cancel_emergency_run(app_context: Context) {
    val service_intent = Intent(app_context, EmergencyExecutionService::class.java).apply {
        action = EmergencyServiceContract.ACTION_CANCEL_RUN
    }
    ContextCompat.startForegroundService(app_context, service_intent)
}

@Composable
internal fun PreflightCard(
    preflight_report: PreflightReport,
    shizuku_state: ShizukuPermissionState,
    on_open_profile_setup: () -> Unit,
    on_request_runtime_permissions: (List<String>) -> Unit,
    on_refresh: () -> Unit,
    on_request_permission: () -> Unit
) {
    val missing_location_permission = preflight_report.live_blocking_issues.contains("missing_location_permission")
    val missing_sms_permission = preflight_report.live_blocking_issues.contains("missing_send_sms_permission")
    val missing_recipients = preflight_report.live_blocking_issues.contains("at_least_one_sms_recipient_required")
    val missing_or_duplicate_binding_id = preflight_report.live_blocking_issues.any { issue ->
        issue == "action_binding_missing_binding_id" || issue == "action_binding_duplicate_binding_id"
    }
    val invalid_policy_keys = preflight_report.live_blocking_issues.contains("action_policy_invalid_or_duplicate_policy_key")
    val needs_profile_setup = missing_recipients || missing_or_duplicate_binding_id || invalid_policy_keys
    val runtime_permissions_to_request = buildList {
        if (missing_location_permission) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (missing_sms_permission) {
            add(Manifest.permission.SEND_SMS)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.preflight_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = when {
                    preflight_report.live_ready -> stringResource(R.string.preflight_ready)
                    !shizuku_state.is_running -> stringResource(R.string.preflight_shizuku_not_running)
                    !shizuku_state.is_permission_granted -> stringResource(R.string.preflight_shizuku_permission_required)
                    preflight_report.live_blocking_issues.isNotEmpty() -> preflight_report.live_blocking_issues.joinToString("\n")
                    else -> stringResource(R.string.preflight_unknown)
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (preflight_report.warnings.isNotEmpty()) {
                Text(
                    text = preflight_report.warnings.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (shizuku_state.should_show_permission_rationale && !shizuku_state.is_permission_granted) {
                Text(
                    text = stringResource(R.string.preflight_permission_rationale),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Box(modifier = Modifier.height(4.dp))
            Button(onClick = on_refresh, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.preflight_refresh))
            }
            if (shizuku_state.is_running && !shizuku_state.is_permission_granted) {
                Button(onClick = on_request_permission, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.preflight_request_permission))
                }
            }
            if (runtime_permissions_to_request.isNotEmpty()) {
                Button(
                    onClick = { on_request_runtime_permissions(runtime_permissions_to_request.distinct()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.preflight_request_runtime_permissions))
                }
            }
            if (needs_profile_setup) {
                Button(onClick = on_open_profile_setup, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.preflight_open_profile_setup))
                }
            }
        }
    }
}

@Composable
internal fun RunActionsCard(
    live_enabled: Boolean,
    dry_run_enabled: Boolean,
    on_run_live: () -> Unit,
    on_run_dry_run: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.run_actions_title),
                style = MaterialTheme.typography.titleMedium
            )
            Button(
                onClick = on_run_live,
                enabled = live_enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.run_live_action))
            }
            Button(
                onClick = on_run_dry_run,
                enabled = dry_run_enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.run_dry_run_action))
            }
        }
    }
}

@Composable
internal fun CancelWindowOverlay(
    remaining_seconds: Int,
    on_cancel: () -> Unit
) {
    val scrim_interaction_source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.48f))
            .clickable(
                interactionSource = scrim_interaction_source,
                indication = null,
                onClick = {}
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .align(Alignment.Center)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.cancel_window_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.cancel_window_body, remaining_seconds),
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(
                    onClick = on_cancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.cancel_window_action))
                }
            }
        }
    }
}
