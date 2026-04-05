package com.yshalsager.mafza.emergency.steps

import android.content.ContextWrapper
import com.yshalsager.mafza.emergency.shell.PrivilegedCommandExecutor
import com.yshalsager.mafza.emergency.shell.PrivilegedCommandResult
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TriggerSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfUninstallStepTest {
    @Test
    fun `returns skipped unavailable when self uninstall disabled`() = runTest {
        val fake_executor = FakeCommandExecutor()
        val step = SelfUninstallStep(
            app_context = FakeContext("com.yshalsager.mafza"),
            command_executor = fake_executor
        )

        val result = step.execute(
            test_step_context(
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    self_uninstall_enabled = false
                )
            )
        )

        assertEquals(StepStatus.SKIPPED_UNAVAILABLE, result.status)
        assertEquals("self_uninstall_disabled", result.details)
        assertTrue(fake_executor.argv_calls.isEmpty())
    }

    @Test
    fun `returns dry run skip`() = runTest {
        val fake_executor = FakeCommandExecutor()
        val step = SelfUninstallStep(
            app_context = FakeContext("com.yshalsager.mafza"),
            command_executor = fake_executor
        )

        val result = step.execute(
            test_step_context(
                mode = ExecutionMode.DRY_RUN,
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    self_uninstall_enabled = true
                )
            )
        )

        assertEquals(StepStatus.SKIPPED_DRY_RUN, result.status)
        assertTrue(fake_executor.argv_calls.isEmpty())
    }

    @Test
    fun `returns success when uninstall command exits zero`() = runTest {
        val fake_executor = FakeCommandExecutor()
        val step = SelfUninstallStep(
            app_context = FakeContext("com.yshalsager.mafza"),
            command_executor = fake_executor
        )

        val result = step.execute(
            test_step_context(
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    self_uninstall_enabled = true
                )
            )
        )

        assertEquals(StepStatus.SUCCESS, result.status)
        assertEquals(
            listOf("pm", "uninstall", "--user", "0", "com.yshalsager.mafza"),
            fake_executor.argv_calls.single()
        )
    }

    @Test
    fun `returns timed out when uninstall exceeds timeout`() = runTest {
        val fake_executor = FakeCommandExecutor(
            execute_block = { _, _ ->
                delay(16_000L)
                PrivilegedCommandResult(
                    exit_code = 0,
                    stdout = "",
                    stderr = "",
                    timed_out = false
                )
            }
        )
        val step = SelfUninstallStep(
            app_context = FakeContext("com.yshalsager.mafza"),
            command_executor = fake_executor
        )

        val result = step.execute(
            test_step_context(
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    self_uninstall_enabled = true
                )
            )
        )

        assertEquals(StepStatus.TIMED_OUT, result.status)
    }

    private fun test_step_context(
        profile: EmergencyProfile,
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

    private class FakeContext(
        private val fake_package_name: String
    ) : ContextWrapper(null) {
        override fun getPackageName(): String = fake_package_name
    }

    private class FakeCommandExecutor(
        private val available: Boolean = true,
        private val execute_block: suspend (argv: List<String>, timeout_seconds: Int) -> PrivilegedCommandResult = { _, _ ->
            PrivilegedCommandResult(
                exit_code = 0,
                stdout = "",
                stderr = "",
                timed_out = false
            )
        }
    ) : PrivilegedCommandExecutor {
        val argv_calls = mutableListOf<List<String>>()

        override fun is_available(): Boolean = available

        override suspend fun execute_argv(argv: List<String>, timeout_seconds: Int): PrivilegedCommandResult {
            argv_calls += argv
            return execute_block(argv, timeout_seconds)
        }
    }
}
