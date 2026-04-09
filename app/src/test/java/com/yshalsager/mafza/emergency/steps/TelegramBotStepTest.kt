package com.yshalsager.mafza.emergency.steps

import com.yshalsager.mafza.emergency.telegram.TelegramBotCheckResult
import com.yshalsager.mafza.emergency.telegram.TelegramBotClient
import com.yshalsager.mafza.emergency.telegram.TelegramBotSendResult
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TelegramBotActionSpec
import com.yshalsager.mafza.core.contracts.TriggerSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramBotStepTest {
    @Test
    fun `dry run skips without sending`() = runBlocking {
        val client = FakeTelegramBotClient()
        val step = create_step(client)

        val result = step.execute(
            test_step_context(mode = ExecutionMode.DRY_RUN)
        )

        assertEquals(StepStatus.SKIPPED_DRY_RUN, result.status)
        assertFalse(client.send_called)
    }

    @Test
    fun `live success returns success`() = runBlocking {
        val client = FakeTelegramBotClient(
            send_result = TelegramBotSendResult(
                success = true,
                details = "telegram_ok"
            )
        )
        val step = create_step(client)

        val result = step.execute(test_step_context())

        assertEquals(StepStatus.SUCCESS, result.status)
        assertTrue(client.send_called)
    }

    @Test
    fun `live failure returns failed`() = runBlocking {
        val client = FakeTelegramBotClient(
            send_result = TelegramBotSendResult(
                success = false,
                details = "telegram_http_401"
            )
        )
        val step = create_step(client)

        val result = step.execute(test_step_context())

        assertEquals(StepStatus.FAILED, result.status)
        assertEquals("telegram_http_401", result.details)
    }

    @Test
    fun `timeout returns timed out`() = runBlocking {
        val client = FakeTelegramBotClient(
            send_block = {
                Thread.sleep(2_000L)
                TelegramBotSendResult(success = true, details = "telegram_ok")
            }
        )
        val step = create_step(
            client = client,
            timeout_seconds = 1
        )

        val result = step.execute(test_step_context())

        assertEquals(StepStatus.TIMED_OUT, result.status)
    }

    private fun create_step(
        client: TelegramBotClient,
        timeout_seconds: Int = 20
    ): TelegramBotStep {
        return TelegramBotStep(
            app_context = null,
            run_step_state = RunStepState(),
            telegram_action_spec = TelegramBotActionSpec(
                id = "telegram_1",
                label = "Primary bot",
                bot_token = "123456:abcdefghijklmnopqrstuvwxyzABCDE",
                chat_id = "@channel_name",
                template_override = null,
                timeout_seconds = timeout_seconds,
                enabled = true
            ),
            action_index = 0,
            telegram_bot_client = client,
            message_renderer = { _, _, _ -> "Mafza test message" }
        )
    }

    private fun test_step_context(mode: ExecutionMode = ExecutionMode.LIVE): StepContext {
        return StepContext(
            run_id = "run_1",
            trigger = TriggerSource.MANUAL_IN_APP,
            mode = mode,
            profile = EmergencyProfile(),
            started_at_epoch_ms = 0L
        )
    }

    private class FakeTelegramBotClient(
        private val send_result: TelegramBotSendResult = TelegramBotSendResult(
            success = true,
            details = "telegram_ok"
        ),
        private val send_block: (() -> TelegramBotSendResult)? = null
    ) : TelegramBotClient {
        var send_called: Boolean = false

        override fun check_bot(bot_token: String, timeout_seconds: Int): TelegramBotCheckResult {
            return TelegramBotCheckResult(ready = true, details = "telegram_get_me_ok")
        }

        override fun send_message(
            bot_token: String,
            chat_id: String,
            text: String,
            timeout_seconds: Int
        ): TelegramBotSendResult {
            send_called = true
            val block = send_block
            if (block != null) return block()
            return send_result
        }
    }
}
