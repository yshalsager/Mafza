package com.yshalsager.mafza.emergency

object EmergencyServiceContract {
    const val ACTION_START_RUN = "com.yshalsager.mafza.action.START_RUN"
    const val ACTION_CANCEL_RUN = "com.yshalsager.mafza.action.CANCEL_RUN"

    const val ACTION_TRIGGER_SHORTCUT = "com.yshalsager.mafza.action.TRIGGER_SHORTCUT"
    const val ACTION_TRIGGER_WIDGET = "com.yshalsager.mafza.action.TRIGGER_WIDGET"
    const val ACTION_TRIGGER_QUICK_SETTINGS_TILE = "com.yshalsager.mafza.action.TRIGGER_QUICK_SETTINGS_TILE"

    const val EXTRA_TRIGGER_SOURCE = "extra_trigger_source"
    const val EXTRA_EXECUTION_MODE = "extra_execution_mode"
    const val EXTRA_RUN_ID = "extra_run_id"
}
