package com.echeeze.wearbatterymonitor.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_history")
data class BatteryEntry(
    val deviceType: String,
    val percent: Int,
    @PrimaryKey val timestamp: Long,
    val isCharging: Boolean = false,
    val deviceName: String? = null,
    val recordReason: String? = null,
    val intervalSeconds: Int? = null
)
