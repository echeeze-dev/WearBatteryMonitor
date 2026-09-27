package com.echeeze.wearbatterymonitor.data

interface BatteryRepository {
    suspend fun getHistoryByType(type: String): List<BatteryEntry>
    suspend fun insertEntry(entry: BatteryEntry)
    suspend fun trimHistory(type: String, limit: Int)
    suspend fun syncStatusToWear(
        phonePercent: Int,
        isCharging: Boolean,
        earphonePercent: Int?,
        earphoneName: String?,
        phoneHistory: List<BatteryEntry>,
        watchHistory: List<BatteryEntry> = emptyList(),
        earphoneHistory: List<BatteryEntry> = emptyList(),
        rangeHours: Int = 3
    )
}
