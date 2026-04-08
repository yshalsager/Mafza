package com.yshalsager.mafza.emergency

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object EmergencyExternalTriggerDispatcher {
    fun dispatch(context: Context, trigger_action: String) {
        context.sendBroadcast(build_broadcast_intent(context, trigger_action))
    }

    fun pending_broadcast(
        context: Context,
        trigger_action: String,
        request_code: Int,
        flags: Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    ): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            request_code,
            build_broadcast_intent(context, trigger_action),
            flags
        )
    }

    private fun build_broadcast_intent(
        context: Context,
        trigger_action: String
    ): Intent {
        return Intent(context, EmergencyStartReceiver::class.java).apply {
            action = trigger_action
            `package` = context.packageName
        }
    }
}
