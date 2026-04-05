package com.yshalsager.mafza.emergency.steps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.yshalsager.mafza.core.contracts.EmergencyStep
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepResult
import com.yshalsager.mafza.core.contracts.StepStatus
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class LocationStep(
    private val app_context: Context?,
    private val run_step_state: RunStepState,
    private val has_location_permission_checker: (() -> Boolean)? = null,
    private val provider_candidates_resolver: (() -> List<String>)? = null,
    private val provider_resolver: (() -> String?)? = null,
    private val location_reader: (suspend (String) -> LocationSnapshot?)? = null,
    private val now_provider: () -> Long = { System.currentTimeMillis() }
) : EmergencyStep {
    override suspend fun execute(ctx: StepContext): StepResult {
        val started_at = now_provider()
        if (ctx.mode == ExecutionMode.DRY_RUN) {
            return StepResult(
                step_id = STEP_ID,
                status = StepStatus.SKIPPED_DRY_RUN,
                details = "dry_run_location",
                started_at_epoch_ms = started_at,
                finished_at_epoch_ms = now_provider()
            )
        }

        if (!has_location_permission()) {
            return StepResult(
                step_id = STEP_ID,
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "missing_location_permission",
                started_at_epoch_ms = started_at,
                finished_at_epoch_ms = now_provider()
            )
        }

        val providers = resolve_providers()
        if (providers.isEmpty()) {
            return StepResult(
                step_id = STEP_ID,
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "no_location_provider_enabled",
                started_at_epoch_ms = started_at,
                finished_at_epoch_ms = now_provider()
            )
        }

        val timeout_seconds = ctx.profile.location_timeout_seconds.coerceIn(1, 60)
        val timeout_millis = timeout_seconds * 1_000L
        val location_outcome = withTimeoutOrNull(timeout_millis) {
            LocationReadOutcome(location_snapshot = read_current_location(providers))
        }
        val finished_at = now_provider()
        if (location_outcome == null) {
            return StepResult(
                step_id = STEP_ID,
                status = StepStatus.TIMED_OUT,
                details = "location_timeout_${timeout_seconds}s",
                started_at_epoch_ms = started_at,
                finished_at_epoch_ms = finished_at
            )
        }
        val location_snapshot = location_outcome.location_snapshot
        if (location_snapshot == null) {
            return StepResult(
                step_id = STEP_ID,
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "location_unavailable",
                started_at_epoch_ms = started_at,
                finished_at_epoch_ms = finished_at
            )
        }

        run_step_state.set_location(location_snapshot)
        return StepResult(
            step_id = STEP_ID,
            status = StepStatus.SUCCESS,
            details = "lat=${location_snapshot.latitude},lon=${location_snapshot.longitude}",
            started_at_epoch_ms = started_at,
            finished_at_epoch_ms = finished_at
        )
    }

    private fun has_location_permission(): Boolean {
        val override_checker = has_location_permission_checker
        if (override_checker != null) return override_checker()

        val context = require_app_context()
        val has_fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val has_coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return has_fine || has_coarse
    }

    private fun resolve_providers(): List<String> {
        val override_candidates_resolver = provider_candidates_resolver
        if (override_candidates_resolver != null) {
            return override_candidates_resolver()
                .map(String::trim)
                .filter { it.isNotEmpty() }
                .distinct()
        }

        val override_resolver = provider_resolver
        if (override_resolver != null) return listOfNotNull(override_resolver())

        val context = require_app_context()
        val location_manager = context.getSystemService(LocationManager::class.java)
        val has_fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val providers = if (has_fine) {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        } else {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
        }
        return providers.filter { provider ->
            runCatching { location_manager.isProviderEnabled(provider) }.getOrDefault(false)
        }
    }

    private suspend fun read_current_location(providers: List<String>): LocationSnapshot? {
        val override_reader = location_reader
        if (override_reader != null) {
            providers.forEach { provider ->
                val location_snapshot = override_reader(provider)
                if (location_snapshot != null) return location_snapshot
            }
            return null
        }

        val context = require_app_context()
        val location_manager = context.getSystemService(LocationManager::class.java)
        providers.forEach { provider ->
            val location = read_platform_location(
                app_context = context,
                location_manager = location_manager,
                provider = provider
            ) ?: return@forEach
            return LocationSnapshot(
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else null,
                accuracy_meters = if (location.hasAccuracy()) location.accuracy else null
            )
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun read_platform_location(
        app_context: Context,
        location_manager: LocationManager,
        provider: String
    ): Location? = suspendCancellableCoroutine { continuation ->
        val cancellation_signal = CancellationSignal()
        continuation.invokeOnCancellation { cancellation_signal.cancel() }

        runCatching {
            location_manager.getCurrentLocation(
                provider,
                cancellation_signal,
                app_context.mainExecutor
            ) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        }.onFailure {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    private fun require_app_context(): Context {
        return requireNotNull(app_context) {
            "LocationStep requires app_context when no test overrides are provided"
        }
    }

    companion object {
        private const val STEP_ID = "location"
    }

    private data class LocationReadOutcome(
        val location_snapshot: LocationSnapshot?
    )
}
