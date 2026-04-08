package com.yshalsager.mafza.emergency

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.yshalsager.mafza.R

class EmergencyQuickSettingsTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = getString(R.string.quick_settings_tile_label)
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        EmergencyExternalTriggerDispatcher.dispatch(
            context = applicationContext,
            trigger_action = EmergencyServiceContract.ACTION_TRIGGER_QUICK_SETTINGS_TILE
        )
    }
}
