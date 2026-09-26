package com.echeeze.wearbatterymonitor.data

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FakeBatteryDao : BatteryDao {
    private val entries = mutableListOf<BatteryEntry>()

    override suspend fun getHistoryByType(type: String): List<BatteryEntry> {
        return entries.filter { it.deviceType == type }.sortedBy { it.timestamp }
    }

    override suspend fun insert(entry: BatteryEntry) {
        entries.add(entry)
    }

    override suspend fun trimHistory(type: String, limit: Int) {
        val filtered = entries.filter { it.deviceType == type }.sortedByDescending { it.timestamp }
        if (filtered.size > limit) {
            val toRemove = filtered.drop(limit)
            entries.removeAll(toRemove)
        }
    }
}

class BatteryRepositoryTest {

    private lateinit var fakeDao: FakeBatteryDao
    private lateinit var mockContext: Context
    private lateinit var repository: BatteryRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeBatteryDao()
        mockContext = mockk(relaxed = true)
        repository = BatteryRepositoryImpl(mockContext, fakeDao)
    }

    @Test
    fun testRepositoryImplInsertAndRetrieve() = runBlocking {
        repository.insertEntry(BatteryEntry("phone", 100, 1000L))
        repository.insertEntry(BatteryEntry("phone", 95, 2000L))

        val history = repository.getHistoryByType("phone")
        assertEquals(2, history.size)
        assertEquals(100, history[0].percent)
        assertEquals(95, history[1].percent)
    }

    @Test
    fun testRepositoryImplTrimHistory() = runBlocking {
        for (i in 1..Constants.MAX_HISTORY_SIZE + 5) {
            repository.insertEntry(BatteryEntry("phone", i, i * 1000L))
        }

        val history = repository.getHistoryByType("phone")
        assertEquals(Constants.MAX_HISTORY_SIZE, history.size)
    }

    @Test
    fun testFakeDaoDirectOperations() = runBlocking {
        fakeDao.insert(BatteryEntry("watch", 80, 1000L))
        val watchHistory = repository.getHistoryByType("watch")
        assertEquals(1, watchHistory.size)
        assertEquals(80, watchHistory[0].percent)
    }
}
