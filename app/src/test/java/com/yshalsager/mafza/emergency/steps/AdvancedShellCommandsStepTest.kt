package com.yshalsager.mafza.emergency.steps

import com.yshalsager.mafza.emergency.shell.PrivilegedCommandExecutor
import com.yshalsager.mafza.emergency.shell.PrivilegedCommandResult
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.ShellCommandSpec
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TriggerSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedShellCommandsStepTest {
    @Test
    fun `returns dry-run skip and does not execute commands`() = runTest {
        val fake_executor = FakeCommandExecutor()
        val step = AdvancedShellCommandsStep(command_executor = fake_executor)

        val result = step.execute(
            test_step_context(
                mode = ExecutionMode.DRY_RUN,
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    advanced_shell_commands = listOf(
                        safe_command(id = "cmd_1", argv = listOf("echo", "hello"))
                    )
                )
            )
        )

        assertEquals(StepStatus.SKIPPED_DRY_RUN, result.status)
        assertTrue(fake_executor.argv_calls.isEmpty())
    }

    @Test
    fun `executes argv-safe command by default`() = runTest {
        val fake_executor = FakeCommandExecutor()
        val step = AdvancedShellCommandsStep(command_executor = fake_executor)

        val result = step.execute(
            test_step_context(
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    advanced_shell_commands = listOf(
                        safe_command(id = "cmd_1", argv = listOf("echo", "hello"))
                    )
                )
            )
        )

        assertEquals(StepStatus.SUCCESS, result.status)
        assertEquals(listOf(listOf("echo", "hello")), fake_executor.argv_calls)
        assertTrue(fake_executor.raw_shell_calls.isEmpty())
    }

    @Test
    fun `executes raw-shell command when provided`() = runTest {
        val fake_executor = FakeCommandExecutor()
        val step = AdvancedShellCommandsStep(command_executor = fake_executor)

        val result = step.execute(
            test_step_context(
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    advanced_shell_commands = listOf(
                        raw_shell_command(id = "cmd_1", raw_shell = "echo hello")
                    )
                )
            )
        )

        assertEquals(StepStatus.SUCCESS, result.status)
        assertEquals(listOf("echo hello"), fake_executor.raw_shell_calls)
        assertTrue(fake_executor.argv_calls.isEmpty())
    }

    @Test
    fun `returns failed when safe command argv is empty`() = runTest {
        val fake_executor = FakeCommandExecutor()
        val step = AdvancedShellCommandsStep(command_executor = fake_executor)

        val result = step.execute(
            test_step_context(
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    advanced_shell_commands = listOf(
                        safe_command(id = "cmd_1", argv = emptyList())
                    )
                )
            )
        )

        assertEquals(StepStatus.FAILED, result.status)
        assertTrue(fake_executor.argv_calls.isEmpty())
    }

    @Test
    fun `respects per-command continue_on_failure`() = runTest {
        val fake_executor = FakeCommandExecutor(
            execute_argv_block = { argv, _ ->
                if (argv.firstOrNull() == "bad") {
                    return@FakeCommandExecutor PrivilegedCommandResult(
                        exit_code = 1,
                        stdout = "",
                        stderr = "fail",
                        timed_out = false
                    )
                }
                return@FakeCommandExecutor PrivilegedCommandResult(
                    exit_code = 0,
                    stdout = "",
                    stderr = "",
                    timed_out = false
                )
            }
        )
        val step = AdvancedShellCommandsStep(command_executor = fake_executor)

        val result = step.execute(
            test_step_context(
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    advanced_shell_commands = listOf(
                        safe_command(id = "cmd_1", argv = listOf("bad"), continue_on_failure = false),
                        safe_command(id = "cmd_2", argv = listOf("echo", "will_not_run"))
                    )
                )
            )
        )

        assertEquals(StepStatus.FAILED, result.status)
        assertEquals(1, fake_executor.argv_calls.size)
    }

    @Test
    fun `returns timed out when command exceeds per-command timeout`() = runTest {
        val fake_executor = FakeCommandExecutor(
            execute_argv_block = { _, _ ->
                delay(2_000L)
                PrivilegedCommandResult(
                    exit_code = 0,
                    stdout = "",
                    stderr = "",
                    timed_out = false
                )
            }
        )
        val step = AdvancedShellCommandsStep(command_executor = fake_executor)

        val result = step.execute(
            test_step_context(
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    advanced_shell_commands = listOf(
                        safe_command(id = "cmd_1", argv = listOf("echo", "timeout"), timeout_seconds = 1)
                    )
                )
            )
        )

        assertEquals(StepStatus.TIMED_OUT, result.status)
    }

    private fun safe_command(
        id: String,
        argv: List<String>,
        timeout_seconds: Int = 10,
        continue_on_failure: Boolean = true,
        enabled: Boolean = true
    ): ShellCommandSpec {
        return ShellCommandSpec(
            id = id,
            label = id,
            argv = argv,
            raw_shell = null,
            timeout_seconds = timeout_seconds,
            continue_on_failure = continue_on_failure,
            enabled = enabled
        )
    }

    private fun raw_shell_command(
        id: String,
        raw_shell: String,
        timeout_seconds: Int = 10,
        continue_on_failure: Boolean = true,
        enabled: Boolean = true
    ): ShellCommandSpec {
        return ShellCommandSpec(
            id = id,
            label = id,
            argv = emptyList(),
            raw_shell = raw_shell,
            timeout_seconds = timeout_seconds,
            continue_on_failure = continue_on_failure,
            enabled = enabled
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

    private class FakeCommandExecutor(
        private val available: Boolean = true,
        private val execute_argv_block: suspend (argv: List<String>, timeout_seconds: Int) -> PrivilegedCommandResult = { _, _ ->
            PrivilegedCommandResult(
                exit_code = 0,
                stdout = "",
                stderr = "",
                timed_out = false
            )
        },
        private val execute_raw_shell_block: suspend (raw_shell: String, timeout_seconds: Int) -> PrivilegedCommandResult = { _, _ ->
            PrivilegedCommandResult(
                exit_code = 0,
                stdout = "",
                stderr = "",
                timed_out = false
            )
        }
    ) : PrivilegedCommandExecutor {
        val argv_calls = mutableListOf<List<String>>()
        val raw_shell_calls = mutableListOf<String>()

        override fun is_available(): Boolean = available

        override suspend fun execute_argv(argv: List<String>, timeout_seconds: Int): PrivilegedCommandResult {
            argv_calls += argv
            return execute_argv_block(argv, timeout_seconds)
        }

        override suspend fun execute_raw_shell(raw_shell: String, timeout_seconds: Int): PrivilegedCommandResult {
            raw_shell_calls += raw_shell
            return execute_raw_shell_block(raw_shell, timeout_seconds)
        }
    }
}
