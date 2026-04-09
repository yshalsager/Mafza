package com.yshalsager.mafza.screenshots

import android.app.Instrumentation
import android.app.LocaleManager
import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yshalsager.mafza.R
import com.yshalsager.mafza.MainActivity
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.IntentActionSpec
import com.yshalsager.mafza.core.contracts.RunStatus
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TriggerSource
import com.yshalsager.mafza.core.data.history.MafzaHistoryDatabase
import com.yshalsager.mafza.core.data.history.RunHistoryEntity
import com.yshalsager.mafza.core.data.history.StepHistoryEntity
import com.yshalsager.mafza.core.data.profile.AndroidKeystoreProfileCipher
import com.yshalsager.mafza.core.data.profile.ProfileDataStoreFactory
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.locale.LocaleTestRule

@RunWith(AndroidJUnit4::class)
class FastlaneScreenshotsTest {
    @get:Rule
    val compose_rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val locale_rule = LocaleTestRule()

    @Test
    fun capture_home_tab() {
        prepare_fixture_state()
        Screengrab.screenshot("01_home")
    }

    @Test
    fun capture_profile_tab() {
        val app_context: Context = ApplicationProvider.getApplicationContext()
        prepare_fixture_state()
        select_tab(label = app_context.getString(R.string.nav_profile))
        Screengrab.screenshot("02_profile")
    }

    @Test
    fun capture_history_tab() {
        val app_context: Context = ApplicationProvider.getApplicationContext()
        prepare_fixture_state()
        select_tab(label = app_context.getString(R.string.nav_history))
        Screengrab.screenshot("04_history")
    }

    @Test
    fun capture_available_actions_drawer() {
        val app_context: Context = ApplicationProvider.getApplicationContext()
        prepare_fixture_state()
        select_tab(label = app_context.getString(R.string.nav_profile))
        compose_rule.onNodeWithContentDescription(
            label = app_context.getString(R.string.profile_topbar_add_action),
            useUnmergedTree = true
        ).performClick()
        wait_for_ui_settle()
        Screengrab.screenshot("03_actions_drawer")
    }

    private fun apply_app_locale(instrumentation: Instrumentation, app_context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val locale_manager = app_context.getSystemService(LocaleManager::class.java) ?: return
        locale_manager.applicationLocales = LocaleList.forLanguageTags(Screengrab.getLocale())
        instrumentation.waitForIdleSync()
    }

    private fun wait_for_ui_settle() {
        compose_rule.waitForIdle()
        compose_rule.mainClock.advanceTimeBy(750L)
        compose_rule.waitForIdle()
    }

    private fun select_tab(label: String) {
        compose_rule.onNodeWithText(label, useUnmergedTree = true).performClick()
        wait_for_ui_settle()
    }

    private fun prepare_fixture_state() {
        val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
        val app_context: Context = ApplicationProvider.getApplicationContext()
        apply_app_locale(instrumentation = instrumentation, app_context = app_context)
        seed_fixture_data(app_context = app_context)
        grant_runtime_permissions(instrumentation = instrumentation, package_name = app_context.packageName)
        compose_rule.activityRule.scenario.recreate()
        wait_for_ui_settle()
    }

    private fun seed_fixture_data(app_context: Context) {
        runBlocking {
            val profile_store = ProfileDataStoreFactory.create_profile_store(
                context = app_context,
                profile_cipher = AndroidKeystoreProfileCipher()
            )
            profile_store.write_profile(screenshot_profile_fixture())

            val history_database = MafzaHistoryDatabase.create(app_context)
            history_database.clearAllTables()
            val history_dao = history_database.run_history_dao()

            val now = FIXTURE_NOW_EPOCH_MS
            val latest_run_id = "shot-live-success-001"
            val earlier_run_id = "shot-dry-success-002"

            history_dao.upsert_run(
                RunHistoryEntity(
                    run_id = latest_run_id,
                    started_at_epoch_ms = now - 180_000L,
                    completed_at_epoch_ms = now - 150_000L,
                    trigger = TriggerSource.MANUAL_IN_APP,
                    mode = ExecutionMode.LIVE,
                    status = RunStatus.COMPLETED_SUCCESS
                )
            )
            history_dao.insert_steps(
                listOf(
                    StepHistoryEntity(
                        run_id = latest_run_id,
                        step_index = 0,
                        step_id = "capture_location",
                        status = StepStatus.SUCCESS,
                        details = "lat=30.0444 lon=31.2357",
                        started_at_epoch_ms = now - 180_000L,
                        finished_at_epoch_ms = now - 178_000L
                    ),
                    StepHistoryEntity(
                        run_id = latest_run_id,
                        step_index = 1,
                        step_id = "send_sms:+201000000000",
                        status = StepStatus.SUCCESS,
                        details = "delivered",
                        started_at_epoch_ms = now - 178_000L,
                        finished_at_epoch_ms = now - 172_000L
                    ),
                    StepHistoryEntity(
                        run_id = latest_run_id,
                        step_index = 2,
                        step_id = "launch_intent:share_location",
                        status = StepStatus.SUCCESS,
                        details = "resolved",
                        started_at_epoch_ms = now - 171_000L,
                        finished_at_epoch_ms = now - 165_000L
                    )
                )
            )

            history_dao.upsert_run(
                RunHistoryEntity(
                    run_id = earlier_run_id,
                    started_at_epoch_ms = now - 3_600_000L,
                    completed_at_epoch_ms = now - 3_560_000L,
                    trigger = TriggerSource.SHORTCUT,
                    mode = ExecutionMode.DRY_RUN,
                    status = RunStatus.COMPLETED_SUCCESS
                )
            )
            history_dao.insert_steps(
                listOf(
                    StepHistoryEntity(
                        run_id = earlier_run_id,
                        step_index = 0,
                        step_id = "capture_location",
                        status = StepStatus.SUCCESS,
                        details = "lat=30.0610 lon=31.2190",
                        started_at_epoch_ms = now - 3_600_000L,
                        finished_at_epoch_ms = now - 3_598_000L
                    ),
                    StepHistoryEntity(
                        run_id = earlier_run_id,
                        step_index = 1,
                        step_id = "send_sms:+201122233344",
                        status = StepStatus.SUCCESS,
                        details = "dry_run",
                        started_at_epoch_ms = now - 3_597_000L,
                        finished_at_epoch_ms = now - 3_596_000L
                    )
                )
            )
        }
    }

    private fun screenshot_profile_fixture(): EmergencyProfile {
        return EmergencyProfile(
            sms_recipients = listOf("+201000000000", "+201122233344"),
            message_template = "Mafza help at {timestamp}. {maps_url}",
            cancel_window_seconds = 2,
            location_timeout_seconds = 8,
            sms_timeout_seconds = 10,
            intent_timeout_seconds = 20,
            intent_actions = listOf(
                IntentActionSpec(
                    id = "share_location",
                    label = "Share location",
                    intent_action = "android.intent.action.VIEW",
                    data_uri = "https://maps.google.com/?q={lat},{lon}",
                    mime_type = null,
                    categories = emptyList(),
                    package_name = null,
                    activity_name = null,
                    extras_json = null,
                    flags = emptyList(),
                    timeout_seconds = 20,
                    continue_on_failure = true,
                    enabled = true
                )
            ),
            default_mode = ExecutionMode.LIVE,
            destructive_actions_enabled = false,
            triggers_enabled = true
        )
    }

    private fun grant_runtime_permissions(instrumentation: Instrumentation, package_name: String) {
        val permissions = listOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_PHONE_STATE",
            "android.permission.SEND_SMS"
        )
        permissions.forEach { permission ->
            execute_shell_command(
                instrumentation = instrumentation,
                command = "pm grant $package_name $permission"
            )
        }
    }

    private fun execute_shell_command(instrumentation: Instrumentation, command: String) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        descriptor.safe_close()
    }

    private fun ParcelFileDescriptor.safe_close() {
        runCatching { close() }
    }

    companion object {
        private const val FIXTURE_NOW_EPOCH_MS = 1_775_052_000_000L
    }
}
