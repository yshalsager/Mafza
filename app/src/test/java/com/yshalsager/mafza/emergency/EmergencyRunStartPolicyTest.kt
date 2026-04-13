package com.yshalsager.mafza.emergency

import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.TriggerSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyRunStartPolicyTest {
    @Test
    fun `manual trigger bypasses profile and preflight gates`() = runTest {
        var profile_reads = 0
        var preflight_checks = 0
        val policy = EmergencyRunStartPolicy(
            profile_reader = {
                profile_reads += 1
                EmergencyProfile()
            },
            live_preflight_checker = {
                preflight_checks += 1
                false
            }
        )

        val should_start = policy.should_start(
            trigger = TriggerSource.MANUAL_IN_APP,
            requested_mode = ExecutionMode.LIVE
        )

        assertTrue(should_start)
        assertFalse(profile_reads > 0)
        assertFalse(preflight_checks > 0)
    }

    @Test
    fun `external trigger blocks when profile cannot be read`() = runTest {
        var preflight_checks = 0
        val policy = EmergencyRunStartPolicy(
            profile_reader = { null },
            live_preflight_checker = {
                preflight_checks += 1
                true
            }
        )

        val should_start = policy.should_start(
            trigger = TriggerSource.WIDGET,
            requested_mode = ExecutionMode.LIVE
        )

        assertFalse(should_start)
        assertFalse(preflight_checks > 0)
    }

    @Test
    fun `external trigger blocks when triggers are disabled`() = runTest {
        var preflight_checks = 0
        val policy = EmergencyRunStartPolicy(
            profile_reader = { EmergencyProfile(triggers_enabled = false) },
            live_preflight_checker = {
                preflight_checks += 1
                true
            }
        )

        val should_start = policy.should_start(
            trigger = TriggerSource.SHORTCUT,
            requested_mode = ExecutionMode.LIVE
        )

        assertFalse(should_start)
        assertFalse(preflight_checks > 0)
    }

    @Test
    fun `external live trigger runs preflight and blocks on failure`() = runTest {
        var preflight_checks = 0
        val policy = EmergencyRunStartPolicy(
            profile_reader = { EmergencyProfile(triggers_enabled = true) },
            live_preflight_checker = {
                preflight_checks += 1
                false
            }
        )

        val should_start = policy.should_start(
            trigger = TriggerSource.QUICK_SETTINGS_TILE,
            requested_mode = ExecutionMode.LIVE
        )

        assertFalse(should_start)
        assertTrue(preflight_checks == 1)
    }

    @Test
    fun `external dry-run trigger skips live preflight gate`() = runTest {
        var preflight_checks = 0
        val policy = EmergencyRunStartPolicy(
            profile_reader = { EmergencyProfile(triggers_enabled = true) },
            live_preflight_checker = {
                preflight_checks += 1
                false
            }
        )

        val should_start = policy.should_start(
            trigger = TriggerSource.WIDGET,
            requested_mode = ExecutionMode.DRY_RUN
        )

        assertTrue(should_start)
        assertFalse(preflight_checks > 0)
    }
}
