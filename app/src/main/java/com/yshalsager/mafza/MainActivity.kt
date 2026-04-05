package com.yshalsager.mafza

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.data.profile.AndroidKeystoreProfileCipher
import com.yshalsager.mafza.core.data.profile.EncryptedProfileStore
import com.yshalsager.mafza.core.data.profile.ProfileDataStoreFactory
import com.yshalsager.mafza.shizuku.ShizukuPermissionManager
import com.yshalsager.mafza.shizuku.ShizukuPermissionState
import com.yshalsager.mafza.ui.theme.MafzaTheme
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
    profile_store: EncryptedProfileStore,
    shizuku_permission_manager: ShizukuPermissionManager
) {
    val profile by profile_store.profile_flow.collectAsStateWithLifecycle(initialValue = EmergencyProfile())
    val shizuku_state by shizuku_permission_manager.state.collectAsStateWithLifecycle()
    val app_scope = rememberCoroutineScope()
    var pending_destructive_enable by remember { mutableStateOf(false) }

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
                profile = profile,
                shizuku_state = shizuku_state,
                on_refresh = { shizuku_permission_manager.refresh_state() },
                on_request_permission = {
                    pending_destructive_enable = true
                    shizuku_permission_manager.request_permission()
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

@Composable
private fun PreflightCard(
    profile: EmergencyProfile,
    shizuku_state: ShizukuPermissionState,
    on_refresh: () -> Unit,
    on_request_permission: () -> Unit
) {
    val preflight_ready = !profile.destructive_actions_enabled || (
        shizuku_state.is_running && shizuku_state.is_permission_granted
    )

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
                    preflight_ready -> stringResource(R.string.preflight_ready)
                    !shizuku_state.is_running -> stringResource(R.string.preflight_shizuku_not_running)
                    !shizuku_state.is_permission_granted -> stringResource(R.string.preflight_shizuku_permission_required)
                    else -> stringResource(R.string.preflight_unknown)
                },
                style = MaterialTheme.typography.bodyMedium
            )
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
