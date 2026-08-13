package com.echeeze.wearbatterymonitor.tile

import android.util.Log
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

class TileDataUpdateService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d("TileDataUpdateService", "onDataChanged received")
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/battery_status") {
                Log.d("TileDataUpdateService", "Requesting tile update")
                TileService.getUpdater(this)
                    .requestUpdate(MainTileService::class.java)
            }
        }
    }
}
