package com.yshalsager.mafza

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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

private const val MIN_CANCEL_WINDOW_SECONDS = 1
private const val MAX_CANCEL_WINDOW_SECONDS = 30

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
    var cancel_window_remaining_seconds by remember { mutableStateOf(0) }
    var preflight_refresh_nonce by remember { mutableIntStateOf(0) }
    val runtime_permission_launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        preflight_refresh_nonce += 1
    }

    val action_provider_registry = remember {
        ActionProviderRegistry(
            providers = listOf(IntentMessageAppProvider(app_context = app_context))
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

    val nav_controller = rememberNavController()
    val back_stack_entry by nav_controller.currentBackStackEntryAsState()
    val current_route = back_stack_entry?.destination?.route ?: AppRoute.HOME.route

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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AppBottomNavigationBar(
                current_route = current_route,
                on_navigate = { route ->
                    nav_controller.navigate(route) {
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(nav_controller.graph.startDestinationId) {
                            saveState = true
                        }
                    }
                }
            )
        }
    ) { inner_padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner_padding)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            NavHost(
                navController = nav_controller,
                startDestination = AppRoute.HOME.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(AppRoute.HOME.route) {
                    HomeScreen(
                        profile = profile,
                        preflight_report = preflight_report,
                        shizuku_state = shizuku_state,
                        on_open_profile_setup = {
                            nav_controller.navigate(AppRoute.PROFILE.route) { launchSingleTop = true }
                        },
                        on_request_runtime_permissions = { permissions ->
                            runtime_permission_launcher.launch(permissions.toTypedArray())
                        },
                        on_refresh_shizuku = {
                            preflight_refresh_nonce += 1
                            shizuku_permission_manager.refresh_state()
                        },
                        on_request_shizuku_permission = {
                            pending_destructive_enable = true
                            shizuku_permission_manager.request_permission()
                        },
                        on_run_live = { show_live_confirmation = true },
                        on_run_dry_run = {
                            start_emergency_run(
                                app_context = app_context,
                                mode = ExecutionMode.DRY_RUN
                            )
                        },
                        on_set_destructive_actions_enabled = { enabled ->
                            if (!enabled) {
                                pending_destructive_enable = false
                                app_scope.launch {
                                    set_destructive_actions_enabled(profile_store, enabled = false)
                                }
                                return@HomeScreen
                            }
                            if (shizuku_state.is_running && shizuku_state.is_permission_granted) {
                                app_scope.launch {
                                    set_destructive_actions_enabled(profile_store, enabled = true)
                                }
                                return@HomeScreen
                            }
                            pending_destructive_enable = true
                            shizuku_permission_manager.request_permission()
                        }
                    )
                }
                composable(AppRoute.PROFILE.route) {
                    ProfileScreen(
                        profile = profile,
                        profile_store = profile_store
                    )
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
                        cancel_window_remaining_seconds = profile.cancel_window_seconds.coerceIn(
                            MIN_CANCEL_WINDOW_SECONDS,
                            MAX_CANCEL_WINDOW_SECONDS
                        )
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

@Composable
private fun AppBottomNavigationBar(
    current_route: String,
    on_navigate: (String) -> Unit
) {
    NavigationBar {
        AppRoute.entries.forEach { route ->
            val is_selected = current_route == route.route
            NavigationBarItem(
                selected = is_selected,
                onClick = { on_navigate(route.route) },
                icon = { Text(text = if (route == AppRoute.HOME) "H" else "P") },
                label = { Text(text = stringResource(route.label_res_id)) }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    profile: EmergencyProfile,
    preflight_report: PreflightReport,
    shizuku_state: ShizukuPermissionState,
    on_open_profile_setup: () -> Unit,
    on_request_runtime_permissions: (List<String>) -> Unit,
    on_refresh_shizuku: () -> Unit,
    on_request_shizuku_permission: () -> Unit,
    on_run_live: () -> Unit,
    on_run_dry_run: () -> Unit,
    on_set_destructive_actions_enabled: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium
        )

        PreflightCard(
            preflight_report = preflight_report,
            shizuku_state = shizuku_state,
            on_open_profile_setup = on_open_profile_setup,
            on_request_runtime_permissions = on_request_runtime_permissions,
            on_refresh = on_refresh_shizuku,
            on_request_permission = on_request_shizuku_permission
        )

        RunActionsCard(
            live_enabled = preflight_report.live_ready,
            dry_run_enabled = preflight_report.dry_run_ready,
            on_run_live = on_run_live,
            on_run_dry_run = on_run_dry_run
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
                            onCheckedChange = on_set_destructive_actions_enabled,
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    profile: EmergencyProfile,
    profile_store: EncryptedProfileStore
) {
    val app_scope = rememberCoroutineScope()

    var sms_recipients_input by remember(profile) { mutableStateOf(profile.sms_recipients.joinToString("\n")) }
    var notify_target_input by remember(profile) { mutableStateOf(profile.notify_target) }
    var message_template_input by remember(profile) { mutableStateOf(profile.message_template) }
    var cancel_window_input by remember(profile) { mutableStateOf(profile.cancel_window_seconds.toString()) }
    var location_timeout_input by remember(profile) { mutableStateOf(profile.location_timeout_seconds.toString()) }
    var sms_timeout_input by remember(profile) { mutableStateOf(profile.sms_timeout_seconds.toString()) }
    var intent_timeout_input by remember(profile) { mutableStateOf(profile.intent_timeout_seconds.toString()) }
    var destructive_actions_enabled by remember(profile) { mutableStateOf(profile.destructive_actions_enabled) }
    var triggers_enabled by remember(profile) { mutableStateOf(profile.triggers_enabled) }
    var self_uninstall_enabled by remember(profile) { mutableStateOf(profile.self_uninstall_enabled) }
    var save_error_message by remember { mutableStateOf<String?>(null) }
    var saved_successfully by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.profile_title),
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = sms_recipients_input,
            onValueChange = {
                sms_recipients_input = it
                saved_successfully = false
                save_error_message = null
            },
            label = { Text(text = stringResource(R.string.profile_sms_recipients_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        OutlinedTextField(
            value = notify_target_input,
            onValueChange = {
                notify_target_input = it
                saved_successfully = false
                save_error_message = null
            },
            label = { Text(text = stringResource(R.string.profile_notify_target_label)) },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = message_template_input,
            onValueChange = {
                message_template_input = it
                saved_successfully = false
                save_error_message = null
            },
            label = { Text(text = stringResource(R.string.profile_message_template_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        TimeoutField(
            label = stringResource(R.string.profile_cancel_window_label),
            value = cancel_window_input,
            on_value_change = {
                cancel_window_input = it
                saved_successfully = false
                save_error_message = null
            }
        )
        TimeoutField(
            label = stringResource(R.string.profile_location_timeout_label),
            value = location_timeout_input,
            on_value_change = {
                location_timeout_input = it
                saved_successfully = false
                save_error_message = null
            }
        )
        TimeoutField(
            label = stringResource(R.string.profile_sms_timeout_label),
            value = sms_timeout_input,
            on_value_change = {
                sms_timeout_input = it
                saved_successfully = false
                save_error_message = null
            }
        )
        TimeoutField(
            label = stringResource(R.string.profile_intent_timeout_label),
            value = intent_timeout_input,
            on_value_change = {
                intent_timeout_input = it
                saved_successfully = false
                save_error_message = null
            }
        )

        ToggleField(
            title = stringResource(R.string.profile_destructive_enabled_label),
            checked = destructive_actions_enabled,
            on_checked_change = {
                destructive_actions_enabled = it
                saved_successfully = false
                save_error_message = null
            }
        )
        ToggleField(
            title = stringResource(R.string.profile_triggers_enabled_label),
            checked = triggers_enabled,
            on_checked_change = {
                triggers_enabled = it
                saved_successfully = false
                save_error_message = null
            }
        )
        ToggleField(
            title = stringResource(R.string.profile_self_uninstall_enabled_label),
            checked = self_uninstall_enabled,
            on_checked_change = {
                self_uninstall_enabled = it
                saved_successfully = false
                save_error_message = null
            }
        )

        Button(
            onClick = {
                app_scope.launch {
                    val updated_profile = profile.copy(
                        sms_recipients = parse_sms_recipients(sms_recipients_input),
                        notify_target = notify_target_input.trim(),
                        message_template = message_template_input,
                        cancel_window_seconds = parse_int_or_fallback(
                            value = cancel_window_input,
                            fallback = profile.cancel_window_seconds,
                            min_value = MIN_CANCEL_WINDOW_SECONDS,
                            max_value = MAX_CANCEL_WINDOW_SECONDS
                        ),
                        location_timeout_seconds = parse_int_or_fallback(
                            value = location_timeout_input,
                            fallback = profile.location_timeout_seconds,
                            min_value = 1,
                            max_value = 120
                        ),
                        sms_timeout_seconds = parse_int_or_fallback(
                            value = sms_timeout_input,
                            fallback = profile.sms_timeout_seconds,
                            min_value = 1,
                            max_value = 120
                        ),
                        intent_timeout_seconds = parse_int_or_fallback(
                            value = intent_timeout_input,
                            fallback = profile.intent_timeout_seconds,
                            min_value = 1,
                            max_value = 180
                        ),
                        destructive_actions_enabled = destructive_actions_enabled,
                        triggers_enabled = triggers_enabled,
                        self_uninstall_enabled = self_uninstall_enabled
                    )

                    runCatching {
                        profile_store.write_profile(updated_profile)
                    }.onSuccess {
                        saved_successfully = true
                        save_error_message = null
                    }.onFailure { error ->
                        saved_successfully = false
                        save_error_message = error.message ?: "unknown_error"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.profile_save_action))
        }

        if (saved_successfully) {
            Text(
                text = stringResource(R.string.profile_saved_successfully),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (save_error_message != null) {
            Text(
                text = stringResource(R.string.profile_save_error_prefix) + save_error_message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun TimeoutField(
    label: String,
    value: String,
    on_value_change: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = on_value_change,
        label = { Text(text = label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

@Composable
private fun ToggleField(
    title: String,
    checked: Boolean,
    on_checked_change: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = on_checked_change
            )
        }
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
    on_open_profile_setup: () -> Unit,
    on_request_runtime_permissions: (List<String>) -> Unit,
    on_refresh: () -> Unit,
    on_request_permission: () -> Unit
) {
    val missing_location_permission = preflight_report.live_blocking_issues.contains("missing_location_permission")
    val missing_sms_permission = preflight_report.live_blocking_issues.contains("missing_send_sms_permission")
    val missing_recipients = preflight_report.live_blocking_issues.contains("at_least_one_sms_recipient_required")
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
            if (missing_recipients) {
                Button(onClick = on_open_profile_setup, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.preflight_open_profile_setup))
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

private enum class AppRoute(
    val route: String,
    val label_res_id: Int
) {
    HOME(route = "home", label_res_id = R.string.nav_home),
    PROFILE(route = "profile", label_res_id = R.string.nav_profile)
}

private fun parse_sms_recipients(input: String): List<String> {
    return input
        .split('\n', ',', ';')
        .map(String::trim)
        .filter { it.isNotEmpty() }
        .distinct()
}

private fun parse_int_or_fallback(
    value: String,
    fallback: Int,
    min_value: Int,
    max_value: Int
): Int {
    val parsed = value.trim().toIntOrNull() ?: return fallback
    return parsed.coerceIn(min_value, max_value)
}
