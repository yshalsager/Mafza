package com.yshalsager.mafza.emergency

import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.TriggerSource

internal class EmergencyRunStartPolicy(
    private val profile_reader: suspend () -> EmergencyProfile?,
    private val live_preflight_checker: suspend (EmergencyProfile) -> Boolean
) {
    suspend fun should_start(
        trigger: TriggerSource,
        requested_mode: ExecutionMode
    ): Boolean {
        if (trigger == TriggerSource.MANUAL_IN_APP) return true

        val profile = profile_reader() ?: return false
        if (!profile.triggers_enabled) return false
        if (requested_mode != ExecutionMode.LIVE) return true

        return live_preflight_checker(profile)
    }
}
