package com.yshalsager.mafza.emergency

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.TriggerSource

class EmergencyStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val trigger_source = resolve_trigger_source(intent?.action) ?: return
        val service_intent = Intent(context, EmergencyExecutionService::class.java).apply {
            action = EmergencyServiceContract.ACTION_START_RUN
            putExtra(EmergencyServiceContract.EXTRA_TRIGGER_SOURCE, trigger_source.name)
            putExtra(EmergencyServiceContract.EXTRA_EXECUTION_MODE, ExecutionMode.LIVE.name)
        }
        ContextCompat.startForegroundService(context, service_intent)
    }

    private fun resolve_trigger_source(action: String?): TriggerSource? {
        return when (action) {
            EmergencyServiceContract.ACTION_TRIGGER_SHORTCUT -> TriggerSource.SHORTCUT
            EmergencyServiceContract.ACTION_TRIGGER_WIDGET -> TriggerSource.WIDGET
            EmergencyServiceContract.ACTION_TRIGGER_QUICK_SETTINGS_TILE -> TriggerSource.QUICK_SETTINGS_TILE
            else -> null
        }
    }
}
