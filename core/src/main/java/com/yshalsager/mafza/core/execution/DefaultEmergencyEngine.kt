package com.yshalsager.mafza.core.execution

import com.yshalsager.mafza.core.contracts.EmergencyEngine
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.EmergencyStep
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.RunId
import com.yshalsager.mafza.core.contracts.RunStatus
import com.yshalsager.mafza.core.contracts.RunStatusDeriver
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepResult
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TriggerSource
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
class DefaultEmergencyEngine(
    private val scope: CoroutineScope,
    private val profile_reader: suspend () -> EmergencyProfile,
    private val steps_provider: suspend (StepContext) -> List<EmergencyStep>,
    private val on_event: (EngineEvent) -> Unit = {},
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val run_id_factory: () -> RunId = { UUID.randomUUID().toString() },
    private val cancel_window_millis_provider: (EmergencyProfile) -> Long = { profile ->
        profile.cancel_window_seconds.coerceIn(1, 30).toLong() * 1_000L
    }
) : EmergencyEngine {
    private val state_lock = Any()
    private var active_run_state: ActiveRunState? = null

    override fun start(trigger: TriggerSource, mode: ExecutionMode): RunId {
        synchronized(state_lock) {
            val current_state = active_run_state
            if (current_state != null) {
                on_event(
                    EngineEvent.IgnoredDuplicateTrigger(
                        active_run_id = current_state.run_id,
                        requested_trigger = trigger,
                        requested_mode = mode
                    )
                )
                return current_state.run_id
            }

            val run_id = run_id_factory()
            val run_job = scope.launch(start = CoroutineStart.ATOMIC) { execute_run(run_id, trigger, mode) }
            active_run_state = ActiveRunState(
                run_id = run_id,
                job = run_job,
                cancel_window_open = true
            )
            return run_id
        }
    }

    override fun cancelWithinWindow(run_id: RunId): Boolean {
        synchronized(state_lock) {
            val current_state = active_run_state ?: return false
            if (current_state.run_id != run_id) return false
            if (!current_state.cancel_window_open) return false

            current_state.cancelled_pre_start = true
            current_state.job.cancel()
            return true
        }
    }

    private suspend fun execute_run(run_id: RunId, trigger: TriggerSource, mode: ExecutionMode) {
        val step_statuses = mutableListOf<StepStatus>()
        try {
            val started_at = clock()
            on_event(
                EngineEvent.RunStarted(
                    run_id = run_id,
                    trigger = trigger,
                    mode = mode,
                    started_at_epoch_ms = started_at
                )
            )

            val profile_snapshot = try {
                profile_reader()
            } catch (cancelled_exception: CancellationException) {
                if (is_cancelled_pre_start(run_id)) {
                    on_event(EngineEvent.RunCancelledPreStart(run_id))
                    complete_run(
                        run_id = run_id,
                        run_status = RunStatus.CANCELLED_PRE_START,
                        step_statuses = listOf(StepStatus.CANCELLED_PRE_START)
                    )
                    return
                }
                throw cancelled_exception
            } catch (_: Throwable) {
                complete_run(
                    run_id = run_id,
                    run_status = RunStatus.COMPLETED_FAILED,
                    step_statuses = emptyList()
                )
                return
            }
            val cancel_window_millis = cancel_window_millis_provider(profile_snapshot)
            val cancel_window_ends_at = started_at + cancel_window_millis
            val remaining_cancel_window_millis = (cancel_window_ends_at - clock()).coerceAtLeast(0L)

            set_cancel_window_state(run_id, is_open = true)
            on_event(
                EngineEvent.CancelWindowOpened(
                    run_id = run_id,
                    window_ends_at_epoch_ms = cancel_window_ends_at
                )
            )

            val cancelled_pre_start = try {
                wait_cancel_window(run_id, remaining_cancel_window_millis)
            } finally {
                set_cancel_window_state(run_id, is_open = false)
            }
            if (cancelled_pre_start) {
                on_event(EngineEvent.RunCancelledPreStart(run_id))
                complete_run(
                    run_id = run_id,
                    run_status = RunStatus.CANCELLED_PRE_START,
                    step_statuses = listOf(StepStatus.CANCELLED_PRE_START)
                )
                return
            }

            val step_context = StepContext(
                run_id = run_id,
                trigger = trigger,
                mode = mode,
                profile = profile_snapshot,
                started_at_epoch_ms = started_at
            )

            val steps = try {
                steps_provider(step_context)
            } catch (cancelled_exception: CancellationException) {
                throw cancelled_exception
            } catch (_: Throwable) {
                emptyList()
            }
            steps.forEach { step ->
                val step_start = clock()
                val step_result = try {
                    step.execute(step_context)
                } catch (cancelled_exception: CancellationException) {
                    throw cancelled_exception
                } catch (throwable: Throwable) {
                    StepResult(
                        step_id = step::class.simpleName ?: "unknown_step",
                        status = StepStatus.FAILED,
                        details = throwable.message,
                        started_at_epoch_ms = step_start,
                        finished_at_epoch_ms = clock()
                    )
                }

                step_statuses += step_result.status
                on_event(EngineEvent.StepCompleted(run_id = run_id, step_result = step_result))
            }

            val run_status = RunStatusDeriver.derive_run_status(
                is_running = false,
                cancelled_pre_start = false,
                step_statuses = step_statuses
            )
            complete_run(run_id = run_id, run_status = run_status, step_statuses = step_statuses)
        } catch (cancelled_exception: CancellationException) {
            if (is_cancelled_pre_start(run_id)) {
                on_event(EngineEvent.RunCancelledPreStart(run_id))
                complete_run(
                    run_id = run_id,
                    run_status = RunStatus.CANCELLED_PRE_START,
                    step_statuses = listOf(StepStatus.CANCELLED_PRE_START)
                )
                return
            }
            complete_run(
                run_id = run_id,
                run_status = RunStatus.COMPLETED_FAILED,
                step_statuses = step_statuses
            )
        } finally {
            clear_active_run(run_id)
        }
    }

    private suspend fun wait_cancel_window(run_id: RunId, cancel_window_millis: Long): Boolean {
        return try {
            delay(cancel_window_millis)
            false
        } catch (cancelled_exception: CancellationException) {
            if (is_cancelled_pre_start(run_id)) return true
            throw cancelled_exception
        }
    }

    private fun complete_run(run_id: RunId, run_status: RunStatus, step_statuses: List<StepStatus>) {
        val completed_at = clock()
        on_event(
            EngineEvent.RunCompleted(
                run_id = run_id,
                run_status = run_status,
                step_statuses = step_statuses,
                completed_at_epoch_ms = completed_at
            )
        )
    }

    private fun set_cancel_window_state(run_id: RunId, is_open: Boolean) {
        synchronized(state_lock) {
            val current_state = active_run_state ?: return
            if (current_state.run_id != run_id) return
            current_state.cancel_window_open = is_open
        }
    }

    private fun is_cancelled_pre_start(run_id: RunId): Boolean {
        synchronized(state_lock) {
            val current_state = active_run_state ?: return false
            if (current_state.run_id != run_id) return false
            return current_state.cancelled_pre_start
        }
    }

    private fun clear_active_run(run_id: RunId) {
        synchronized(state_lock) {
            val current_state = active_run_state ?: return
            if (current_state.run_id == run_id) active_run_state = null
        }
    }

    private data class ActiveRunState(
        val run_id: RunId,
        val job: Job,
        var cancel_window_open: Boolean = false,
        var cancelled_pre_start: Boolean = false
    )
}
