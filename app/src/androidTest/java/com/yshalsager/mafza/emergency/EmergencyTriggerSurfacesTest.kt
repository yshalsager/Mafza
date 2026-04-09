package com.yshalsager.mafza.emergency

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.RunStatus
import com.yshalsager.mafza.core.contracts.TriggerSource
import com.yshalsager.mafza.core.data.history.MafzaHistoryDatabase
import com.yshalsager.mafza.core.data.history.RunHistoryDao
import com.yshalsager.mafza.core.data.history.RunHistoryEntity
import com.yshalsager.mafza.core.data.profile.AndroidKeystoreProfileCipher
import com.yshalsager.mafza.core.data.profile.EncryptedProfileStore
import com.yshalsager.mafza.core.data.profile.ProfileDataStoreFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmergencyTriggerSurfacesTest {
    private lateinit var profile_store: EncryptedProfileStore
    private lateinit var history_database: MafzaHistoryDatabase
    private lateinit var history_dao: RunHistoryDao
    private lateinit var package_manager: PackageManager
    private lateinit var activity_manager: ActivityManager
    private lateinit var app_package_name: String
    private lateinit var original_profile: EmergencyProfile

    @Before
    fun set_up(): Unit = runBlocking {
        val app_context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        profile_store = ProfileDataStoreFactory.create_profile_store(
            context = app_context,
            profile_cipher = AndroidKeystoreProfileCipher()
        )
        history_database = MafzaHistoryDatabase.create(app_context)
        history_dao = history_database.run_history_dao()
        package_manager = app_context.packageManager
        activity_manager = app_context.getSystemService(ActivityManager::class.java)
        app_package_name = app_context.packageName
        original_profile = profile_store.read_profile()
    }

    @After
    fun tear_down(): Unit = runBlocking {
        profile_store.write_profile(original_profile)
        history_database.close()
    }

    @Test
    fun shortcut_widget_and_qs_tile_components_are_registered() = runBlocking {
        val app_context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val shortcut_manager = app_context.getSystemService(ShortcutManager::class.java)
        val shortcut_ids = shortcut_manager.manifestShortcuts.map { it.id }
        assertTrue(shortcut_ids.contains("shortcut_emergency_live"))

        val widget_receiver_info = package_manager.getReceiverInfo(
            ComponentName(app_context, EmergencyWidgetProvider::class.java),
            PackageManager.GET_META_DATA
        )
        assertTrue(widget_receiver_info.exported)

        val tile_service_info = package_manager.getServiceInfo(
            ComponentName(app_context, EmergencyQuickSettingsTileService::class.java),
            PackageManager.GET_META_DATA
        )
        assertTrue(tile_service_info.exported)
        assertEquals(android.Manifest.permission.BIND_QUICK_SETTINGS_TILE, tile_service_info.permission)
    }

    @Test
    fun external_trigger_is_blocked_when_triggers_are_disabled() = runBlocking {
        profile_store.write_profile(test_profile(triggers_enabled = false))
        val latest_run_before = latest_run()?.run_id

        EmergencyExternalTriggerDispatcher.dispatch(
            context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext,
            trigger_action = EmergencyServiceContract.ACTION_TRIGGER_WIDGET
        )

        val latest_run_after = wait_for_new_run(previous_run_id = latest_run_before, timeout_millis = 4_000L)
        assertEquals(null, latest_run_after)
    }

    @Test
    fun manual_trigger_runs_even_when_external_triggers_are_disabled() = runBlocking {
        profile_store.write_profile(test_profile(triggers_enabled = false))
        val app_context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val latest_run_before = latest_run()?.run_id

        val service_intent = Intent(app_context, EmergencyExecutionService::class.java).apply {
            action = EmergencyServiceContract.ACTION_START_RUN
            putExtra(EmergencyServiceContract.EXTRA_TRIGGER_SOURCE, TriggerSource.MANUAL_IN_APP.name)
            putExtra(EmergencyServiceContract.EXTRA_EXECUTION_MODE, ExecutionMode.LIVE.name)
        }
        app_context.startForegroundService(service_intent)

        val latest_run_after = wait_for_new_run(previous_run_id = latest_run_before, timeout_millis = 15_000L)
        assertNotNull(latest_run_after)
        assertEquals(TriggerSource.MANUAL_IN_APP, latest_run_after?.trigger)
        assertEquals(ExecutionMode.LIVE, latest_run_after?.mode)
    }

    @Test
    fun receiver_forces_live_mode_for_external_trigger_actions() = runBlocking {
        profile_store.write_profile(test_profile(triggers_enabled = true))
        val app_context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val latest_run_before = latest_run()?.run_id

        val receiver_intent = Intent(app_context, EmergencyStartReceiver::class.java).apply {
            action = EmergencyServiceContract.ACTION_TRIGGER_SHORTCUT
            putExtra(EmergencyServiceContract.EXTRA_EXECUTION_MODE, ExecutionMode.DRY_RUN.name)
            putExtra(EmergencyServiceContract.EXTRA_TRIGGER_SOURCE, TriggerSource.MANUAL_IN_APP.name)
        }
        app_context.sendBroadcast(receiver_intent)

        val latest_run_after = wait_for_new_run(previous_run_id = latest_run_before, timeout_millis = 15_000L)
        assertNotNull(latest_run_after)
        assertEquals(TriggerSource.SHORTCUT, latest_run_after?.trigger)
        assertEquals(ExecutionMode.LIVE, latest_run_after?.mode)
    }

    @Test
    fun service_runs_foreground_lifecycle_and_stops_after_completion() = runBlocking {
        profile_store.write_profile(
            test_profile(triggers_enabled = true).copy(
                cancel_window_seconds = 5,
                location_timeout_seconds = 1
            )
        )
        val app_context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val latest_run_before = latest_run()?.run_id

        val service_intent = Intent(app_context, EmergencyExecutionService::class.java).apply {
            action = EmergencyServiceContract.ACTION_START_RUN
            putExtra(EmergencyServiceContract.EXTRA_TRIGGER_SOURCE, TriggerSource.MANUAL_IN_APP.name)
            putExtra(EmergencyServiceContract.EXTRA_EXECUTION_MODE, ExecutionMode.LIVE.name)
        }
        app_context.startForegroundService(service_intent)

        assertTrue(wait_for_service_running_state(expected_running = true, timeout_millis = 5_000L))

        val latest_run_after = wait_for_new_run(previous_run_id = latest_run_before, timeout_millis = 20_000L)
        assertNotNull(latest_run_after)
        assertTrue(latest_run_after?.status != RunStatus.RUNNING)

        assertTrue(wait_for_service_running_state(expected_running = false, timeout_millis = 10_000L))
    }

    private suspend fun latest_run(): RunHistoryEntity? {
        return history_dao.latest_runs(limit = 1).firstOrNull()
    }

    private suspend fun wait_for_new_run(previous_run_id: String?, timeout_millis: Long): RunHistoryEntity? {
        val started_at = System.currentTimeMillis()
        while (System.currentTimeMillis() - started_at < timeout_millis) {
            val latest_run = latest_run()
            if (latest_run != null && latest_run.run_id != previous_run_id) return latest_run
            delay(250L)
        }
        return null
    }

    private suspend fun wait_for_service_running_state(
        expected_running: Boolean,
        timeout_millis: Long
    ): Boolean {
        val started_at = System.currentTimeMillis()
        while (System.currentTimeMillis() - started_at < timeout_millis) {
            if (is_emergency_service_running() == expected_running) return true
            delay(200L)
        }
        return false
    }

    private fun is_emergency_service_running(): Boolean {
        val running_services = activity_manager.getRunningServices(Int.MAX_VALUE)
        return running_services.any { service_info ->
            service_info.service.packageName == app_package_name &&
                service_info.service.className == EmergencyExecutionService::class.java.name
        }
    }

    private fun test_profile(triggers_enabled: Boolean): EmergencyProfile {
        return EmergencyProfile(
            triggers_enabled = triggers_enabled,
            cancel_window_seconds = 1,
            location_timeout_seconds = 1,
            sms_recipients = emptyList(),
            uninstall_allowlist = emptyList(),
            delete_allowlist = emptyList(),
            advanced_shell_commands = emptyList()
        )
    }
}
