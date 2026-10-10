package com.echeeze.wearbatterymonitor

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.echeeze.wearbatterymonitor.data.AppDatabase
import com.echeeze.wearbatterymonitor.data.BatteryEntry
import com.echeeze.wearbatterymonitor.data.BatteryRepositoryImpl
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MobileWearableListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.i("BatteryMonitorListener", "Received message path: ${messageEvent.path}")
        if (messageEvent.path == "/request_battery_update") {
            Log.i("BatteryMonitorListener", "Processing battery update request from Wear")
            sendBatteryStatus()
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.i("BatteryMonitorListener", "onDataChanged received ${dataEvents.count} events")
        // 常駐監視サービスが起動中で WearDataSyncer 側が直接受領・保存を行っている場合は二重インサートを防止する
        if (BatteryMonitorService.isRunning) {
            Log.d("BatteryMonitorListener", "Skipping duplicate insert in listener service because BatteryMonitorService is running")
            return
        }

        val database = AppDatabase.getDatabase(this)
        val repository = BatteryRepositoryImpl(this, database.batteryDao())
        val scope = CoroutineScope(Dispatchers.IO)

        for (event in dataEvents) {
            Log.i("BatteryMonitorListener", "Event path: ${event.dataItem.uri.path}")
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/watch_battery") {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val percent = dataMap.getInt("watch_percent")
                val isCharging = dataMap.getBoolean("watch_is_charging")
                val deviceName = if (dataMap.containsKey("device_name")) dataMap.getString("device_name") else null
                val recordReason = if (dataMap.containsKey("record_reason")) dataMap.getString("record_reason") else "DATA_SYNC"

                Log.i("BatteryMonitorListener", "Backup listener received watch battery: $percent% from $deviceName ($recordReason)")
                scope.launch {
                    repository.insertEntry(
                        BatteryEntry(
                            deviceType = "watch",
                            percent = percent,
                            timestamp = System.currentTimeMillis(),
                            isCharging = isCharging,
                            deviceName = deviceName,
                            recordReason = recordReason
                        )
                    )
                }
            }
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
