package com.yshalsager.mafza.emergency.steps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yshalsager.mafza.core.contracts.ActionBinding
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ActionProvider
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.IntentActionSpec
import com.yshalsager.mafza.core.contracts.ProviderCapabilities
import com.yshalsager.mafza.core.contracts.ProviderExecutionResult
import com.yshalsager.mafza.core.contracts.ProviderPreflightResult
import com.yshalsager.mafza.core.contracts.ProviderRequest
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TriggerSource
import com.yshalsager.mafza.emergency.providers.ActionProviderRegistry
import com.yshalsager.mafza.emergency.providers.IntentMessageAppProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActionStepsDeviceTest {
    @Test
    fun launch_intent_action_step_launches_main_activity() = runBlocking {
        val app_context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val spec = IntentActionSpec(
            id = "open_main_activity",
            label = "Open Main Activity",
            intent_action = Intent.ACTION_MAIN,
            data_uri = null,
            mime_type = null,
            categories = listOf(Intent.CATEGORY_LAUNCHER),
            package_name = app_context.packageName,
            activity_name = "com.yshalsager.mafza.MainActivity",
            extras_json = null,
            flags = listOf("FLAG_ACTIVITY_NEW_TASK"),
            timeout_seconds = 10,
            continue_on_failure = true,
            enabled = true
        )
        val step = LaunchIntentActionStep(
            app_context = app_context,
            intent_action_spec = spec
        )

        val result = step.execute(test_step_context())
        assertEquals(StepStatus.SUCCESS, result.status)
    }

    @Test
    fun message_app_provider_step_executes_with_installed_share_target() = runBlocking {
        val app_context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val package_name = find_share_target_package()
        assumeTrue(
            "No installed package can handle ACTION_SEND text/plain",
            package_name != null
        )

        val binding = ActionBinding(
            action_id = ActionId.NOTIFY_MESSAGE_APP,
            binding_id = "device_share_target",
            package_name = package_name!!,
            activity_name = null,
            enabled = true
        )
        val profile = EmergencyProfile(
            action_bindings = listOf(binding),
            message_template = "Mafza test {timestamp}"
        )
        val step = MessageAppProviderStep(
            app_context = app_context,
            action_provider_registry = ActionProviderRegistry(
                providers = listOf(IntentMessageAppProvider(app_context = app_context))
            ),
            run_step_state = RunStepState(),
            action_binding = binding,
            binding_index = 0
        )

        val result = step.execute(test_step_context(profile = profile))
        assertEquals(StepStatus.SUCCESS, result.status)
    }

    @Test
    fun message_app_provider_step_returns_missing_provider_when_registry_has_no_provider() = runBlocking {
        val app_context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val binding = ActionBinding(
            action_id = ActionId.NOTIFY_MESSAGE_APP,
            binding_id = "missing_provider",
            package_name = app_context.packageName,
            activity_name = null,
            enabled = true
        )
        val step = MessageAppProviderStep(
            app_context = app_context,
            action_provider_registry = ActionProviderRegistry(providers = emptyList()),
            run_step_state = RunStepState(),
            action_binding = binding,
            binding_index = 0
        )

        val result = step.execute(test_step_context(profile = EmergencyProfile(action_bindings = listOf(binding))))
        assertEquals(StepStatus.SKIPPED_UNAVAILABLE, result.status)
        assertEquals("missing_notify_provider", result.details)
    }

    @Test
    fun message_app_provider_step_dry_run_does_not_execute_provider() = runBlocking {
        val app_context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val fake_provider = RecordingMessageProvider()
        val binding = ActionBinding(
            action_id = ActionId.NOTIFY_MESSAGE_APP,
            binding_id = "dry_run_provider",
            package_name = app_context.packageName,
            activity_name = null,
            enabled = true
        )
        val profile = EmergencyProfile(
            action_bindings = listOf(binding),
            message_template = "Mafza test {timestamp}"
        )
        val step = MessageAppProviderStep(
            app_context = app_context,
            action_provider_registry = ActionProviderRegistry(providers = listOf(fake_provider)),
            run_step_state = RunStepState(),
            action_binding = binding,
            binding_index = 0
        )

        val result = step.execute(test_step_context(profile = profile, mode = ExecutionMode.DRY_RUN))
        assertEquals(StepStatus.SKIPPED_DRY_RUN, result.status)
        assertFalse(fake_provider.execute_called)
    }

    @Test
    fun sms_recipient_step_skips_when_send_sms_permission_not_granted() = runBlocking {
        val app_context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        revoke_send_sms_permission(package_name = app_context.packageName)

        val permission_granted = app_context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        assumeTrue("SEND_SMS permission should be revoked for this test", !permission_granted)

        val step = SmsRecipientStep(
            app_context = app_context,
            run_step_state = RunStepState(),
            recipient = "+20123456789",
            recipient_index = 0
        )

        val result = step.execute(test_step_context())
        assertEquals(StepStatus.SKIPPED_UNAVAILABLE, result.status)
        assertEquals("missing_send_sms_permission", result.details)
    }

    private fun find_share_target_package(): String? {
        val app_context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val package_manager = app_context.packageManager
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Mafza share probe")
        }
        return package_manager.queryIntentActivities(intent, 0)
            .firstOrNull { resolve_info ->
                val package_name = resolve_info.activityInfo?.packageName ?: return@firstOrNull false
                package_name != app_context.packageName &&
                    package_manager.getLaunchIntentForPackage(package_name) != null
            }
            ?.activityInfo
            ?.packageName
    }

    private fun revoke_send_sms_permission(package_name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pfd = instrumentation.uiAutomation.executeShellCommand(
            "pm revoke $package_name ${Manifest.permission.SEND_SMS}"
        )
        pfd.close()
    }

    private fun test_step_context(
        profile: EmergencyProfile = EmergencyProfile(),
        mode: ExecutionMode = ExecutionMode.LIVE
    ): StepContext {
        return StepContext(
            run_id = "android_test_run",
            trigger = TriggerSource.MANUAL_IN_APP,
            mode = mode,
            profile = profile,
            started_at_epoch_ms = System.currentTimeMillis()
        )
    }

    private class RecordingMessageProvider : ActionProvider {
        var execute_called = false

        override fun actionId(): ActionId = ActionId.NOTIFY_MESSAGE_APP

        override fun isAvailable(binding: ActionBinding): Boolean = true

        override fun capabilities(binding: ActionBinding): ProviderCapabilities {
            return ProviderCapabilities(
                supports_template = true,
                supports_target = true,
                supports_auto_send = false
            )
        }

        override suspend fun preflight(binding: ActionBinding): ProviderPreflightResult {
            return ProviderPreflightResult(ready = true)
        }

        override suspend fun execute(request: ProviderRequest): ProviderExecutionResult {
            execute_called = true
            return ProviderExecutionResult(
                status = StepStatus.SUCCESS,
                details = "provider_executed"
            )
        }
    }
}
