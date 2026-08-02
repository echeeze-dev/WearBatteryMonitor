package com.echeeze.wearbatterymonitor.data

data class BatteryEntry(
    val deviceType: String,
    val percent: Int,
    val timestamp: Long
)
