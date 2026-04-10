package com.yshalsager.mafza.core.contracts

import org.junit.Assert.assertEquals
import org.junit.Test

class RunStatusDeriverTest {
    @Test
    fun `returns RUNNING when run is active`() {
        val run_status = RunStatusDeriver.derive_run_status(
            is_running = true,
            cancelled_pre_start = false,
            step_statuses = listOf(StepStatus.SUCCESS)
        )

        assertEquals(RunStatus.RUNNING, run_status)
    }

    @Test
    fun `returns CANCELLED_PRE_START when cancelled in window`() {
        val run_status = RunStatusDeriver.derive_run_status(
            is_running = false,
            cancelled_pre_start = true,
            step_statuses = emptyList()
        )

        assertEquals(RunStatus.CANCELLED_PRE_START, run_status)
    }

    @Test
    fun `returns COMPLETED_SUCCESS when all steps are success`() {
        val run_status = RunStatusDeriver.derive_run_status(
            is_running = false,
            cancelled_pre_start = false,
            step_statuses = listOf(StepStatus.SUCCESS, StepStatus.SUCCESS)
        )

        assertEquals(RunStatus.COMPLETED_SUCCESS, run_status)
    }

    @Test
    fun `returns COMPLETED_PARTIAL when mixed success and non-success`() {
        val run_status = RunStatusDeriver.derive_run_status(
            is_running = false,
            cancelled_pre_start = false,
            step_statuses = listOf(StepStatus.SUCCESS, StepStatus.SKIPPED_UNAVAILABLE, StepStatus.FAILED)
        )

        assertEquals(RunStatus.COMPLETED_PARTIAL, run_status)
    }

    @Test
    fun `returns COMPLETED_FAILED when no success and has hard failure`() {
        val run_status = RunStatusDeriver.derive_run_status(
            is_running = false,
            cancelled_pre_start = false,
            step_statuses = listOf(StepStatus.TIMED_OUT, StepStatus.FAILED)
        )

        assertEquals(RunStatus.COMPLETED_FAILED, run_status)
    }

    @Test
    fun `returns COMPLETED_SUCCESS when all outcomes are non-failing skips`() {
        val run_status = RunStatusDeriver.derive_run_status(
            is_running = false,
            cancelled_pre_start = false,
            step_statuses = listOf(StepStatus.SKIPPED_DRY_RUN, StepStatus.SKIPPED_UNAVAILABLE)
        )

        assertEquals(RunStatus.COMPLETED_SUCCESS, run_status)
    }

    @Test
    fun `returns COMPLETED_FAILED when no steps executed`() {
        val run_status = RunStatusDeriver.derive_run_status(
            is_running = false,
            cancelled_pre_start = false,
            step_statuses = emptyList()
        )

        assertEquals(RunStatus.COMPLETED_FAILED, run_status)
    }
}
