package com.nahuel.homeflow.engine

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nahuel.homeflow.R
import com.nahuel.homeflow.data.Store

/** Quick Settings tile: one tap runs the routine chosen in Settings (Config.tileRoutineId). */
class RoutineTileService : TileService() {

    companion object {
        /** Ask the system to re-read the tile after the chosen routine changed. */
        fun refresh(ctx: Context) {
            runCatching { requestListeningState(ctx, ComponentName(ctx, RoutineTileService::class.java)) }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        Store.init(applicationContext)
        val tile = qsTile ?: return
        val routine = Store.routine(Store.config.value.tileRoutineId)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_smartflow)
        tile.label = routine?.name ?: "SmartFlow"
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (routine == null) "In Einstellungen wählen" else "Tippen zum Starten"
        tile.state = if (routine == null) Tile.STATE_UNAVAILABLE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val id = Store.config.value.tileRoutineId
        if (id.isBlank()) return
        // Running smart-home actions from the lock screen needs an unlock first.
        if (isLocked) unlockAndRun { RoutineEngine.runAsync(applicationContext, id) }
        else RoutineEngine.runAsync(applicationContext, id)
    }
}
