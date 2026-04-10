package com.yshalsager.mafza.emergency.steps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.yshalsager.mafza.emergency.location.OpenCellIdLookupClient
import com.yshalsager.mafza.emergency.location.RealOpenCellIdLookupClient
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
    private val has_phone_state_permission_checker: (() -> Boolean)? = null,
    private val provider_candidates_resolver: (() -> List<String>)? = null,
    private val provider_resolver: (() -> String?)? = null,
    private val location_reader: (suspend (String) -> LocationSnapshot?)? = null,
    private val cell_snapshot_reader: (() -> CellSnapshot?)? = null,
    private val open_cell_lookup_client: OpenCellIdLookupClient = RealOpenCellIdLookupClient(),
    private val cell_lookup_reader: (suspend (String, CellSnapshot) -> LocationSnapshot?)? = null,
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
            val location_snapshot = read_current_location(providers)
            val cell_snapshot = read_cell_snapshot()
            val resolved_location = location_snapshot
                ?: read_location_from_cell_lookup(
                    opencellid_api_key = ctx.profile.opencellid_api_key,
                    cell_snapshot = cell_snapshot,
                    timeout_seconds = timeout_seconds
                )
            LocationReadOutcome(
                location_snapshot = resolved_location,
                cell_snapshot = cell_snapshot
            )
        }
        if (location_outcome == null) {
            val finished_at = now_provider()
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
            val finished_at = now_provider()
            return StepResult(
                step_id = STEP_ID,
                status = StepStatus.SKIPPED_UNAVAILABLE,
                details = "location_unavailable",
                started_at_epoch_ms = started_at,
                finished_at_epoch_ms = finished_at
            )
        }

        val location_with_cell = location_snapshot.copy(cell_snapshot = location_outcome.cell_snapshot)
        run_step_state.set_location(location_with_cell)
        val finished_at = now_provider()
        return StepResult(
            step_id = STEP_ID,
            status = StepStatus.SUCCESS,
            details = details_for(location_with_cell),
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

    private fun has_phone_state_permission(): Boolean {
        val override_checker = has_phone_state_permission_checker
        if (override_checker != null) return override_checker()

        val context = app_context ?: return false
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
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
    private fun read_cell_snapshot(): CellSnapshot? {
        if (!has_phone_state_permission()) return null

        val override_reader = cell_snapshot_reader
        if (override_reader != null) return override_reader()

        val context = require_app_context()
        val telephony_manager = context.getSystemService(TelephonyManager::class.java) ?: return null
        val all_cell_info = runCatching { telephony_manager.allCellInfo }.getOrNull().orEmpty()
        if (all_cell_info.isEmpty()) return null

        val selected_cell = all_cell_info.firstOrNull { it.isRegistered } ?: all_cell_info.firstOrNull() ?: return null
        return selected_cell.to_cell_snapshot()
    }

    private fun details_for(location_snapshot: LocationSnapshot): String {
        val parts = mutableListOf(
            "lat=${location_snapshot.latitude}",
            "lon=${location_snapshot.longitude}"
        )
        location_snapshot.cell_snapshot?.cell_id?.takeIf { it.isNotBlank() }?.let { parts += "cell_id=$it" }
        location_snapshot.cell_snapshot?.radio_type?.takeIf { it.isNotBlank() }?.let { parts += "cell_radio=$it" }
        location_snapshot.cell_snapshot?.area_code?.takeIf { it.isNotBlank() }?.let { parts += "cell_area=$it" }
        location_snapshot.cell_snapshot?.pci?.let { parts += "cell_pci=$it" }
        location_snapshot.cell_snapshot?.mcc?.takeIf { it.isNotBlank() }?.let { parts += "cell_mcc=$it" }
        location_snapshot.cell_snapshot?.mnc?.takeIf { it.isNotBlank() }?.let { parts += "cell_mnc=$it" }
        location_snapshot.accuracy_meters?.let { parts += "accuracy=$it" }
        return parts.joinToString(",")
    }

    private fun CellInfo.to_cell_snapshot(): CellSnapshot? {
        return when (this) {
            is CellInfoGsm -> CellSnapshot(
                cell_id = int_or_null(cellIdentity.cid)?.toString(),
                radio_type = "gsm",
                area_code = int_or_null(cellIdentity.lac)?.toString(),
                pci = null,
                mcc = normalize_plmn(cellIdentity.mccString),
                mnc = normalize_plmn(cellIdentity.mncString)
            )

            is CellInfoLte -> CellSnapshot(
                cell_id = int_or_null(cellIdentity.ci)?.toString(),
                radio_type = "lte",
                area_code = int_or_null(cellIdentity.tac)?.toString(),
                pci = int_or_null(cellIdentity.pci),
                mcc = normalize_plmn(cellIdentity.mccString),
                mnc = normalize_plmn(cellIdentity.mncString)
            )

            is CellInfoWcdma -> CellSnapshot(
                cell_id = int_or_null(cellIdentity.cid)?.toString(),
                radio_type = "wcdma",
                area_code = int_or_null(cellIdentity.lac)?.toString(),
                pci = int_or_null(cellIdentity.psc),
                mcc = normalize_plmn(cellIdentity.mccString),
                mnc = normalize_plmn(cellIdentity.mncString)
            )

            is CellInfoTdscdma -> CellSnapshot(
                cell_id = int_or_null(cellIdentity.cid)?.toString(),
                radio_type = "tdscdma",
                area_code = int_or_null(cellIdentity.lac)?.toString(),
                pci = int_or_null(cellIdentity.cpid),
                mcc = normalize_plmn(cellIdentity.mccString),
                mnc = normalize_plmn(cellIdentity.mncString)
            )

            is CellInfoCdma -> CellSnapshot(
                cell_id = int_or_null(cellIdentity.basestationId)?.toString(),
                radio_type = "cdma",
                area_code = int_or_null(cellIdentity.networkId)?.toString(),
                pci = null,
                mcc = null,
                mnc = null
            )

            is CellInfoNr -> CellSnapshot(
                cell_id = reflect_long(cellIdentity, "getNci")?.toString(),
                radio_type = "nr",
                area_code = reflect_int(cellIdentity, "getTac")?.toString(),
                pci = reflect_int(cellIdentity, "getPci"),
                mcc = normalize_plmn(reflect_string(cellIdentity, "getMccString")),
                mnc = normalize_plmn(reflect_string(cellIdentity, "getMncString"))
            )

            else -> null
        }?.takeIf { snapshot ->
            !snapshot.cell_id.isNullOrBlank() || !snapshot.area_code.isNullOrBlank() || snapshot.pci != null
        }
    }

    private fun int_or_null(value: Int): Int? {
        return if (value == Int.MAX_VALUE || value == Int.MIN_VALUE || value < 0) null else value
    }

    private fun long_or_null(value: Long): Long? {
        return if (value == Long.MAX_VALUE || value == Long.MIN_VALUE || value < 0L) null else value
    }

    private fun reflect_int(target: Any, method_name: String): Int? {
        val raw_value = runCatching {
            target::class.java.getMethod(method_name).invoke(target) as? Int
        }.getOrNull() ?: return null
        return int_or_null(raw_value)
    }

    private fun reflect_long(target: Any, method_name: String): Long? {
        val raw_value = runCatching {
            target::class.java.getMethod(method_name).invoke(target) as? Long
        }.getOrNull() ?: return null
        return long_or_null(raw_value)
    }

    private fun reflect_string(target: Any, method_name: String): String? {
        return runCatching {
            target::class.java.getMethod(method_name).invoke(target) as? String
        }.getOrNull()
    }

    private suspend fun read_location_from_cell_lookup(
        opencellid_api_key: String,
        cell_snapshot: CellSnapshot?,
        timeout_seconds: Int
    ): LocationSnapshot? {
        if (cell_snapshot == null) return null
        if (opencellid_api_key.trim().isEmpty()) return null

        val override_reader = cell_lookup_reader
        if (override_reader != null) return override_reader(opencellid_api_key, cell_snapshot)

        val resolved = open_cell_lookup_client.lookup(
            cell_snapshot = cell_snapshot,
            api_key = opencellid_api_key,
            timeout_seconds = timeout_seconds
        ) ?: return null
        return LocationSnapshot(
            latitude = resolved.latitude,
            longitude = resolved.longitude,
            altitude = null,
            accuracy_meters = resolved.accuracy_meters
        )
    }

    private fun normalize_plmn(value: String?): String? {
        val normalized = value?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        if (normalized == Int.MAX_VALUE.toString() || normalized == Int.MIN_VALUE.toString()) return null
        if (normalized == "-1") return null
        return normalized
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
        val location_snapshot: LocationSnapshot?,
        val cell_snapshot: CellSnapshot?
    )
}
