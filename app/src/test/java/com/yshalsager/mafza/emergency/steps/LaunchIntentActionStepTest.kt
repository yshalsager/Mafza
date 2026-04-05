package com.yshalsager.mafza.emergency.steps

import android.content.ActivityNotFoundException
import android.content.Intent
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.IntentActionSpec
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TriggerSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchIntentActionStepTest {
    @Test
    fun `returns dry-run skip and does not launch`() = runTest {
        var launched = false
        val step = LaunchIntentActionStep(
            app_context = null,
            intent_action_spec = test_intent_spec(),
            intent_builder = { Intent() },
            intent_resolver = { true },
            intent_launcher = {
                launched = true
                Result.success(Unit)
            }
        )

        val result = step.execute(test_step_context(mode = ExecutionMode.DRY_RUN))
        assertEquals(StepStatus.SKIPPED_DRY_RUN, result.status)
        assertFalse(launched)
    }

    @Test
    fun `returns skipped unavailable when intent cannot resolve`() = runTest {
        val step = LaunchIntentActionStep(
            app_context = null,
            intent_action_spec = test_intent_spec(),
            intent_builder = { Intent() },
            intent_resolver = { false }
        )

        val result = step.execute(test_step_context())
        assertEquals(StepStatus.SKIPPED_UNAVAILABLE, result.status)
        assertEquals("intent_unresolvable", result.details)
    }

    @Test
    fun `returns timeout when launch exceeds timeout`() = runTest {
        val step = LaunchIntentActionStep(
            app_context = null,
            intent_action_spec = test_intent_spec(timeout_seconds = 1),
            intent_builder = { Intent() },
            intent_resolver = { true },
            intent_launcher = {
                delay(2_000L)
                Result.success(Unit)
            }
        )

        val result = step.execute(test_step_context())
        assertEquals(StepStatus.TIMED_OUT, result.status)
    }

    @Test
    fun `uses profile intent timeout when spec timeout is invalid`() = runTest {
        val step = LaunchIntentActionStep(
            app_context = null,
            intent_action_spec = test_intent_spec(timeout_seconds = 0),
            intent_builder = { Intent() },
            intent_resolver = { true },
            intent_launcher = {
                delay(2_000L)
                Result.success(Unit)
            }
        )

        val result = step.execute(
            test_step_context(profile = EmergencyProfile(intent_timeout_seconds = 1))
        )
        assertEquals(StepStatus.TIMED_OUT, result.status)
    }

    @Test
    fun `returns skipped unavailable when activity is not found`() = runTest {
        val step = LaunchIntentActionStep(
            app_context = null,
            intent_action_spec = test_intent_spec(),
            intent_builder = { Intent() },
            intent_resolver = { true },
            intent_launcher = { Result.failure(ActivityNotFoundException("not_found")) }
        )

        val result = step.execute(test_step_context())
        assertEquals(StepStatus.SKIPPED_UNAVAILABLE, result.status)
        assertEquals("intent_activity_not_found", result.details)
    }

    @Test
    fun `returns success when launch succeeds`() = runTest {
        val step = LaunchIntentActionStep(
            app_context = null,
            intent_action_spec = test_intent_spec(),
            intent_builder = { Intent() },
            intent_resolver = { true },
            intent_launcher = { Result.success(Unit) }
        )

        val result = step.execute(test_step_context())
        assertEquals(StepStatus.SUCCESS, result.status)
        assertTrue(result.details == "intent_launched")
    }

    private fun test_intent_spec(
        timeout_seconds: Int = 20,
        extras_json: String? = null
    ): IntentActionSpec {
        return IntentActionSpec(
            id = "intent_1",
            label = "Open Uri",
            intent_action = null,
            data_uri = "https://example.com",
            mime_type = null,
            categories = emptyList(),
            package_name = null,
            activity_name = null,
            extras_json = extras_json,
            flags = emptyList(),
            timeout_seconds = timeout_seconds,
            continue_on_failure = true,
            enabled = true
        )
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
}
