package com.yshalsager.mafza.emergency.shell

import android.os.Bundle
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class PrivilegedShellUserService : IPrivilegedShellService.Stub() {
    override fun execute_argv(argv: Array<String>, timeout_seconds: Int): Bundle {
        val command = argv.map(String::trim).filter { it.isNotEmpty() }
        if (command.isEmpty()) return failure_bundle("empty_argv")
        return execute_command(command, timeout_seconds)
    }

    override fun execute_raw_shell(raw_shell: String, timeout_seconds: Int): Bundle {
        val command = raw_shell.trim()
        if (command.isEmpty()) return failure_bundle("empty_raw_shell")
        return execute_command(listOf("sh", "-c", command), timeout_seconds)
    }

    private fun execute_command(
        argv: List<String>,
        timeout_seconds: Int
    ): Bundle {
        return runCatching {
            val process = ProcessBuilder(argv).start()
            val stdout_buffer = StringBuilder()
            val stderr_buffer = StringBuilder()
            val stdout_thread = stream_reader_thread(process.inputStream, stdout_buffer)
            val stderr_thread = stream_reader_thread(process.errorStream, stderr_buffer)

            val did_finish = process.waitFor(timeout_seconds.coerceIn(1, 120).toLong(), TimeUnit.SECONDS)
            if (!did_finish) {
                process.destroy()
                if (process.isAlive) process.destroyForcibly()
            }

            stdout_thread.join(250L)
            stderr_thread.join(250L)

            val exit_code = if (did_finish) process.exitValue() else null
            result_bundle(
                exit_code = exit_code,
                stdout = stdout_buffer.toString(),
                stderr = stderr_buffer.toString(),
                timed_out = !did_finish
            )
        }.getOrElse { throwable ->
            failure_bundle(throwable.message ?: "command_execution_failed")
        }
    }

    private fun stream_reader_thread(
        input_stream: java.io.InputStream,
        output_buffer: StringBuilder
    ): Thread {
        return thread(start = true) {
            runCatching {
                input_stream.bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        output_buffer.append(line).append('\n')
                    }
                }
            }
        }
    }

    private fun result_bundle(
        exit_code: Int?,
        stdout: String,
        stderr: String,
        timed_out: Boolean
    ): Bundle {
        return Bundle().apply {
            putBoolean(PrivilegedShellContract.RESULT_KEY_HAS_EXIT_CODE, exit_code != null)
            if (exit_code != null) putInt(PrivilegedShellContract.RESULT_KEY_EXIT_CODE, exit_code)
            putString(PrivilegedShellContract.RESULT_KEY_STDOUT, stdout)
            putString(PrivilegedShellContract.RESULT_KEY_STDERR, stderr)
            putBoolean(PrivilegedShellContract.RESULT_KEY_TIMED_OUT, timed_out)
        }
    }

    private fun failure_bundle(message: String): Bundle {
        return Bundle().apply {
            putBoolean(PrivilegedShellContract.RESULT_KEY_HAS_EXIT_CODE, false)
            putString(PrivilegedShellContract.RESULT_KEY_STDOUT, "")
            putString(PrivilegedShellContract.RESULT_KEY_STDERR, "")
            putBoolean(PrivilegedShellContract.RESULT_KEY_TIMED_OUT, false)
            putString(PrivilegedShellContract.RESULT_KEY_FAILURE_MESSAGE, message)
        }
    }
}
