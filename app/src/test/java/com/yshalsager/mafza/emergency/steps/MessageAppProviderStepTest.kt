package com.yshalsager.mafza.emergency.steps

import com.yshalsager.mafza.emergency.providers.ActionProviderRegistry
import com.yshalsager.mafza.core.contracts.ActionBinding
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ActionProvider
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.ProviderCapabilities
import com.yshalsager.mafza.core.contracts.ProviderExecutionResult
import com.yshalsager.mafza.core.contracts.ProviderPreflightResult
import com.yshalsager.mafza.core.contracts.ProviderRequest
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TriggerSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageAppProviderStepTest {
    @Test
    fun `returns skipped unavailable when binding is disabled`() = runTest {
        val binding = ActionBinding(
            action_id = ActionId.NOTIFY_MESSAGE_APP,
            package_name = "com.example.msg",
            activity_name = null,
            enabled = false
        )
        val step = MessageAppProviderStep(
            app_context = null,
            action_provider_registry = ActionProviderRegistry(providers = emptyList()),
            run_step_state = RunStepState(),
            action_binding = binding,
            binding_index = 0,
            message_renderer = { _, _ -> "msg" }
        )

        val result = step.execute(test_step_context())
        assertEquals(StepStatus.SKIPPED_UNAVAILABLE, result.status)
        assertEquals("notify_binding_disabled", result.details)
    }

    @Test
    fun `returns dry-run skip and does not execute provider`() = runTest {
        val fake_provider = FakeMessageProvider()
        val binding = ActionBinding(
            action_id = ActionId.NOTIFY_MESSAGE_APP,
            package_name = "com.example.msg",
            activity_name = null,
            enabled = true
        )
        val step = MessageAppProviderStep(
            app_context = null,
            action_provider_registry = ActionProviderRegistry(providers = listOf(fake_provider)),
            run_step_state = RunStepState(),
            action_binding = binding,
            binding_index = 0,
            message_renderer = { _, _ -> "msg" }
        )

        val result = step.execute(
            test_step_context(
                mode = ExecutionMode.DRY_RUN,
                profile = profile_with_binding()
            )
        )
        assertEquals(StepStatus.SKIPPED_DRY_RUN, result.status)
        assertFalse(fake_provider.execute_called)
    }

    @Test
    fun `returns skipped unavailable on provider capability mismatch`() = runTest {
        val fake_provider = FakeMessageProvider(
            capabilities = ProviderCapabilities(
                supports_template = true,
                supports_target = false,
                supports_auto_send = false
            )
        )
        val binding = ActionBinding(
            action_id = ActionId.NOTIFY_MESSAGE_APP,
            package_name = "com.example.msg",
            activity_name = null,
            enabled = true
        )
        val step = MessageAppProviderStep(
            app_context = null,
            action_provider_registry = ActionProviderRegistry(providers = listOf(fake_provider)),
            run_step_state = RunStepState(),
            action_binding = binding,
            binding_index = 0,
            message_renderer = { _, _ -> "msg" }
        )

        val result = step.execute(
            test_step_context(
                profile = profile_with_binding(notify_target = "+20123456789")
            )
        )
        assertEquals(StepStatus.SKIPPED_UNAVAILABLE, result.status)
        assertEquals("provider_capability_mismatch", result.details)
        assertFalse(fake_provider.execute_called)
    }

    @Test
    fun `returns success when provider execution succeeds`() = runTest {
        val fake_provider = FakeMessageProvider(
            execute_result = ProviderExecutionResult(
                status = StepStatus.SUCCESS,
                details = "provider_ok"
            )
        )
        val binding = ActionBinding(
            action_id = ActionId.NOTIFY_MESSAGE_APP,
            package_name = "com.example.msg",
            activity_name = null,
            enabled = true
        )
        val step = MessageAppProviderStep(
            app_context = null,
            action_provider_registry = ActionProviderRegistry(providers = listOf(fake_provider)),
            run_step_state = RunStepState(),
            action_binding = binding,
            binding_index = 0,
            message_renderer = { _, _ -> "msg" }
        )

        val result = step.execute(
            test_step_context(
                profile = profile_with_binding()
            )
        )
        assertEquals(StepStatus.SUCCESS, result.status)
        assertTrue(fake_provider.execute_called)
    }

    @Test
    fun `returns timed out when provider execution exceeds timeout`() = runTest {
        val fake_provider = FakeMessageProvider(
            execute_block = {
                delay(2_000L)
                ProviderExecutionResult(
                    status = StepStatus.SUCCESS,
                    details = "provider_ok"
                )
            }
        )
        val binding = ActionBinding(
            action_id = ActionId.NOTIFY_MESSAGE_APP,
            package_name = "com.example.msg",
            activity_name = null,
            enabled = true
        )
        val step = MessageAppProviderStep(
            app_context = null,
            action_provider_registry = ActionProviderRegistry(providers = listOf(fake_provider)),
            run_step_state = RunStepState(),
            action_binding = binding,
            binding_index = 0,
            message_renderer = { _, _ -> "msg" },
            timeout_millis_provider = { 500L }
        )

        val result = step.execute(
            test_step_context(
                profile = profile_with_binding()
            )
        )
        assertEquals(StepStatus.TIMED_OUT, result.status)
    }

    private fun test_step_context(
        profile: EmergencyProfile = EmergencyProfile(),
        mode: ExecutionMode = ExecutionMode.LIVE
    ): StepContext {
        return StepContext(
            run_id = "test_run",
            trigger = TriggerSource.MANUAL_IN_APP,
            mode = mode,
            profile = profile,
            started_at_epoch_ms = 0L
        )
    }

    private fun profile_with_binding(notify_target: String = ""): EmergencyProfile {
        return EmergencyProfile(
            notify_target = notify_target,
            action_bindings = listOf(
                ActionBinding(
                    action_id = ActionId.NOTIFY_MESSAGE_APP,
                    package_name = "com.example.msg",
                    activity_name = null,
                    enabled = true
                )
            )
        )
    }

    private class FakeMessageProvider(
        private val capabilities: ProviderCapabilities = ProviderCapabilities(
            supports_template = true,
            supports_target = true,
            supports_auto_send = false
        ),
        private val preflight: ProviderPreflightResult = ProviderPreflightResult(ready = true),
        private val execute_result: ProviderExecutionResult = ProviderExecutionResult(
            status = StepStatus.SUCCESS,
            details = "ok"
        ),
        private val execute_block: (suspend () -> ProviderExecutionResult)? = null
    ) : ActionProvider {
        var execute_called = false

        override fun actionId(): ActionId = ActionId.NOTIFY_MESSAGE_APP

        override fun isAvailable(binding: ActionBinding): Boolean = true

        override fun capabilities(binding: ActionBinding): ProviderCapabilities = capabilities

        override suspend fun preflight(binding: ActionBinding): ProviderPreflightResult = preflight

        override suspend fun execute(request: ProviderRequest): ProviderExecutionResult {
            execute_called = true
            val block = execute_block
            if (block != null) return block()
            return execute_result
        }
    }
}
