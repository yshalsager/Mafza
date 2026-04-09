package com.yshalsager.mafza

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yshalsager.mafza.core.contracts.BackupService
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.data.backup.EncryptedBackupService
import com.yshalsager.mafza.core.data.history.MafzaHistoryDatabase
import com.yshalsager.mafza.core.data.history.RunHistoryDao
import com.yshalsager.mafza.core.data.history.RunHistoryStore
import com.yshalsager.mafza.core.data.profile.AndroidKeystoreProfileCipher
import com.yshalsager.mafza.core.data.profile.EncryptedProfileStore
import com.yshalsager.mafza.core.data.profile.ProfileDataStoreFactory
import com.yshalsager.mafza.emergency.providers.ActionProviderRegistry
import com.yshalsager.mafza.emergency.providers.IntentMessageAppProvider
import com.yshalsager.mafza.preflight.PreflightValidator
import com.yshalsager.mafza.preflight.PreflightReport
import com.yshalsager.mafza.preflight.requires_shizuku_for_live_destructive_actions
import com.yshalsager.mafza.profile.AppRoute
import com.yshalsager.mafza.profile.CancelWindowOverlay
import com.yshalsager.mafza.profile.ProfileScreen
import com.yshalsager.mafza.profile.cancel_emergency_run
import com.yshalsager.mafza.profile.set_destructive_actions_enabled
import com.yshalsager.mafza.profile.start_emergency_run
import com.yshalsager.mafza.shizuku.ShizukuPermissionManager
import com.yshalsager.mafza.ui.theme.MafzaTokens
import com.yshalsager.mafza.ui.theme.MafzaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MIN_CANCEL_WINDOW_SECONDS = 1
private const val MAX_CANCEL_WINDOW_SECONDS = 30
private const val RUN_DETAILS_ROUTE_BASE = "run_details"
private const val RUN_DETAILS_ARG_RUN_ID = "run_id"
private const val RUN_DETAILS_ROUTE_PATTERN = "$RUN_DETAILS_ROUTE_BASE/{$RUN_DETAILS_ARG_RUN_ID}"

class MainActivity : ComponentActivity() {
    private val profile_store by lazy {
        ProfileDataStoreFactory.create_profile_store(
            context = applicationContext,
            profile_cipher = AndroidKeystoreProfileCipher()
        )
    }
    private val shizuku_permission_manager by lazy { ShizukuPermissionManager() }
    private val history_database by lazy { MafzaHistoryDatabase.create(applicationContext) }
    private val history_dao by lazy { history_database.run_history_dao() }
    private val backup_service: BackupService by lazy {
        EncryptedBackupService(
            app_context = applicationContext,
            profile_store = profile_store,
            history_store = RunHistoryStore(history_database)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MafzaTheme {
                MafzaApp(
                    app_context = applicationContext,
                    profile_store = profile_store,
                    history_dao = history_dao,
                    shizuku_permission_manager = shizuku_permission_manager,
                    backup_service = backup_service
                )
            }
        }
    }

    override fun onDestroy() {
        shizuku_permission_manager.close()
        history_database.close()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MafzaApp(
    app_context: Context,
    profile_store: EncryptedProfileStore,
    history_dao: RunHistoryDao,
    shizuku_permission_manager: ShizukuPermissionManager,
    backup_service: BackupService
) {
    val spacing = MafzaTokens.spacing
    val profile by profile_store.profile_flow.collectAsStateWithLifecycle(initialValue = EmergencyProfile())
    val shizuku_state by shizuku_permission_manager.state.collectAsStateWithLifecycle()
    val app_scope = rememberCoroutineScope()
    var pending_destructive_enable by remember { mutableStateOf(false) }
    var cancel_window_remaining_seconds by remember { mutableIntStateOf(0) }
    var preflight_refresh_nonce by remember { mutableIntStateOf(0) }
    var profile_add_action_nonce by remember { mutableIntStateOf(0) }
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
    val preflight_report by produceState(
        initialValue = PreflightReport(
            live_ready = false,
            dry_run_ready = false,
            live_blocking_issues = emptyList(),
            dry_run_blocking_issues = emptyList(),
            warnings = emptyList()
        ),
        key1 = profile,
        key2 = shizuku_state,
        key3 = preflight_refresh_nonce
    ) {
        value = withContext(Dispatchers.IO) {
            preflight_validator.validate(
                profile = profile,
                shizuku_permission_state = shizuku_state,
                perform_telegram_reachability_checks = true
            )
        }
    }

    val nav_controller = rememberNavController()
    val back_stack_entry by nav_controller.currentBackStackEntryAsState()
    val current_route = back_stack_entry?.destination?.route ?: AppRoute.HOME.route
    val is_run_details_route = current_route == RUN_DETAILS_ROUTE_PATTERN

    LaunchedEffect(
        shizuku_state.is_running,
        shizuku_state.is_permission_granted,
        pending_destructive_enable,
        profile
    ) {
        if (!pending_destructive_enable) return@LaunchedEffect
        val candidate_profile = profile.copy(destructive_actions_enabled = true)
        if (!requires_shizuku_for_live_destructive_actions(candidate_profile)) {
            set_destructive_actions_enabled(profile_store, enabled = true)
            pending_destructive_enable = false
            return@LaunchedEffect
        }
        if (!shizuku_state.is_running || !shizuku_state.is_permission_granted) return@LaunchedEffect

        set_destructive_actions_enabled(profile_store, enabled = true)
        pending_destructive_enable = false
    }
    LaunchedEffect(cancel_window_remaining_seconds) {
        if (cancel_window_remaining_seconds <= 0) return@LaunchedEffect
        delay(1_000L)
        cancel_window_remaining_seconds -= 1
    }
    fun start_live_run() {
        start_emergency_run(
            app_context = app_context,
            mode = ExecutionMode.LIVE
        )
        val cancel_window_seconds = profile.cancel_window_seconds.coerceIn(
            MIN_CANCEL_WINDOW_SECONDS,
            MAX_CANCEL_WINDOW_SECONDS
        )
        cancel_window_remaining_seconds = cancel_window_seconds
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            val title_res_id = if (current_route == AppRoute.PROFILE.route) {
                R.string.profile_title
            } else if (current_route == AppRoute.HISTORY.route) {
                R.string.history_title
            } else if (is_run_details_route) {
                R.string.run_details_title
            } else {
                R.string.home_title
            }
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(title_res_id)) },
                navigationIcon = {
                    if (is_run_details_route) {
                        IconButton(onClick = { nav_controller.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.run_details_back_action)
                            )
                        }
                    }
                },
                actions = {
                    if (current_route == AppRoute.PROFILE.route) {
                        IconButton(onClick = { profile_add_action_nonce += 1 }) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.profile_topbar_add_action)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!is_run_details_route) {
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
        }
    ) { inner_padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner_padding)
                .padding(horizontal = spacing.lg, vertical = spacing.xl)
        ) {
            NavHost(
                navController = nav_controller,
                startDestination = AppRoute.HOME.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(AppRoute.HOME.route) {
                    HomeScreen(
                        history_dao = history_dao,
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
                        on_run_live = ::start_live_run,
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
                            val candidate_profile = profile.copy(destructive_actions_enabled = true)
                            val shizuku_required = requires_shizuku_for_live_destructive_actions(candidate_profile)
                            if (!shizuku_required) {
                                app_scope.launch {
                                    set_destructive_actions_enabled(profile_store, enabled = true)
                                }
                                pending_destructive_enable = false
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
                        profile_store = profile_store,
                        app_context = app_context,
                        action_provider_registry = action_provider_registry,
                        backup_service = backup_service,
                        add_action_nonce = profile_add_action_nonce,
                        on_add_action_nonce_consumed = { profile_add_action_nonce = 0 },
                        on_backup_restore_complete = { preflight_refresh_nonce += 1 }
                    )
                }
                composable(AppRoute.HISTORY.route) {
                    HistoryScreen(
                        history_dao = history_dao,
                        on_open_run_details = { run_id ->
                            nav_controller.navigate(run_details_route(run_id))
                        }
                    )
                }
                composable(
                    route = RUN_DETAILS_ROUTE_PATTERN,
                    arguments = listOf(navArgument(RUN_DETAILS_ARG_RUN_ID) { defaultValue = "" })
                ) { nav_back_stack_entry ->
                    RunDetailsScreen(
                        run_id = nav_back_stack_entry.arguments?.getString(RUN_DETAILS_ARG_RUN_ID).orEmpty(),
                        history_dao = history_dao
                    )
                }
            }
        }
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

private fun run_details_route(run_id: String): String = "$RUN_DETAILS_ROUTE_BASE/$run_id"
