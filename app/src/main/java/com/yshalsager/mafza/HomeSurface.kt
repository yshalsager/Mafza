package com.yshalsager.mafza

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.RunStatus
import com.yshalsager.mafza.core.data.history.RunHistoryDao
import com.yshalsager.mafza.core.data.history.RunHistoryEntity
import com.yshalsager.mafza.preflight.PreflightReport
import com.yshalsager.mafza.profile.AppRoute
import com.yshalsager.mafza.profile.PreflightCard
import com.yshalsager.mafza.profile.RunActionsCard
import com.yshalsager.mafza.shizuku.ShizukuPermissionState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun AppBottomNavigationBar(
    current_route: String,
    on_navigate: (String) -> Unit
) {
    NavigationBar {
        AppRoute.entries.forEach { route ->
            val is_selected = current_route == route.route
            NavigationBarItem(
                selected = is_selected,
                onClick = { on_navigate(route.route) },
                icon = {
                    Icon(
                        imageVector = when (route) {
                            AppRoute.HOME -> Icons.Filled.Home
                            AppRoute.PROFILE -> Icons.Filled.Person
                            AppRoute.HISTORY -> Icons.AutoMirrored.Filled.List
                        },
                        contentDescription = stringResource(route.label_res_id)
                    )
                },
                label = { Text(text = stringResource(route.label_res_id)) }
            )
        }
    }
}

@Composable
internal fun HomeScreen(
    history_dao: RunHistoryDao,
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
    val recent_runs by history_dao.latest_runs_flow(limit = 100).collectAsStateWithLifecycle(initialValue = emptyList())
    val last_success_live = recent_runs.firstOrNull { run ->
        run.mode == ExecutionMode.LIVE && run.status == RunStatus.COMPLETED_SUCCESS
    }
    val last_success_dry_run = recent_runs.firstOrNull { run ->
        run.mode == ExecutionMode.DRY_RUN && run.status == RunStatus.COMPLETED_SUCCESS
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val destructive_switch_state = if (profile.destructive_actions_enabled) {
            stringResource(R.string.a11y_state_on)
        } else {
            stringResource(R.string.a11y_state_off)
        }
        val destructive_switch_label = stringResource(R.string.destructive_toggle_title)
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
        RunHealthCard(
            last_success_live = last_success_live,
            last_success_dry_run = last_success_dry_run
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
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .semantics {
                                    contentDescription = destructive_switch_label
                                    stateDescription = destructive_switch_state
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunHealthCard(
    last_success_live: RunHistoryEntity?,
    last_success_dry_run: RunHistoryEntity?
) {
    val now_epoch_ms = System.currentTimeMillis()
    val has_live_baseline = last_success_live != null
    val has_dry_baseline = last_success_dry_run != null
    val has_any_baseline = has_live_baseline || has_dry_baseline
    val live_stale = last_success_live?.started_at_epoch_ms?.let { is_stale(it, now_epoch_ms) } == true
    val dry_run_stale = last_success_dry_run?.started_at_epoch_ms?.let { is_stale(it, now_epoch_ms) } == true

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.home_health_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    R.string.home_health_last_live,
                    last_success_live?.started_at_epoch_ms?.let(::format_home_epoch_ms)
                        ?: stringResource(R.string.home_health_never)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(
                    R.string.home_health_last_dry_run,
                    last_success_dry_run?.started_at_epoch_ms?.let(::format_home_epoch_ms)
                        ?: stringResource(R.string.home_health_never)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            when {
                !has_any_baseline -> Text(
                    text = stringResource(R.string.home_health_no_baseline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                live_stale || dry_run_stale -> Text(
                    text = stringResource(R.string.home_health_stale_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                !has_live_baseline || !has_dry_baseline -> Text(
                    text = stringResource(R.string.home_health_partial_baseline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun is_stale(
    run_started_at_epoch_ms: Long,
    now_epoch_ms: Long
): Boolean {
    return (now_epoch_ms - run_started_at_epoch_ms) > STALE_WINDOW_MILLIS
}

private fun format_home_epoch_ms(epoch_ms: Long): String {
    return HOME_DATETIME_FORMATTER.format(
        Instant.ofEpochMilli(epoch_ms).atZone(ZoneId.systemDefault())
    )
}

private val HOME_DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private const val STALE_WINDOW_MILLIS: Long = 24L * 60L * 60L * 1_000L
