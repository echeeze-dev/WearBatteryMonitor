package com.echeeze.wearbatterymonitor.tile

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

class TileDataUpdateService : WearableListenerService() {
    override fun onCreate() {
        super.onCreate()
        Log.i("TileDataUpdateService", "Service created")
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.i("TileDataUpdateService", "onDataChanged received ${dataEvents.count} events")
        for (event in dataEvents) {
            Log.i("TileDataUpdateService", "Event path: ${event.dataItem.uri.path}")
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/battery_status") {
                Log.d("TileDataUpdateService", "Requesting tile update")
                TileService.getUpdater(this)
                    .requestUpdate(PhoneBatteryTileService::class.java)
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.i("TileDataUpdateService", "Received message path: ${messageEvent.path}")
        if (messageEvent.path == "/request_battery_update") {
            Log.i("TileDataUpdateService", "Processing battery update request from phone")
            sendWatchBatteryToPhone()
        }
    }

    private fun sendWatchBatteryToPhone() {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0
        val isCharging = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let {
            it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL
        }

        val putDataReq = PutDataMapRequest.create("/watch_battery").apply {
            dataMap.putInt("watch_percent", percent)
            dataMap.putBoolean("watch_is_charging", isCharging)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        
        Wearable.getDataClient(this).putDataItem(putDataReq)
        Log.d("TileDataUpdateService", "Sent watch battery status to phone: $percent%")
    }
}
