package com.yshalsager.mafza.core.execution

import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.EmergencyStep
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.RunStatus
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepResult
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TriggerSource
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultEmergencyEngineTest {
    @Test
    fun `duplicate trigger is ignored while active run exists`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val events = CopyOnWriteArrayList<EngineEvent>()

        val engine = DefaultEmergencyEngine(
            scope = CoroutineScope(dispatcher + Job()),
            profile_reader = { EmergencyProfile() },
            steps_provider = {
                listOf(
                    object : EmergencyStep {
                        override suspend fun execute(ctx: StepContext): StepResult {
                            delay(25)
                            return StepResult(
                                step_id = "dummy_step",
                                status = StepStatus.SUCCESS,
                                started_at_epoch_ms = 0L,
                                finished_at_epoch_ms = 0L
                            )
                        }
                    }
                )
            },
            cancel_window_millis_provider = { 10L },
            on_event = { events += it }
        )

        val first_run_id = engine.start(TriggerSource.SHORTCUT, ExecutionMode.LIVE)
        val second_run_id = engine.start(TriggerSource.WIDGET, ExecutionMode.LIVE)

        assertEquals(first_run_id, second_run_id)
        assertTrue(events.any { it is EngineEvent.IgnoredDuplicateTrigger })
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `cancel within window prevents step execution and completes as cancelled pre start`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val events = CopyOnWriteArrayList<EngineEvent>()
        var executed_steps = 0

        val engine = DefaultEmergencyEngine(
            scope = CoroutineScope(dispatcher + Job()),
            profile_reader = { EmergencyProfile() },
            steps_provider = {
                listOf(
                    object : EmergencyStep {
                        override suspend fun execute(ctx: StepContext): StepResult {
                            executed_steps += 1
                            return StepResult(
                                step_id = "should_not_execute",
                                status = StepStatus.SUCCESS,
                                started_at_epoch_ms = 0L,
                                finished_at_epoch_ms = 0L
                            )
                        }
                    }
                )
            },
            cancel_window_millis_provider = { 100L },
            on_event = { events += it }
        )

        val run_id = engine.start(TriggerSource.SHORTCUT, ExecutionMode.LIVE)
        val cancel_result = engine.cancelWithinWindow(run_id)
        testScheduler.advanceUntilIdle()

        assertTrue(cancel_result)
        assertEquals(0, executed_steps)

        val completed_event = events.filterIsInstance<EngineEvent.RunCompleted>().last()
        assertEquals(RunStatus.CANCELLED_PRE_START, completed_event.run_status)
        assertTrue(completed_event.step_statuses.contains(StepStatus.CANCELLED_PRE_START))
    }

    @Test
    fun `cancel outside window returns false`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val events = CopyOnWriteArrayList<EngineEvent>()

        val engine = DefaultEmergencyEngine(
            scope = CoroutineScope(dispatcher + Job()),
            profile_reader = { EmergencyProfile() },
            steps_provider = { emptyList() },
            cancel_window_millis_provider = { 10L },
            on_event = { events += it }
        )

        val run_id = engine.start(TriggerSource.SHORTCUT, ExecutionMode.LIVE)
        testScheduler.advanceTimeBy(11L)
        val cancel_result = engine.cancelWithinWindow(run_id)
        testScheduler.advanceUntilIdle()

        assertFalse(cancel_result)
        assertTrue(events.any { it is EngineEvent.RunCompleted })
    }

    @Test
    fun `cancel during profile snapshot load still completes as cancelled pre start`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val events = CopyOnWriteArrayList<EngineEvent>()

        val engine = DefaultEmergencyEngine(
            scope = CoroutineScope(dispatcher + Job()),
            profile_reader = {
                delay(1_000L)
                EmergencyProfile()
            },
            steps_provider = { emptyList() },
            cancel_window_millis_provider = { 100L },
            on_event = { events += it }
        )

        val run_id = engine.start(TriggerSource.SHORTCUT, ExecutionMode.LIVE)
        val cancel_result = engine.cancelWithinWindow(run_id)
        testScheduler.advanceUntilIdle()

        assertTrue(cancel_result)
        val completed_event = events.filterIsInstance<EngineEvent.RunCompleted>().last()
        assertEquals(RunStatus.CANCELLED_PRE_START, completed_event.run_status)
    }

    @Test
    fun `cancel window does not extend after slow profile load`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = DefaultEmergencyEngine(
            scope = CoroutineScope(dispatcher + Job()),
            profile_reader = {
                delay(1_000L)
                EmergencyProfile()
            },
            steps_provider = { emptyList() },
            cancel_window_millis_provider = { 100L },
            clock = { testScheduler.currentTime }
        )

        val run_id = engine.start(TriggerSource.SHORTCUT, ExecutionMode.LIVE)
        testScheduler.advanceTimeBy(1_001L)
        val cancel_result = engine.cancelWithinWindow(run_id)
        testScheduler.advanceUntilIdle()

        assertFalse(cancel_result)
    }

    @Test
    fun `step cancellation clears active run so next trigger can start`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val events = CopyOnWriteArrayList<EngineEvent>()

        val engine = DefaultEmergencyEngine(
            scope = CoroutineScope(dispatcher + Job()),
            profile_reader = { EmergencyProfile() },
            steps_provider = {
                listOf(
                    object : EmergencyStep {
                        override suspend fun execute(ctx: StepContext): StepResult {
                            throw CancellationException("simulated_step_cancel")
                        }
                    }
                )
            },
            cancel_window_millis_provider = { 1L },
            on_event = { events += it }
        )

        val first_run_id = engine.start(TriggerSource.SHORTCUT, ExecutionMode.LIVE)
        testScheduler.advanceUntilIdle()
        val first_completed_event = events.filterIsInstance<EngineEvent.RunCompleted>().first { it.run_id == first_run_id }
        val second_run_id = engine.start(TriggerSource.WIDGET, ExecutionMode.LIVE)

        assertEquals(RunStatus.COMPLETED_FAILED, first_completed_event.run_status)
        assertFalse(first_run_id == second_run_id && events.any { it is EngineEvent.IgnoredDuplicateTrigger })
    }

    @Test
    fun `external cancellation during cancel window does not execute steps`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val events = CopyOnWriteArrayList<EngineEvent>()
        val scope_job = Job()
        var executed_steps = 0

        val engine = DefaultEmergencyEngine(
            scope = CoroutineScope(dispatcher + scope_job),
            profile_reader = { EmergencyProfile() },
            steps_provider = {
                listOf(
                    object : EmergencyStep {
                        override suspend fun execute(ctx: StepContext): StepResult {
                            executed_steps += 1
                            return StepResult(
                                step_id = "unexpected_execution",
                                status = StepStatus.SUCCESS,
                                started_at_epoch_ms = 0L,
                                finished_at_epoch_ms = 0L
                            )
                        }
                    }
                )
            },
            cancel_window_millis_provider = { 5_000L },
            on_event = { events += it }
        )

        val first_run_id = engine.start(TriggerSource.SHORTCUT, ExecutionMode.LIVE)
        scope_job.cancel()
        testScheduler.advanceUntilIdle()
        assertEquals(0, executed_steps)
        assertTrue(events.any { event ->
            event is EngineEvent.RunStarted && event.run_id == first_run_id
        })
        assertFalse(events.any { event ->
            event is EngineEvent.StepCompleted && event.run_id == first_run_id
        })
        val completed_event = events.filterIsInstance<EngineEvent.RunCompleted>().first { it.run_id == first_run_id }
        assertEquals(RunStatus.COMPLETED_FAILED, completed_event.run_status)
    }
}
