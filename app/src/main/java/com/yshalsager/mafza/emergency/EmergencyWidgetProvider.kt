package com.yshalsager.mafza.emergency

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.yshalsager.mafza.R

class EmergencyWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        app_widget_manager: AppWidgetManager,
        app_widget_ids: IntArray
    ) {
        app_widget_ids.forEach { app_widget_id ->
            app_widget_manager.updateAppWidget(
                app_widget_id,
                build_remote_views(context)
            )
        }
    }

    private fun build_remote_views(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.emergency_trigger_widget)
        views.setOnClickPendingIntent(
            R.id.emergency_widget_trigger_button,
            EmergencyExternalTriggerDispatcher.pending_broadcast(
                context = context,
                trigger_action = EmergencyServiceContract.ACTION_TRIGGER_WIDGET,
                request_code = REQUEST_CODE_WIDGET_TRIGGER
            )
        )
        return views
    }

    companion object {
        private const val REQUEST_CODE_WIDGET_TRIGGER = 1001
    }
}
