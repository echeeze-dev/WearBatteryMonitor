package com.echeeze.wearbatterymonitor

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

class MobileWearableListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/request_battery_update") {
            Log.d("BatteryMonitorListener", "Received request from Wear")
            sendBatteryStatus()
        }
    }

    private fun sendBatteryStatus() {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, ifilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val dataClient = Wearable.getDataClient(this)
        val putDataMapReq = PutDataMapRequest.create("/battery_status")
        putDataMapReq.dataMap.putInt("battery_percent", percent)
        putDataMapReq.dataMap.putBoolean("is_charging", isCharging)
        putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis())
        val putDataReq = putDataMapReq.asPutDataRequest()
        putDataReq.setUrgent()

        dataClient.putDataItem(putDataReq).addOnSuccessListener {
            Log.d("BatteryMonitorListener", "Sent initial status: $percent%")
        }
    }
}
