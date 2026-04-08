package com.yshalsager.mafza.emergency

import android.app.Activity
import android.os.Bundle

class EmergencyShortcutProxyActivity : Activity() {
    override fun onCreate(saved_instance_state: Bundle?) {
        super.onCreate(saved_instance_state)
        EmergencyExternalTriggerDispatcher.dispatch(
            context = applicationContext,
            trigger_action = EmergencyServiceContract.ACTION_TRIGGER_SHORTCUT
        )
        finish()
    }
}
