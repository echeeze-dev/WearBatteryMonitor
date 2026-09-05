package com.echeeze.wearbatterymonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.echeeze.wearbatterymonitor.data.BatteryEntry
import com.echeeze.wearbatterymonitor.data.Constants
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.echeeze.wearbatterymonitor.data.AppDatabase
import kotlinx.coroutines.*
import java.util.Timer
import java.util.TimerTask

class BatteryMonitorService : Service(), DataClient.OnDataChangedListener {

    private var bluetoothHeadset: BluetoothHeadset? = null
    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = proxy as BluetoothHeadset
                Log.d("BatteryMonitorService", "BluetoothHeadset profile connected")
                updateEarphoneBatteryFromProfile()
            }
        }
        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = null
                Log.d("BatteryMonitorService", "BluetoothHeadset profile disconnected")
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): BatteryMonitorService = this@BatteryMonitorService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var database: AppDatabase
    private lateinit var batteryDao: com.echeeze.wearbatterymonitor.data.BatteryDao

    private val CHANNEL_ID = "battery_monitor_channel"
    private val NOTIFICATION_ID = 1
    private val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"

    var currentPhonePercent = 0
    var currentPhoneCharging = false
    var currentEarphonePercent: Int? = null
    var currentEarphoneName: String? = null

    val phoneHistory = mutableListOf<BatteryEntry>()
    val watchHistory = mutableListOf<BatteryEntry>()
    val earphoneHistory = mutableListOf<BatteryEntry>()

    private var watchUpdateTimer: Timer? = null
    private var lastWatchRequestTime = 0L

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    currentPhonePercent = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0
                    currentPhoneCharging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
                    addHistory(phoneHistory, "phone", currentPhonePercent, currentPhoneCharging, Build.MODEL, "BATTERY_CHANGED")
                    
                    val now = System.currentTimeMillis()
                    if (now - lastWatchRequestTime > 60 * 1000) {
                        requestWatchBatteryUpdate()
                    }
                }
                ACTION_BATTERY_LEVEL_CHANGED -> {
                    updateEarphoneInfo(intent)
                    currentEarphonePercent?.let { addHistory(earphoneHistory, "earphone", it, false, currentEarphoneName, "BT_BATTERY_CHANGED") }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.d("BatteryMonitorService", "Bluetooth ACL connected")
                    updateEarphoneInfo(intent)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Log.d("BatteryMonitorService", "Bluetooth ACL disconnected")
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        if (device?.name == currentEarphoneName) {
                            currentEarphonePercent = null; currentEarphoneName = null
                            addHistory(earphoneHistory, "earphone", -1, false, null, "BT_CONNECTION")
                        }
                    }
                }
            }
            syncAllData()
            updateNotification(currentPhonePercent, currentPhoneCharging)
        }
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        batteryDao = database.batteryDao()
        loadHistoryFromDb()
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification(0, false))
        fetchInitialBatteryState()
        fetchInitialBluetoothState()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(ACTION_BATTERY_LEVEL_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter?.getProfileProxy(this, profileListener, BluetoothProfile.HEADSET)

        Wearable.getDataClient(this).addListener(this)
        syncAllData()
        val sharedPref = getSharedPreferences("MainActivity", MODE_PRIVATE)
        updateIntervalSeconds = sharedPref.getInt("update_interval_seconds", 60)
        startWatchUpdateTimer(updateIntervalSeconds)
    }

    private var updateIntervalSeconds = 60

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
                    
                    // Watch heartbeat: Record -1 if no recent data
                    val lastWatch = watchHistory.lastOrNull()
                    if (lastWatch == null || System.currentTimeMillis() - lastWatch.timestamp > intervalSeconds * 2000L) {
                        // For watch, deviceName is handled separately in watchHistory list, but we can store it if we have it
                        addHistory(watchHistory, "watch", -1, false, null, "PERIODIC_INTERVAL", intervalSeconds)
                    }

                    // Earphone heartbeat: Record -1 if not connected
                    if (currentEarphonePercent == null) {
                        addHistory(earphoneHistory, "earphone", -1, false, null, "PERIODIC_INTERVAL", intervalSeconds)
                    }

                    requestWatchBatteryUpdate()
                    syncAllData()
                }
            }, 1000, intervalSeconds * 1000L)
        }
    }

    private fun requestWatchBatteryUpdate() {
        Log.d("BatteryMonitorService", "Requesting watch battery update")
        lastWatchRequestTime = System.currentTimeMillis()
        val nodeClient = Wearable.getNodeClient(this)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            Log.d("BatteryMonitorService", "Found ${nodes.size} connected nodes")
            nodes.forEach { node ->
                Log.d("BatteryMonitorService", "Sending request to node: ${node.displayName} (${node.id})")
                Wearable.getMessageClient(this).sendMessage(node.id, "/request_battery_update", null)
            }
        }
    }

    private fun fetchInitialBatteryState() {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        currentPhonePercent = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0
        currentPhoneCharging = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
        addHistory(phoneHistory, "phone", currentPhonePercent, currentPhoneCharging, Build.MODEL, "INITIAL_FETCH")
    }

    private fun fetchInitialBluetoothState() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter ?: return
        
        // Find connected devices
        val connectedDevices = adapter.bondedDevices.filter { _ ->
            // There's no direct "isConnected" on BluetoothDevice, so we look for those with battery level available or generally bonded
            // More accurately, we could use BluetoothProfile.getConnectedDevices() but that requires more setup
            true 
        }
        
        // Check for devices with battery info
        connectedDevices.forEach { device ->
            // Attempt to get battery level via reflection or hidden API if available (device.batteryLevel is hidden on some Android versions)
            try {
                val method = device.javaClass.getMethod("getBatteryLevel")
                val level = method.invoke(device) as Int
                if (level != -1) {
                    currentEarphonePercent = level
                    currentEarphoneName = device.name
                    addHistory(earphoneHistory, "earphone", level, false, device.name, "INITIAL_FETCH")
                    return@forEach
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun addHistory(list: MutableList<BatteryEntry>, type: String, percent: Int, isCharging: Boolean, deviceName: String? = null, recordReason: String? = null, intervalSeconds: Int? = null) {
        val entry = BatteryEntry(type, percent, System.currentTimeMillis(), isCharging, deviceName, recordReason, intervalSeconds)
        list.add(entry)
        if (list.size > Constants.MAX_HISTORY_SIZE) list.removeAt(0)
        
        serviceScope.launch {
            batteryDao.insert(entry)
            batteryDao.trimHistory(type, Constants.MAX_HISTORY_SIZE)
        }
    }

    private fun loadHistoryFromDb() {
        serviceScope.launch {
            val phoneData = batteryDao.getHistoryByType("phone")
            val watchData = batteryDao.getHistoryByType("watch")
            val earphoneData = batteryDao.getHistoryByType("earphone")
            
            withContext(Dispatchers.Main) {
                phoneHistory.clear(); phoneHistory.addAll(phoneData)
                watchHistory.clear(); watchHistory.addAll(watchData)
                earphoneHistory.clear(); earphoneHistory.addAll(earphoneData)
                syncAllData()
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d("BatteryMonitorService", "onDataChanged received ${dataEvents.count} events")
        for (event in dataEvents) {
            Log.d("BatteryMonitorService", "Event path: ${event.dataItem.uri.path}")
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/watch_battery") {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val percent = dataMap.getInt("watch_percent")
                val isCharging = dataMap.getBoolean("watch_is_charging")
                Log.i("BatteryMonitorService", "Received watch battery: $percent%")
                // We don't have easy access to watch name here without fetching connected nodes, 
                // but we can try to get it if needed. However, watch history is mostly for the graph.
                addHistory(watchHistory, "watch", percent, isCharging, null, "DATA_SYNC")
                syncAllData()
            }
        }
        dataEvents.release()
    }

    private fun updateEarphoneInfo(intent: Intent) {
        Log.d("BatteryMonitorService", "updateEarphoneInfo called with action: ${intent.action}")
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val batteryLevel = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
        
        if (device != null) {
            // Filter out Wear OS devices (Watables) from earphone section
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                val deviceClass = device.bluetoothClass?.deviceClass ?: 0
                // Wearable device class is 0x0700, Wrist Watch is 0x0704
                val isWatch = (deviceClass and 0x0700) == 0x0700
                if (isWatch) {
                    Log.d("BatteryMonitorService", "Ignoring watch device in earphone info: ${device.name}")
                    return
                }
            }

            if (batteryLevel != -1) {
                Log.d("BatteryMonitorService", "Earphone battery from intent: $batteryLevel%")
                currentEarphonePercent = batteryLevel
            }
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                currentEarphoneName = device.name
                Log.d("BatteryMonitorService", "Device name: ${device.name}")
            }
        } else {
            // If device info is missing in intent, try profile
            updateEarphoneBatteryFromProfile()
        }
    }

    private fun updateEarphoneBatteryFromProfile() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        
        bluetoothHeadset?.connectedDevices?.firstOrNull()?.let { device ->
            // Filter out watches if we can determine the device class
            val deviceClass = device.bluetoothClass?.deviceClass ?: 0
            if ((deviceClass and 0x0700) == 0x0700) {
                return@let
            }
            // Attempt to get battery level via reflection or standard method if available
            try {
                // Some devices support standard getBatteryLevel() via reflection or hidden API
                val method = device.javaClass.getMethod("getBatteryLevel")
                val level = method.invoke(device) as Int
                if (level != -1) {
                    Log.d("BatteryMonitorService", "Earphone battery from profile: $level%")
                    currentEarphonePercent = level
                    currentEarphoneName = device.name
                    addHistory(earphoneHistory, "earphone", level, false, device.name, "INITIAL_FETCH")
                    syncAllData()
                }
            } catch (e: Exception) {
                Log.d("BatteryMonitorService", "Could not get battery level via reflection: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        watchUpdateTimer?.cancel()
        serviceScope.cancel()
        unregisterReceiver(batteryReceiver)
        bluetoothHeadset?.let {
            val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter?.closeProfileProxy(BluetoothProfile.HEADSET, it)
        }
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Battery Monitor Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(percent: Int, isCharging: Boolean): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        val chargingText = if (isCharging) " (Charging)" else ""
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Battery Monitoring Active")
            .setContentText("Current Battery: $percent%$chargingText")
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(percent: Int, isCharging: Boolean) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(percent, isCharging))
    }

    private fun syncAllData() {
        val dataClient = Wearable.getDataClient(this)
        val putDataMapReq = PutDataMapRequest.create("/battery_status")
        putDataMapReq.dataMap.putInt("battery_percent", currentPhonePercent)
        putDataMapReq.dataMap.putBoolean("is_charging", currentPhoneCharging)
        currentEarphonePercent?.let { putDataMapReq.dataMap.putInt("earphone_percent", it) }
        currentEarphoneName?.let { putDataMapReq.dataMap.putString("earphone_name", it) }
        putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis())
        
        // Add history data
        putDataMapReq.dataMap.putDataMapArrayList("phone_history", historyToDataMapList(phoneHistory))
        putDataMapReq.dataMap.putDataMapArrayList("watch_history", historyToDataMapList(watchHistory))
        putDataMapReq.dataMap.putDataMapArrayList("earphone_history", historyToDataMapList(earphoneHistory))

        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq)
    }

    private fun historyToDataMapList(history: List<BatteryEntry>): ArrayList<com.google.android.gms.wearable.DataMap> {
        val list = ArrayList<com.google.android.gms.wearable.DataMap>()
        history.forEach { entry ->
            val dm = com.google.android.gms.wearable.DataMap()
            dm.putInt("p", entry.percent)
            dm.putLong("t", entry.timestamp)
            dm.putBoolean("c", entry.isCharging)
            list.add(dm)
        }
        return list
    }
}
