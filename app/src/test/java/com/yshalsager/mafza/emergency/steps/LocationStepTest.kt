package com.yshalsager.mafza.emergency.steps

import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TriggerSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationStepTest {
    @Test
    fun `returns dry-run skip and does not evaluate permissions`() = runTest {
        var permission_checked = false
        val location_step = LocationStep(
            app_context = null,
            run_step_state = RunStepState(),
            has_location_permission_checker = {
                permission_checked = true
                true
            }
        )

        val result = location_step.execute(test_step_context(mode = ExecutionMode.DRY_RUN))
        assertEquals(StepStatus.SKIPPED_DRY_RUN, result.status)
        assertEquals("dry_run_location", result.details)
        assertFalse(permission_checked)
    }

    @Test
    fun `returns skipped unavailable when location permission is missing`() = runTest {
        val run_step_state = RunStepState()
        val location_step = LocationStep(
            app_context = null,
            run_step_state = run_step_state,
            has_location_permission_checker = { false }
        )

        val result = location_step.execute(test_step_context())
        assertEquals(StepStatus.SKIPPED_UNAVAILABLE, result.status)
        assertEquals("missing_location_permission", result.details)
        assertNull(run_step_state.get_location())
    }

    @Test
    fun `returns skipped unavailable when no provider is enabled`() = runTest {
        val location_step = LocationStep(
            app_context = null,
            run_step_state = RunStepState(),
            has_location_permission_checker = { true },
            provider_resolver = { null }
        )

        val result = location_step.execute(test_step_context())
        assertEquals(StepStatus.SKIPPED_UNAVAILABLE, result.status)
        assertEquals("no_location_provider_enabled", result.details)
    }

    @Test
    fun `returns timeout when location read exceeds configured timeout`() = runTest {
        val location_step = LocationStep(
            app_context = null,
            run_step_state = RunStepState(),
            has_location_permission_checker = { true },
            provider_resolver = { "gps" },
            location_reader = {
                delay(2_000L)
                null
            }
        )

        val result = location_step.execute(
            test_step_context(
                profile = EmergencyProfile(location_timeout_seconds = 1)
            )
        )
        assertEquals(StepStatus.TIMED_OUT, result.status)
    }

    @Test
    fun `returns success and stores snapshot when location read succeeds`() = runTest {
        val run_step_state = RunStepState()
        val location_step = LocationStep(
            app_context = null,
            run_step_state = run_step_state,
            has_location_permission_checker = { true },
            provider_resolver = { "gps" },
            location_reader = {
                LocationSnapshot(
                    latitude = 30.0444,
                    longitude = 31.2357,
                    altitude = 10.0,
                    accuracy_meters = 4.5f
                )
            }
        )

        val result = location_step.execute(test_step_context())
        assertEquals(StepStatus.SUCCESS, result.status)
        assertNotNull(run_step_state.get_location())
        assertEquals(30.0444, run_step_state.get_location()?.latitude ?: 0.0, 0.0001)
    }

    @Test
    fun `falls back to next provider when first provider returns unavailable`() = runTest {
        val attempted_providers = mutableListOf<String>()
        val run_step_state = RunStepState()
        val location_step = LocationStep(
            app_context = null,
            run_step_state = run_step_state,
            has_location_permission_checker = { true },
            provider_candidates_resolver = { listOf("gps", "network") },
            location_reader = { provider ->
                attempted_providers += provider
                if (provider == "gps") return@LocationStep null
                LocationSnapshot(
                    latitude = 51.5074,
                    longitude = -0.1278,
                    altitude = null,
                    accuracy_meters = 12.0f
                )
            }
        )

        val result = location_step.execute(test_step_context())
        assertEquals(StepStatus.SUCCESS, result.status)
        assertEquals(listOf("gps", "network"), attempted_providers)
        assertNotNull(run_step_state.get_location())
    }

    @Test
    fun `includes cell snapshot and details when phone state permission is granted`() = runTest {
        val run_step_state = RunStepState()
        val location_step = LocationStep(
            app_context = null,
            run_step_state = run_step_state,
            has_location_permission_checker = { true },
            has_phone_state_permission_checker = { true },
            provider_resolver = { "gps" },
            location_reader = {
                LocationSnapshot(
                    latitude = 24.7136,
                    longitude = 46.6753,
                    altitude = null,
                    accuracy_meters = 6.0f
                )
            },
            cell_snapshot_reader = {
                CellSnapshot(
                    cell_id = "12345",
                    radio_type = "lte",
                    area_code = "404",
                    pci = 321
                )
            }
        )

        val result = location_step.execute(test_step_context())
        assertEquals(StepStatus.SUCCESS, result.status)
        assertTrue(result.details?.contains("cell_id=12345") == true)
        assertTrue(result.details?.contains("cell_radio=lte") == true)
        assertEquals("12345", run_step_state.get_location()?.cell_snapshot?.cell_id)
    }

    @Test
    fun `keeps location success when phone state permission is missing`() = runTest {
        val run_step_state = RunStepState()
        val location_step = LocationStep(
            app_context = null,
            run_step_state = run_step_state,
            has_location_permission_checker = { true },
            has_phone_state_permission_checker = { false },
            provider_resolver = { "network" },
            location_reader = {
                LocationSnapshot(
                    latitude = 40.7128,
                    longitude = -74.0060,
                    altitude = null,
                    accuracy_meters = 10.0f
                )
            },
            cell_snapshot_reader = {
                CellSnapshot(
                    cell_id = "should_not_be_used",
                    radio_type = "nr",
                    area_code = "11",
                    pci = 5
                )
            }
        )

        val result = location_step.execute(test_step_context())
        assertEquals(StepStatus.SUCCESS, result.status)
        assertTrue(result.details?.contains("cell_id=") == false)
        assertNull(run_step_state.get_location()?.cell_snapshot)
    }

    @Test
    fun `uses OpenCellID lookup when platform location is unavailable`() = runTest {
        val run_step_state = RunStepState()
        var lookup_invocations = 0
        val location_step = LocationStep(
            app_context = null,
            run_step_state = run_step_state,
            has_location_permission_checker = { true },
            has_phone_state_permission_checker = { true },
            provider_resolver = { "network" },
            location_reader = { null },
            cell_snapshot_reader = {
                CellSnapshot(
                    cell_id = "170402199",
                    radio_type = "lte",
                    area_code = "35632",
                    pci = 321,
                    mcc = "310",
                    mnc = "410"
                )
            },
            cell_lookup_reader = { api_key, snapshot ->
                lookup_invocations += 1
                assertEquals("test_key", api_key)
                assertEquals("310", snapshot.mcc)
                assertEquals("410", snapshot.mnc)
                LocationSnapshot(
                    latitude = 29.3759,
                    longitude = 47.9774,
                    altitude = null,
                    accuracy_meters = 850.0f
                )
            }
        )

        val result = location_step.execute(
            test_step_context(
                profile = EmergencyProfile(opencellid_api_key = "test_key")
            )
        )
        assertEquals(StepStatus.SUCCESS, result.status)
        assertEquals(1, lookup_invocations)
        assertEquals(29.3759, run_step_state.get_location()?.latitude ?: 0.0, 0.0001)
        assertEquals(850.0f, run_step_state.get_location()?.accuracy_meters ?: 0.0f, 0.0001f)
    }

    @Test
    fun `uses OpenCellID lookup when no location provider is enabled`() = runTest {
        val run_step_state = RunStepState()
        var lookup_invocations = 0
        val location_step = LocationStep(
            app_context = null,
            run_step_state = run_step_state,
            has_location_permission_checker = { true },
            has_phone_state_permission_checker = { true },
            provider_candidates_resolver = { emptyList() },
            cell_snapshot_reader = {
                CellSnapshot(
                    cell_id = "170402199",
                    radio_type = "lte",
                    area_code = "35632",
                    pci = 321,
                    mcc = "310",
                    mnc = "410"
                )
            },
            cell_lookup_reader = { api_key, _ ->
                lookup_invocations += 1
                assertEquals("test_key", api_key)
                LocationSnapshot(
                    latitude = 35.6895,
                    longitude = 139.6917,
                    altitude = null,
                    accuracy_meters = 600.0f
                )
            }
        )

        val result = location_step.execute(
            test_step_context(
                profile = EmergencyProfile(opencellid_api_key = "test_key")
            )
        )
        assertEquals(StepStatus.SUCCESS, result.status)
        assertEquals(1, lookup_invocations)
        assertEquals(35.6895, run_step_state.get_location()?.latitude ?: 0.0, 0.0001)
    }

    @Test
    fun `skips OpenCellID lookup when API key is missing`() = runTest {
        val run_step_state = RunStepState()
        var lookup_invocations = 0
        val location_step = LocationStep(
            app_context = null,
            run_step_state = run_step_state,
            has_location_permission_checker = { true },
            has_phone_state_permission_checker = { true },
            provider_resolver = { "network" },
            location_reader = { null },
            cell_snapshot_reader = {
                CellSnapshot(
                    cell_id = "170402199",
                    radio_type = "lte",
                    area_code = "35632",
                    pci = 321,
                    mcc = "310",
                    mnc = "410"
                )
            },
            cell_lookup_reader = { _, _ ->
                lookup_invocations += 1
                null
            }
        )

        val result = location_step.execute(test_step_context(profile = EmergencyProfile(opencellid_api_key = "")))
        assertEquals(StepStatus.SKIPPED_UNAVAILABLE, result.status)
        assertEquals("location_unavailable", result.details)
        assertEquals(0, lookup_invocations)
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
}
