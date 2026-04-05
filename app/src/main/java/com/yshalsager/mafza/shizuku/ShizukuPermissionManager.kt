package com.yshalsager.mafza.shizuku

import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

data class ShizukuPermissionState(
    val is_running: Boolean = false,
    val is_permission_granted: Boolean = false,
    val should_show_permission_rationale: Boolean = false
)

class ShizukuPermissionManager {
    private val state_flow = MutableStateFlow(ShizukuPermissionState())
    private val binder_received_listener = Shizuku.OnBinderReceivedListener { refresh_state() }
    private val binder_dead_listener = Shizuku.OnBinderDeadListener { refresh_state() }
    private val permission_result_listener = Shizuku.OnRequestPermissionResultListener { request_code, grant_result ->
        if (request_code != permission_request_code) return@OnRequestPermissionResultListener
        state_flow.value = current_state(
            permission_granted_override = grant_result == PackageManager.PERMISSION_GRANTED
        )
    }

    val state: StateFlow<ShizukuPermissionState> = state_flow.asStateFlow()

    init {
        Shizuku.addBinderReceivedListenerSticky(binder_received_listener)
        Shizuku.addBinderDeadListener(binder_dead_listener)
        Shizuku.addRequestPermissionResultListener(permission_result_listener)
        refresh_state()
    }

    fun refresh_state() {
        state_flow.value = current_state()
    }

    fun request_permission() {
        if (!Shizuku.pingBinder()) {
            refresh_state()
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            refresh_state()
            return
        }
        Shizuku.requestPermission(permission_request_code)
    }

    fun close() {
        Shizuku.removeBinderReceivedListener(binder_received_listener)
        Shizuku.removeBinderDeadListener(binder_dead_listener)
        Shizuku.removeRequestPermissionResultListener(permission_result_listener)
    }

    private fun current_state(permission_granted_override: Boolean? = null): ShizukuPermissionState {
        val is_running = Shizuku.pingBinder()
        val is_permission_granted = permission_granted_override ?: (
            is_running && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        )
        val should_show_permission_rationale = is_running &&
            !is_permission_granted &&
            Shizuku.shouldShowRequestPermissionRationale()

        return ShizukuPermissionState(
            is_running = is_running,
            is_permission_granted = is_permission_granted,
            should_show_permission_rationale = should_show_permission_rationale
        )
    }

    companion object {
        private const val permission_request_code = 7301
    }
}
