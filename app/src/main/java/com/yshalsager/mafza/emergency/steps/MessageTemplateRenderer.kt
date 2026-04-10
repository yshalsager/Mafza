package com.yshalsager.mafza.emergency.steps

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.yshalsager.mafza.core.contracts.StepContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MessageTemplateRenderer {
    private const val DEFAULT_TEMPLATE = "Mafza emergency alert at {timestamp}."

    fun render(
        context: Context,
        step_context: StepContext,
        run_step_state: RunStepState,
        template_override: String? = null
    ): String {
        val template = template_override?.takeIf { it.isNotBlank() }
            ?: step_context.profile.message_template.ifBlank { DEFAULT_TEMPLATE }
        val now = System.currentTimeMillis()
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale).format(Date(now))
        val location_snapshot = run_step_state.get_location()
        val latitude = location_snapshot?.latitude?.toString().orEmpty()
        val longitude = location_snapshot?.longitude?.toString().orEmpty()
        val altitude = location_snapshot?.altitude?.toString().orEmpty()
        val accuracy = location_snapshot?.accuracy_meters?.toString().orEmpty()
        val cell_id = location_snapshot?.cell_snapshot?.cell_id.orEmpty()
        val cell_radio = location_snapshot?.cell_snapshot?.radio_type.orEmpty()
        val cell_area = location_snapshot?.cell_snapshot?.area_code.orEmpty()
        val cell_pci = location_snapshot?.cell_snapshot?.pci?.toString().orEmpty()
        val cell_mcc = location_snapshot?.cell_snapshot?.mcc.orEmpty()
        val cell_mnc = location_snapshot?.cell_snapshot?.mnc.orEmpty()
        val maps_url = if (location_snapshot == null) "" else {
            "https://www.google.com/maps/search/?api=1&query=${location_snapshot.latitude},${location_snapshot.longitude}"
        }
        val battery_percent = read_battery_percent(context)
        val app_version = runCatching {
            val package_info = context.packageManager.getPackageInfo(context.packageName, 0)
            package_info.versionName ?: ""
        }.getOrDefault("")

        val replacements = mapOf(
            "{timestamp}" to timestamp,
            "{lat}" to latitude,
            "{lon}" to longitude,
            "{maps_url}" to maps_url,
            "{trigger}" to step_context.trigger.name,
            "{altitude}" to altitude,
            "{accuracy}" to accuracy,
            "{cell_id}" to cell_id,
            "{cell_radio}" to cell_radio,
            "{cell_area}" to cell_area,
            "{cell_pci}" to cell_pci,
            "{cell_mcc}" to cell_mcc,
            "{cell_mnc}" to cell_mnc,
            "{battery}" to battery_percent,
            "{locale}" to locale.toLanguageTag(),
            "{app_version}" to app_version
        )

        var rendered = template
        replacements.forEach { (token, value) ->
            rendered = rendered.replace(token, value)
        }
        return rendered
    }

    private fun read_battery_percent(context: Context): String {
        val battery_intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return ""
        val level = battery_intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery_intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return ""
        return ((level * 100f) / scale).toInt().toString()
    }
}
