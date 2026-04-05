package com.yshalsager.mafza.emergency.steps

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UninstallAppsStepTest {
    @Test
    fun `returns dry-run skip and does not execute commands`() = runTest {
        val fake_executor = FakeCommandExecutor()
        val step = UninstallAppsStep(command_executor = fake_executor)

        val result = step.execute(
            test_step_context(
                mode = ExecutionMode.DRY_RUN,
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    uninstall_allowlist = listOf("com.example.app")
                )
            )
        )

        assertEquals(StepStatus.SKIPPED_DRY_RUN, result.status)
        assertTrue(fake_executor.argv_calls.isEmpty())
    }

    @Test
    fun `returns skipped unavailable when shizuku is unavailable`() = runTest {
        val fake_executor = FakeCommandExecutor(available = false)
        val step = UninstallAppsStep(command_executor = fake_executor)

        val result = step.execute(
            test_step_context(
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    uninstall_allowlist = listOf("com.example.app")
                )
            )
        )

        assertEquals(StepStatus.SKIPPED_UNAVAILABLE, result.status)
        assertEquals("shizuku_unavailable", result.details)
        assertTrue(fake_executor.argv_calls.isEmpty())
    }

    @Test
    fun `returns success when all uninstall commands exit zero`() = runTest {
        val fake_executor = FakeCommandExecutor(
            execute_block = { _, _ ->
                PrivilegedCommandResult(
                    exit_code = 0,
                    stdout = "",
                    stderr = "",
                    timed_out = false
                )
            }
        )
        val step = UninstallAppsStep(command_executor = fake_executor)

        val result = step.execute(
            test_step_context(
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    uninstall_allowlist = listOf("com.example.first", "com.example.second")
                )
            )
        )

        assertEquals(StepStatus.SUCCESS, result.status)
        assertEquals(2, fake_executor.argv_calls.size)
        assertEquals(listOf("pm", "uninstall", "--user", "0", "com.example.first"), fake_executor.argv_calls[0])
        assertEquals(listOf("pm", "uninstall", "--user", "0", "com.example.second"), fake_executor.argv_calls[1])
    }

    @Test
    fun `returns failed when package name is invalid`() = runTest {
        val fake_executor = FakeCommandExecutor()
        val step = UninstallAppsStep(command_executor = fake_executor)

        val result = step.execute(
            test_step_context(
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    uninstall_allowlist = listOf("invalid package")
                )
            )
        )

        assertEquals(StepStatus.FAILED, result.status)
        assertTrue(fake_executor.argv_calls.isEmpty())
    }

    @Test
    fun `returns timed out when uninstall command exceeds timeout`() = runTest {
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
        val step = UninstallAppsStep(command_executor = fake_executor)

        val result = step.execute(
            test_step_context(
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    uninstall_allowlist = listOf("com.example.timeout")
                )
            )
        )

        assertEquals(StepStatus.TIMED_OUT, result.status)
        assertFalse(fake_executor.argv_calls.isEmpty())
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
