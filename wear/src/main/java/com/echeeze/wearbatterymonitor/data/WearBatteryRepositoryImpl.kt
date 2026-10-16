package com.echeeze.wearbatterymonitor.data

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WearBatteryRepositoryImpl(
    private val context: Context
) : WearBatteryRepository {

    override suspend fun sendWatchBatteryToPhone(
        percent: Int,
        isCharging: Boolean,
        deviceName: String,
        recordReason: String
    ): Unit = withContext(Dispatchers.IO) {
        try {
            val putDataReq = PutDataMapRequest.create("/watch_battery").apply {
                dataMap.putInt("watch_percent", percent)
                dataMap.putBoolean("watch_is_charging", isCharging)
                dataMap.putString("device_name", deviceName)
                dataMap.putString("record_reason", recordReason)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            Wearable.getDataClient(context).putDataItem(putDataReq)
            Log.d("WearBatteryRepository", "Sent watch battery: $percent% ($recordReason)")
        } catch (e: Exception) {
            Log.e("WearBatteryRepository", "Failed to send watch battery", e)
        }
    }

    override suspend fun getPhoneBatteryStatus(): PhoneBatteryStatus? = withContext(Dispatchers.IO) {
        try {
            val dataClient = Wearable.getDataClient(context)
            val dataItems = Tasks.await(dataClient.dataItems)

            var rangeHours = 3
            dataItems.find { it.uri.path == "/settings/graph_range" }?.let { item ->
                rangeHours = DataMapItem.fromDataItem(item).dataMap.getInt("range_hours", 3)
            }

            val batteryItem = dataItems.find { it.uri.path == "/battery_status" }
            if (batteryItem != null) {
                val dataMap = DataMapItem.fromDataItem(batteryItem).dataMap
                val percent = dataMap.getInt("battery_percent")
                val isCharging = dataMap.getBoolean("is_charging")
                val timestamp = dataMap.getLong("timestamp")
                val earphonePercent = if (dataMap.containsKey("earphone_percent")) dataMap.getInt("earphone_percent") else null
                val earphoneName = if (dataMap.containsKey("earphone_name")) dataMap.getString("earphone_name") else null

                val history = mutableListOf<Triple<Int, Boolean, Long>>()
                dataMap.getDataMapArrayList("phone_history")?.forEach { entryMap ->
                    history.add(Triple(entryMap.getInt("p"), entryMap.getBoolean("c"), entryMap.getLong("t")))
                }
                dataItems.release()
                return@withContext PhoneBatteryStatus(
                    percent = percent,
                    isCharging = isCharging,
                    timestamp = timestamp,
                    earphonePercent = earphonePercent,
                    earphoneName = earphoneName,
                    phoneHistory = history,
                    rangeHours = rangeHours
                )
            }
            dataItems.release()
            null
        } catch (e: Exception) {
            Log.e("WearBatteryRepository", "Error fetching phone battery status", e)
            null
        }
    }

    override suspend fun getEarphoneBatteryStatus(): EarphoneBatteryStatus? = withContext(Dispatchers.IO) {
        try {
            val dataClient = Wearable.getDataClient(context)
            val dataItems = Tasks.await(dataClient.dataItems)

            var rangeHours = 3
            dataItems.find { it.uri.path == "/settings/graph_range" }?.let { item ->
                rangeHours = DataMapItem.fromDataItem(item).dataMap.getInt("range_hours", 3)
            }

            val batteryItem = dataItems.find { it.uri.path == "/battery_status" }
            if (batteryItem != null) {
                val dataMap = DataMapItem.fromDataItem(batteryItem).dataMap
                val timestamp = dataMap.getLong("timestamp")
                val earphonePercent = if (dataMap.containsKey("earphone_percent")) dataMap.getInt("earphone_percent") else null
                val earphoneName = if (dataMap.containsKey("earphone_name")) dataMap.getString("earphone_name") else null

                val history = mutableListOf<Triple<Int, Boolean, Long>>()
                dataMap.getDataMapArrayList("earphone_history")?.forEach { entryMap ->
                    history.add(Triple(entryMap.getInt("p"), entryMap.getBoolean("c"), entryMap.getLong("t")))
                }
                dataItems.release()
                return@withContext EarphoneBatteryStatus(
                    percent = earphonePercent,
                    name = earphoneName,
                    timestamp = timestamp,
                    earphoneHistory = history,
                    rangeHours = rangeHours
                )
            }
            dataItems.release()
            null
        } catch (e: Exception) {
            Log.e("WearBatteryRepository", "Error fetching earphone battery status", e)
            null
        }
    }

    override suspend fun requestBatteryUpdate(): Unit = withContext(Dispatchers.IO) {
        try {
            val capabilityClient = Wearable.getCapabilityClient(context)
            val info = Tasks.await(capabilityClient.getCapability("battery_monitor_phone_app", CapabilityClient.FILTER_REACHABLE))
            info.nodes.forEach { node ->
                Wearable.getMessageClient(context).sendMessage(node.id, "/request_battery_update", null)
            }
        } catch (e: Exception) {
            Log.e("WearBatteryRepository", "Failed to request battery update", e)
        }
    }
}
