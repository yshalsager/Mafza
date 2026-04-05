package com.yshalsager.mafza.emergency.steps

import java.util.concurrent.atomic.AtomicReference

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracy_meters: Float?
)

class RunStepState {
    private val location_ref = AtomicReference<LocationSnapshot?>(null)

    fun set_location(location_snapshot: LocationSnapshot) {
        location_ref.set(location_snapshot)
    }

    fun get_location(): LocationSnapshot? = location_ref.get()
}
