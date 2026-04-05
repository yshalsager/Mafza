package com.yshalsager.mafza.emergency.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class PrivilegedCommandResult(
    val exit_code: Int?,
    val stdout: String,
    val stderr: String,
    val timed_out: Boolean,
    val unavailable: Boolean = false,
    val failure_message: String? = null
)

interface PrivilegedCommandExecutor {
    fun is_available(): Boolean
    suspend fun execute_argv(
        argv: List<String>,
        timeout_seconds: Int
    ): PrivilegedCommandResult

    suspend fun execute_raw_shell(
        raw_shell: String,
        timeout_seconds: Int
    ): PrivilegedCommandResult {
        return execute_argv(
            argv = listOf("sh", "-c", raw_shell),
            timeout_seconds = timeout_seconds
        )
    }
}

class ShizukuCommandExecutor(
    private val app_context: Context,
    private val io_dispatcher: CoroutineDispatcher = Dispatchers.IO
) : PrivilegedCommandExecutor {
    private val user_service_args = Shizuku.UserServiceArgs(
        ComponentName(app_context.packageName, PrivilegedShellUserService::class.java.name)
    )
        .daemon(false)
        .tag("mafza_shell")
        .version(1)
        .processNameSuffix("mafza_shell")

    override fun is_available(): Boolean {
        return runCatching {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    override suspend fun execute_argv(
        argv: List<String>,
        timeout_seconds: Int
    ): PrivilegedCommandResult {
        if (argv.isEmpty()) {
            return PrivilegedCommandResult(
                exit_code = null,
                stdout = "",
                stderr = "",
                timed_out = false,
                unavailable = false,
                failure_message = "empty_argv"
            )
        }
        val timeout = timeout_seconds.coerceIn(1, 120)
        return execute_remote {
            it.execute_argv(argv.toTypedArray(), timeout)
        }
    }

    override suspend fun execute_raw_shell(
        raw_shell: String,
        timeout_seconds: Int
    ): PrivilegedCommandResult {
        if (raw_shell.isBlank()) {
            return PrivilegedCommandResult(
                exit_code = null,
                stdout = "",
                stderr = "",
                timed_out = false,
                unavailable = false,
                failure_message = "empty_raw_shell"
            )
        }
        val timeout = timeout_seconds.coerceIn(1, 120)
        return execute_remote {
            it.execute_raw_shell(raw_shell, timeout)
        }
    }

    private suspend fun execute_remote(
        remote_call: (IPrivilegedShellService) -> Bundle
    ): PrivilegedCommandResult = withContext(io_dispatcher) {
        if (!is_available()) {
            return@withContext PrivilegedCommandResult(
                exit_code = null,
                stdout = "",
                stderr = "",
                timed_out = false,
                unavailable = true,
                failure_message = "shizuku_unavailable"
            )
        }

        return@withContext suspendCancellableCoroutine { continuation ->
            var is_finished = false
            lateinit var connection: ServiceConnection

            fun finish(result: PrivilegedCommandResult) {
                if (is_finished) return
                is_finished = true
                runCatching { Shizuku.unbindUserService(user_service_args, connection, false) }
                if (continuation.isActive) continuation.resume(result)
            }

            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    Thread {
                        val result = runCatching {
                            val remote = IPrivilegedShellService.Stub.asInterface(service)
                            val bundle = remote_call(remote)
                            bundle_to_result(bundle)
                        }.getOrElse { throwable ->
                            PrivilegedCommandResult(
                                exit_code = null,
                                stdout = "",
                                stderr = "",
                                timed_out = false,
                                unavailable = false,
                                failure_message = throwable.message ?: "user_service_execution_failed"
                            )
                        }
                        finish(result)
                    }.start()
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    finish(
                        PrivilegedCommandResult(
                            exit_code = null,
                            stdout = "",
                            stderr = "",
                            timed_out = false,
                            unavailable = true,
                            failure_message = "user_service_disconnected"
                        )
                    )
                }
            }

            continuation.invokeOnCancellation {
                runCatching { Shizuku.unbindUserService(user_service_args, connection, false) }
            }

            runCatching { Shizuku.bindUserService(user_service_args, connection) }
                .onFailure { throwable ->
                    finish(
                        PrivilegedCommandResult(
                            exit_code = null,
                            stdout = "",
                            stderr = "",
                            timed_out = false,
                            unavailable = true,
                            failure_message = throwable.message ?: "bind_user_service_failed"
                        )
                    )
                }
        }
    }

    private fun bundle_to_result(bundle: Bundle?): PrivilegedCommandResult {
        if (bundle == null) {
            return PrivilegedCommandResult(
                exit_code = null,
                stdout = "",
                stderr = "",
                timed_out = false,
                unavailable = false,
                failure_message = "empty_service_response"
            )
        }
        val has_exit_code = bundle.getBoolean(PrivilegedShellContract.RESULT_KEY_HAS_EXIT_CODE, false)
        return PrivilegedCommandResult(
            exit_code = if (has_exit_code) bundle.getInt(PrivilegedShellContract.RESULT_KEY_EXIT_CODE) else null,
            stdout = bundle.getString(PrivilegedShellContract.RESULT_KEY_STDOUT).orEmpty(),
            stderr = bundle.getString(PrivilegedShellContract.RESULT_KEY_STDERR).orEmpty(),
            timed_out = bundle.getBoolean(PrivilegedShellContract.RESULT_KEY_TIMED_OUT, false),
            unavailable = false,
            failure_message = bundle.getString(PrivilegedShellContract.RESULT_KEY_FAILURE_MESSAGE)
        )
    }
}
