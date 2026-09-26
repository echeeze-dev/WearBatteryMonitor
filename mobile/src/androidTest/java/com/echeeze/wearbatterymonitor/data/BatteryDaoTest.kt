package com.echeeze.wearbatterymonitor.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatteryDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var batteryDao: BatteryDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        batteryDao = database.batteryDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndGetHistoryByType() = runBlocking {
        val entry1 = BatteryEntry(deviceType = "phone", percent = 80, timestamp = 1000L, isCharging = false)
        val entry2 = BatteryEntry(deviceType = "phone", percent = 75, timestamp = 2000L, isCharging = false)

        batteryDao.insert(entry1)
        batteryDao.insert(entry2)

        val history = batteryDao.getHistoryByType("phone")
        assertEquals(2, history.size)
        assertEquals(80, history[0].percent)
        assertEquals(1000L, history[0].timestamp)
        assertEquals(75, history[1].percent)
        assertEquals(2000L, history[1].timestamp)
    }

    @Test
    fun trimHistory() = runBlocking {
        for (i in 1..10) {
            batteryDao.insert(
                BatteryEntry(
                    deviceType = "phone",
                    percent = i * 10,
                    timestamp = i * 1000L
                )
            )
        }

        // Limit to 3 items
        batteryDao.trimHistory("phone", 3)

        val history = batteryDao.getHistoryByType("phone")
        assertEquals(3, history.size)
        // Should keep the latest 3 (80, 90, 100) ordered by timestamp ASC
        assertEquals(80, history[0].percent)
        assertEquals(8000L, history[0].timestamp)
        assertEquals(90, history[1].percent)
        assertEquals(9000L, history[1].timestamp)
        assertEquals(100, history[2].percent)
        assertEquals(10000L, history[2].timestamp)
    }

    @Test
    fun filterByDeviceType() = runBlocking {
        batteryDao.insert(BatteryEntry("phone", 90, 1000L))
        batteryDao.insert(BatteryEntry("watch", 85, 1000L))
        batteryDao.insert(BatteryEntry("phone", 88, 2000L))

        val phoneHistory = batteryDao.getHistoryByType("phone")
        val watchHistory = batteryDao.getHistoryByType("watch")

        assertEquals(2, phoneHistory.size)
        assertEquals(1, watchHistory.size)
        assertEquals("watch", watchHistory[0].deviceType)
    }
}
