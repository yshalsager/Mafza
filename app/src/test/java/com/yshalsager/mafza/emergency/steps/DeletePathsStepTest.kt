package com.yshalsager.mafza.emergency.steps

import com.yshalsager.mafza.emergency.shell.PrivilegedCommandExecutor
import com.yshalsager.mafza.emergency.shell.PrivilegedCommandResult
import com.yshalsager.mafza.core.contracts.DeleteTarget
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TriggerSource
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletePathsStepTest {
    @Test
    fun `returns dry-run skip and does not execute commands`() = runTest {
        val fake_executor = FakeCommandExecutor()
        val step = DeletePathsStep(command_executor = fake_executor)

        val result = step.execute(
            test_step_context(
                mode = ExecutionMode.DRY_RUN,
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    delete_allowlist = listOf(DeleteTarget(path = "/tmp/mafza-file", recursive = false))
                )
            )
        )

        assertEquals(StepStatus.SKIPPED_DRY_RUN, result.status)
        assertTrue(fake_executor.argv_calls.isEmpty())
    }

    @Test
    fun `returns skipped unavailable when shizuku is unavailable`() = runTest {
        val fake_executor = FakeCommandExecutor(available = false)
        val step = DeletePathsStep(command_executor = fake_executor)

        val result = step.execute(
            test_step_context(
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    delete_allowlist = listOf(DeleteTarget(path = "/tmp/mafza-file", recursive = false))
                )
            )
        )

        assertEquals(StepStatus.SKIPPED_UNAVAILABLE, result.status)
        assertEquals("shizuku_unavailable", result.details)
    }

    @Test
    fun `returns failed when target path is invalid`() = runTest {
        val fake_executor = FakeCommandExecutor()
        val step = DeletePathsStep(command_executor = fake_executor)

        val result = step.execute(
            test_step_context(
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    delete_allowlist = listOf(DeleteTarget(path = "/", recursive = true))
                )
            )
        )

        assertEquals(StepStatus.FAILED, result.status)
        assertTrue(fake_executor.argv_calls.isEmpty())
    }

    @Test
    fun `uses rmdir for directory target when recursive is false`() = runTest {
        val temp_dir = create_temp_dir()
        val canonical_path = temp_dir.canonicalPath
        val fake_executor = FakeCommandExecutor()
        val step = DeletePathsStep(command_executor = fake_executor)

        val result = step.execute(
            test_step_context(
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    delete_allowlist = listOf(DeleteTarget(path = canonical_path, recursive = false))
                )
            )
        )

        assertEquals(StepStatus.SUCCESS, result.status)
        assertEquals(listOf("rmdir", "--", canonical_path), fake_executor.argv_calls.single())
        temp_dir.deleteRecursively()
    }

    @Test
    fun `falls back to rm f when rmdir fails for non recursive target`() = runTest {
        val temp_target = create_temp_file()
        val canonical_path = temp_target.canonicalPath
        val fake_executor = FakeCommandExecutor(
            execute_block = { argv, _ ->
                if (argv.firstOrNull() == "rmdir") {
                    return@FakeCommandExecutor PrivilegedCommandResult(
                        exit_code = 1,
                        stdout = "",
                        stderr = "not_a_directory",
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
        val step = DeletePathsStep(command_executor = fake_executor)

        val result = step.execute(
            test_step_context(
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    delete_allowlist = listOf(DeleteTarget(path = canonical_path, recursive = false))
                )
            )
        )

        assertEquals(StepStatus.SUCCESS, result.status)
        assertEquals(
            listOf(
                listOf("rmdir", "--", canonical_path),
                listOf("rm", "-f", "--", canonical_path)
            ),
            fake_executor.argv_calls
        )
        temp_target.delete()
    }

    @Test
    fun `uses rm recursive flags when recursive is true`() = runTest {
        val temp_dir = create_temp_dir()
        val canonical_path = temp_dir.canonicalPath
        val fake_executor = FakeCommandExecutor()
        val step = DeletePathsStep(command_executor = fake_executor)

        val result = step.execute(
            test_step_context(
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    delete_allowlist = listOf(DeleteTarget(path = canonical_path, recursive = true))
                )
            )
        )

        assertEquals(StepStatus.SUCCESS, result.status)
        assertEquals(listOf("rm", "-rf", "--", canonical_path), fake_executor.argv_calls.single())
        temp_dir.deleteRecursively()
    }

    @Test
    fun `returns timed out when delete command exceeds timeout`() = runTest {
        val temp_file = create_temp_file()
        val canonical_path = temp_file.canonicalPath
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
        val step = DeletePathsStep(command_executor = fake_executor)

        val result = step.execute(
            test_step_context(
                profile = EmergencyProfile(
                    destructive_actions_enabled = true,
                    delete_allowlist = listOf(DeleteTarget(path = canonical_path, recursive = false))
                )
            )
        )

        assertEquals(StepStatus.TIMED_OUT, result.status)
        assertFalse(fake_executor.argv_calls.isEmpty())
        temp_file.delete()
    }

    private fun create_temp_dir(): File {
        return Files.createTempDirectory("mafza-dir-").toFile().apply {
            deleteOnExit()
        }
    }

    private fun create_temp_file(): File {
        return File.createTempFile("mafza-file-", ".tmp").apply {
            deleteOnExit()
        }
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
