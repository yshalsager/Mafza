package com.yshalsager.mafza

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.TriggerSource
import com.yshalsager.mafza.core.data.profile.AndroidKeystoreProfileCipher
import com.yshalsager.mafza.core.data.profile.EncryptedProfileStore
import com.yshalsager.mafza.core.data.profile.ProfileDataStoreFactory
import com.yshalsager.mafza.emergency.EmergencyExecutionService
import com.yshalsager.mafza.emergency.EmergencyServiceContract
import com.yshalsager.mafza.emergency.providers.ActionProviderRegistry
import com.yshalsager.mafza.emergency.providers.IntentMessageAppProvider
import com.yshalsager.mafza.preflight.PreflightReport
import com.yshalsager.mafza.preflight.PreflightValidator
import com.yshalsager.mafza.shizuku.ShizukuPermissionManager
import com.yshalsager.mafza.shizuku.ShizukuPermissionState
import com.yshalsager.mafza.ui.theme.MafzaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val profile_store by lazy {
        ProfileDataStoreFactory.create_profile_store(
            context = applicationContext,
            profile_cipher = AndroidKeystoreProfileCipher()
        )
    }
    private val shizuku_permission_manager by lazy { ShizukuPermissionManager() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MafzaTheme {
                MafzaApp(
                    app_context = applicationContext,
                    profile_store = profile_store,
                    shizuku_permission_manager = shizuku_permission_manager
                )
            }
        }
    }

    override fun onDestroy() {
        shizuku_permission_manager.close()
        super.onDestroy()
    }
}

@Composable
private fun MafzaApp(
    app_context: Context,
    profile_store: EncryptedProfileStore,
    shizuku_permission_manager: ShizukuPermissionManager
) {
    val profile by profile_store.profile_flow.collectAsStateWithLifecycle(initialValue = EmergencyProfile())
    val shizuku_state by shizuku_permission_manager.state.collectAsStateWithLifecycle()
    val app_scope = rememberCoroutineScope()
    var pending_destructive_enable by remember { mutableStateOf(false) }
    var show_live_confirmation by remember { mutableStateOf(false) }
    var cancel_window_remaining_seconds by remember { mutableIntStateOf(0) }
    var preflight_refresh_nonce by remember { mutableIntStateOf(0) }
    val action_provider_registry = remember {
        ActionProviderRegistry(
            providers = listOf(
                IntentMessageAppProvider(app_context = app_context)
            )
        )
    }
    val preflight_validator = remember {
        PreflightValidator(
            app_context = app_context,
            action_provider_registry = action_provider_registry
        )
    }
    val preflight_report = remember(profile, shizuku_state, preflight_refresh_nonce) {
        preflight_validator.validate(
            profile = profile,
            shizuku_permission_state = shizuku_state
        )
    }

    LaunchedEffect(
        shizuku_state.is_running,
        shizuku_state.is_permission_granted,
        pending_destructive_enable
    ) {
        if (!pending_destructive_enable) return@LaunchedEffect
        if (!shizuku_state.is_running || !shizuku_state.is_permission_granted) return@LaunchedEffect

        set_destructive_actions_enabled(profile_store, enabled = true)
        pending_destructive_enable = false
    }
    LaunchedEffect(cancel_window_remaining_seconds) {
        if (cancel_window_remaining_seconds <= 0) return@LaunchedEffect
        delay(1_000L)
        cancel_window_remaining_seconds -= 1
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { inner_padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner_padding)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium
            )
            PreflightCard(
                preflight_report = preflight_report,
                shizuku_state = shizuku_state,
                on_refresh = {
                    preflight_refresh_nonce += 1
                    shizuku_permission_manager.refresh_state()
                },
                on_request_permission = {
                    pending_destructive_enable = true
                    shizuku_permission_manager.request_permission()
                }
            )
            RunActionsCard(
                live_enabled = preflight_report.live_ready,
                dry_run_enabled = preflight_report.dry_run_ready,
                on_run_live = { show_live_confirmation = true },
                on_run_dry_run = {
                    start_emergency_run(
                        app_context = app_context,
                        mode = ExecutionMode.DRY_RUN
                    )
                }
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.destructive_toggle_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.destructive_toggle_subtitle),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Box(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Switch(
                                checked = profile.destructive_actions_enabled,
                                onCheckedChange = { enabled ->
                                    if (!enabled) {
                                        pending_destructive_enable = false
                                        app_scope.launch {
                                            set_destructive_actions_enabled(profile_store, enabled = false)
                                        }
                                        return@Switch
                                    }
                                    if (shizuku_state.is_running && shizuku_state.is_permission_granted) {
                                        app_scope.launch {
                                            set_destructive_actions_enabled(profile_store, enabled = true)
                                        }
                                        return@Switch
                                    }
                                    pending_destructive_enable = true
                                    shizuku_permission_manager.request_permission()
                                },
                                modifier = Modifier.align(Alignment.CenterStart)
                            )
                        }
                    }
                }
            }
        }
    }

    if (show_live_confirmation) {
        AlertDialog(
            onDismissRequest = { show_live_confirmation = false },
            title = {
                Text(text = stringResource(R.string.run_live_confirm_title))
            },
            text = {
                Text(text = stringResource(R.string.run_live_confirm_body))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        show_live_confirmation = false
                        start_emergency_run(
                            app_context = app_context,
                            mode = ExecutionMode.LIVE
                        )
                        cancel_window_remaining_seconds = profile.cancel_window_seconds.coerceIn(1, 30)
                    }
                ) {
                    Text(text = stringResource(R.string.run_live_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { show_live_confirmation = false }) {
                    Text(text = stringResource(R.string.run_live_cancel_action))
                }
            }
        )
    }
    if (cancel_window_remaining_seconds > 0) {
        CancelWindowOverlay(
            remaining_seconds = cancel_window_remaining_seconds,
            on_cancel = {
                cancel_window_remaining_seconds = 0
                cancel_emergency_run(app_context = app_context)
            }
        )
    }
}

private suspend fun set_destructive_actions_enabled(
    profile_store: EncryptedProfileStore,
    enabled: Boolean
) {
    val current_profile = profile_store.read_profile()
    if (current_profile.destructive_actions_enabled == enabled) return
    profile_store.write_profile(
        current_profile.copy(destructive_actions_enabled = enabled)
    )
}

private fun start_emergency_run(
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

private fun cancel_emergency_run(app_context: Context) {
    val service_intent = Intent(app_context, EmergencyExecutionService::class.java).apply {
        action = EmergencyServiceContract.ACTION_CANCEL_RUN
    }
    ContextCompat.startForegroundService(app_context, service_intent)
}

@Composable
private fun PreflightCard(
    preflight_report: PreflightReport,
    shizuku_state: ShizukuPermissionState,
    on_refresh: () -> Unit,
    on_request_permission: () -> Unit
) {
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
        }
    }
}

@Composable
private fun RunActionsCard(
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
private fun CancelWindowOverlay(
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
