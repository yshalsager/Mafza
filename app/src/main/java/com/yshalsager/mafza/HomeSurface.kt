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
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.preflight.PreflightReport
import com.yshalsager.mafza.profile.AppRoute
import com.yshalsager.mafza.profile.PreflightCard
import com.yshalsager.mafza.profile.RunActionsCard
import com.yshalsager.mafza.shizuku.ShizukuPermissionState

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
                            AppRoute.HISTORY -> Icons.Filled.History
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
