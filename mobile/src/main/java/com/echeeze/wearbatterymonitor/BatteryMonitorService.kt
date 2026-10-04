package com.echeeze.wearbatterymonitor

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.echeeze.wearbatterymonitor.data.AppDatabase
import com.echeeze.wearbatterymonitor.data.BatteryEntry
import com.echeeze.wearbatterymonitor.data.BatteryRepository
import com.echeeze.wearbatterymonitor.data.BatteryRepositoryImpl
import com.echeeze.wearbatterymonitor.data.Constants
import com.echeeze.wearbatterymonitor.monitor.EarphoneBatteryMonitor
import com.echeeze.wearbatterymonitor.monitor.NotificationHelper
import com.echeeze.wearbatterymonitor.monitor.PhoneBatteryMonitor
import com.echeeze.wearbatterymonitor.sync.WearDataSyncer
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Timer
import java.util.TimerTask

/**
 * バックグラウンドで常駐し、スマホ・イヤホン・ウォッチのバッテリー監視および各種データ同期を統括するフォアグラウンドサービスです。
 *
 * 各種監視・通知・同期の具体的処理は [PhoneBatteryMonitor], [EarphoneBatteryMonitor],
 * [NotificationHelper], [WearDataSyncer] へ委譲しています。
 */
class BatteryMonitorService : Service() {

    /**
     * バインド時のローカルバインダーです。
     */
    inner class LocalBinder : Binder() {
        fun getService(): BatteryMonitorService = this@BatteryMonitorService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var repository: BatteryRepository
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var phoneMonitor: PhoneBatteryMonitor
    private lateinit var earphoneMonitor: EarphoneBatteryMonitor
    private lateinit var wearDataSyncer: WearDataSyncer

    var currentPhonePercent = 0
    var currentPhoneCharging = false
    var currentEarphonePercent: Int? = null
    var currentEarphoneName: String? = null
    var currentWatchName: String? = null

    val phoneHistory = mutableListOf<BatteryEntry>()
    val watchHistory = mutableListOf<BatteryEntry>()
    val earphoneHistory = mutableListOf<BatteryEntry>()

    private var watchUpdateTimer: Timer? = null
    private var updateIntervalSeconds = 60

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(this)
        repository = BatteryRepositoryImpl(this, database.batteryDao())
        notificationHelper = NotificationHelper(this)

        phoneMonitor = PhoneBatteryMonitor(this) { percent, isCharging, reason ->
            currentPhonePercent = percent
            currentPhoneCharging = isCharging
            addHistory(phoneHistory, "phone", percent, isCharging, Build.MODEL, reason)
            syncAllData()
            notificationHelper.updateNotification(percent, isCharging)
        }

        earphoneMonitor = EarphoneBatteryMonitor(this) { percent, name, reason ->
            currentEarphonePercent = percent
            currentEarphoneName = name
            if (percent != null || name != null) {
                addHistory(earphoneHistory, "earphone", percent ?: -1, false, name, reason)
                syncAllData()
            }
        }

        wearDataSyncer = WearDataSyncer(
            context = this,
            repository = repository,
            scope = serviceScope,
            onWatchBatteryReceived = { percent, isCharging, deviceName, recordReason ->
                if (deviceName != null) {
                    currentWatchName = deviceName
                }
                addHistory(watchHistory, "watch", percent, isCharging, deviceName, recordReason)
                syncAllData()
            },
            onRequestBatteryUpdate = {
                syncAllData()
            }
        )

        notificationHelper.createNotificationChannel()
        startForeground(NotificationHelper.NOTIFICATION_ID, notificationHelper.createNotification(0, false))

        val (initialPercent, initialCharging) = phoneMonitor.startMonitoring()
        currentPhonePercent = initialPercent
        currentPhoneCharging = initialCharging
        addHistory(phoneHistory, "phone", initialPercent, initialCharging, Build.MODEL, "INITIAL_FETCH")

        earphoneMonitor.startMonitoring()
        wearDataSyncer.startSyncing()

        loadHistoryFromDb()

        val sharedPref = getSharedPreferences("MainActivity", MODE_PRIVATE)
        updateIntervalSeconds = sharedPref.getInt("update_interval_seconds", 60)
        startWatchUpdateTimer(updateIntervalSeconds)
    }

    /**
     * 定期タイマーの更新間隔（秒）を変更します。
     *
     * @param seconds 新しい更新間隔 (秒)
     */
    fun updateTimerInterval(seconds: Int) {
        updateIntervalSeconds = seconds
        startWatchUpdateTimer(seconds)
    }

    private fun startWatchUpdateTimer(intervalSeconds: Int) {
        watchUpdateTimer?.cancel()
        watchUpdateTimer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    addHistory(phoneHistory, "phone", currentPhonePercent, currentPhoneCharging, Build.MODEL, "PERIODIC_INTERVAL", intervalSeconds)

                    val lastWatch = watchHistory.lastOrNull()
                    val isWatchStale = lastWatch == null || System.currentTimeMillis() - lastWatch.timestamp > intervalSeconds * 2000L
                    if (isWatchStale) {
                        Wearable.getNodeClient(this@BatteryMonitorService).connectedNodes.addOnSuccessListener { nodes ->
                            val reason = if (nodes.isEmpty()) "WATCH_DISCONNECT" else "WATCH_NO_RESPONSE"
                            addHistory(watchHistory, "watch", -1, false, currentWatchName, reason, intervalSeconds)
                        }.addOnFailureListener {
                            addHistory(watchHistory, "watch", -1, false, currentWatchName, "WATCH_DISCONNECT", intervalSeconds)
                        }
                    }

                    if (currentEarphoneName != null) {
                        addHistory(earphoneHistory, "earphone", currentEarphonePercent ?: -1, false, currentEarphoneName, "PERIODIC_INTERVAL", intervalSeconds)
                    } else {
                        addHistory(earphoneHistory, "earphone", -1, false, null, "PERIODIC_INTERVAL", intervalSeconds)
                    }

                    requestWatchBatteryUpdate()
                    syncAllData()
                }
            }, 1000, intervalSeconds * 1000L)
        }
    }

    private fun requestWatchBatteryUpdate() {
        val nodeClient = Wearable.getNodeClient(this)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                Wearable.getMessageClient(this)
                    .sendMessage(node.id, "/request_battery_update", null)
            }
        }
    }

    private fun addHistory(
        list: MutableList<BatteryEntry>,
        type: String,
        percent: Int,
        isCharging: Boolean,
        deviceName: String? = null,
        recordReason: String? = null,
        intervalSeconds: Int? = null
    ) {
        val entry = BatteryEntry(type, percent, System.currentTimeMillis(), isCharging, deviceName, recordReason, intervalSeconds)
        list.add(entry)
        if (list.size > Constants.MAX_HISTORY_SIZE) list.removeAt(0)

        serviceScope.launch {
            repository.insertEntry(entry)
        }
    }

    private fun loadHistoryFromDb() {
        serviceScope.launch {
            val phoneData = repository.getHistoryByType("phone")
            val watchData = repository.getHistoryByType("watch")
            val earphoneData = repository.getHistoryByType("earphone")

            withContext(Dispatchers.Main) {
                phoneHistory.clear(); phoneHistory.addAll(phoneData)
                watchHistory.clear(); watchHistory.addAll(watchData)
                earphoneHistory.clear(); earphoneHistory.addAll(earphoneData)
                syncAllData()
            }
        }
    }

    /**
     * スマホ、ウォッチ、イヤホンの全バッテリー状態および履歴を Wear OS 端末へ自動同期します。
     */
    fun syncAllData() {
        wearDataSyncer.syncStatusToWear(
            phonePercent = currentPhonePercent,
            isCharging = currentPhoneCharging,
            earphonePercent = currentEarphonePercent,
            earphoneName = currentEarphoneName,
            phoneHistory = phoneHistory,
            watchHistory = watchHistory,
            earphoneHistory = earphoneHistory
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        watchUpdateTimer?.cancel()
        phoneMonitor.stopMonitoring()
        earphoneMonitor.stopMonitoring()
        wearDataSyncer.stopSyncing()
        serviceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder = binder
}
