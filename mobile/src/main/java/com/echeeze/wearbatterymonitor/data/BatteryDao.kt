package com.echeeze.wearbatterymonitor.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BatteryDao {
    @Query("SELECT * FROM battery_history WHERE deviceType = :type ORDER BY timestamp ASC")
    suspend fun getHistoryByType(type: String): List<BatteryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BatteryEntry)

    @Query("DELETE FROM battery_history WHERE deviceType = :type AND timestamp NOT IN (SELECT timestamp FROM battery_history WHERE deviceType = :type ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun trimHistory(type: String, limit: Int)
}
