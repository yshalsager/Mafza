package com.yshalsager.mafza.emergency.steps

import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TriggerSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsRecipientStepTest {
    @Test
    fun `returns dry-run skip and does not invoke sender`() = runTest {
        var sender_invoked = false
        val sms_step = SmsRecipientStep(
            app_context = null,
            run_step_state = RunStepState(),
            recipient = "+20123456789",
            recipient_index = 0,
            has_sms_permission_checker = { true },
            message_renderer = { _, _ -> "message" },
            sms_sender = { _, _ ->
                sender_invoked = true
                Result.success(Unit)
            }
        )

        val result = sms_step.execute(test_step_context(mode = ExecutionMode.DRY_RUN))
        assertEquals(StepStatus.SKIPPED_DRY_RUN, result.status)
        assertFalse(sender_invoked)
    }

    @Test
    fun `returns skipped unavailable when sms permission is missing`() = runTest {
        val sms_step = SmsRecipientStep(
            app_context = null,
            run_step_state = RunStepState(),
            recipient = "+20123456789",
            recipient_index = 0,
            has_sms_permission_checker = { false },
            message_renderer = { _, _ -> "message" }
        )

        val result = sms_step.execute(test_step_context())
        assertEquals(StepStatus.SKIPPED_UNAVAILABLE, result.status)
        assertEquals("missing_send_sms_permission", result.details)
    }

    @Test
    fun `returns timeout when sms sending exceeds configured timeout`() = runTest {
        val sms_step = SmsRecipientStep(
            app_context = null,
            run_step_state = RunStepState(),
            recipient = "+20123456789",
            recipient_index = 0,
            has_sms_permission_checker = { true },
            message_renderer = { _, _ -> "message" },
            sms_sender = { _, _ ->
                delay(2_000L)
                Result.success(Unit)
            }
        )

        val result = sms_step.execute(
            test_step_context(profile = EmergencyProfile(sms_timeout_seconds = 1))
        )
        assertEquals(StepStatus.TIMED_OUT, result.status)
    }

    @Test
    fun `returns skipped unavailable when sms service is unavailable`() = runTest {
        val sms_step = SmsRecipientStep(
            app_context = null,
            run_step_state = RunStepState(),
            recipient = "+20123456789",
            recipient_index = 0,
            has_sms_permission_checker = { true },
            message_renderer = { _, _ -> "message" },
            sms_sender = { _, _ ->
                Result.failure(SmsRecipientStep.SmsServiceUnavailableException())
            }
        )

        val result = sms_step.execute(test_step_context())
        assertEquals(StepStatus.SKIPPED_UNAVAILABLE, result.status)
        assertEquals("sms_service_unavailable", result.details)
    }

    @Test
    fun `returns success when sms sending succeeds`() = runTest {
        val sms_step = SmsRecipientStep(
            app_context = null,
            run_step_state = RunStepState(),
            recipient = "+20123456789",
            recipient_index = 0,
            has_sms_permission_checker = { true },
            message_renderer = { _, _ -> "message" },
            sms_sender = { _, _ -> Result.success(Unit) }
        )

        val result = sms_step.execute(test_step_context())
        assertEquals(StepStatus.SUCCESS, result.status)
        assertTrue(result.details?.startsWith("sms_sent_") == true)
    }

    private fun test_step_context(
        profile: EmergencyProfile = EmergencyProfile(message_template = "Emergency at {timestamp}"),
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
