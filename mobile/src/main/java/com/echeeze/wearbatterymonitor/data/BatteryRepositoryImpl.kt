package com.echeeze.wearbatterymonitor.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BatteryRepositoryImpl(
    private val context: Context,
    private val batteryDao: BatteryDao
) : BatteryRepository {

    override suspend fun getHistoryByType(type: String): List<BatteryEntry> = withContext(Dispatchers.IO) {
        batteryDao.getHistoryByType(type)
    }

    override suspend fun insertEntry(entry: BatteryEntry) = withContext(Dispatchers.IO) {
        batteryDao.insert(entry)
        batteryDao.trimHistory(entry.deviceType, Constants.MAX_HISTORY_SIZE)
    }

    override suspend fun trimHistory(type: String, limit: Int) = withContext(Dispatchers.IO) {
        batteryDao.trimHistory(type, limit)
    }

    override suspend fun syncStatusToWear(
        phonePercent: Int,
        isCharging: Boolean,
        earphonePercent: Int?,
        earphoneName: String?,
        phoneHistory: List<BatteryEntry>,
        watchHistory: List<BatteryEntry>,
        earphoneHistory: List<BatteryEntry>,
        rangeHours: Int
    ): Unit = withContext(Dispatchers.IO) {
        try {
            // DBから最新の完全な履歴データをロードして同期データの整合性を100%保証する
            val dbPhoneHistory = batteryDao.getHistoryByType("phone").ifEmpty { phoneHistory }
            val dbWatchHistory = batteryDao.getHistoryByType("watch").ifEmpty { watchHistory }
            val dbEarphoneHistory = batteryDao.getHistoryByType("earphone").ifEmpty { earphoneHistory }

            val dataClient = Wearable.getDataClient(context)
            val putDataMapReq = PutDataMapRequest.create("/battery_status")
            val dataMap = putDataMapReq.dataMap
            dataMap.putInt("battery_percent", phonePercent)
            dataMap.putBoolean("is_charging", isCharging)
            dataMap.putInt("range_hours", rangeHours)
            dataMap.putLong("timestamp", System.currentTimeMillis())

            earphonePercent?.let { dataMap.putInt("earphone_percent", it) }
            earphoneName?.let { dataMap.putString("earphone_name", it) }

            dataMap.putDataMapArrayList("phone_history", historyToDataMapList(downsampleHistory(dbPhoneHistory, 400)))
            dataMap.putDataMapArrayList("watch_history", historyToDataMapList(downsampleHistory(dbWatchHistory, 400)))
            dataMap.putDataMapArrayList("earphone_history", historyToDataMapList(downsampleHistory(dbEarphoneHistory, 400)))

            val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
            dataClient.putDataItem(putDataReq)
            Log.d("BatteryRepositoryImpl", "Synced status to Wear successfully ($phonePercent%)")
        } catch (e: Exception) {
            Log.e("BatteryRepositoryImpl", "Failed to sync status to Wear", e)
        }
    }

    private fun historyToDataMapList(history: List<BatteryEntry>): ArrayList<DataMap> {
        val list = ArrayList<DataMap>()
        history.forEach { entry ->
            val dm = DataMap()
            dm.putInt("p", entry.percent)
            dm.putLong("t", entry.timestamp)
            dm.putBoolean("c", entry.isCharging)
            list.add(dm)
        }
        return list
    }

    private fun downsampleHistory(history: List<BatteryEntry>, targetSize: Int): List<BatteryEntry> {
        if (history.size <= targetSize) return history
        val step = history.size.toDouble() / targetSize
        val result = mutableListOf<BatteryEntry>()
        for (i in 0 until targetSize) {
            val index = (i * step).toInt().coerceAtMost(history.size - 1)
            result.add(history[index])
        }
        if (result.isNotEmpty() && history.isNotEmpty()) {
            result[0] = history.first()
            result[result.size - 1] = history.last()
        }
        return result
    }
}
