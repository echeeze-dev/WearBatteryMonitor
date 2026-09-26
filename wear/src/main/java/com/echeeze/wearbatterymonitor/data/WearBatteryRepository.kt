package com.echeeze.wearbatterymonitor.data

data class PhoneBatteryStatus(
    val percent: Int,
    val isCharging: Boolean,
    val timestamp: Long,
    val earphonePercent: Int?,
    val earphoneName: String?,
    val phoneHistory: List<Triple<Int, Boolean, Long>>,
    val rangeHours: Int = 3
)

interface WearBatteryRepository {
    suspend fun sendWatchBatteryToPhone(
        percent: Int,
        isCharging: Boolean,
        deviceName: String,
        recordReason: String
    )

    suspend fun getPhoneBatteryStatus(): PhoneBatteryStatus?

    suspend fun requestBatteryUpdate()
}
