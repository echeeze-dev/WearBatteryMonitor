package com.echeeze.wearbatterymonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
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
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class BatteryMonitorService : Service(), DataClient.OnDataChangedListener {

    inner class LocalBinder : Binder() {
        fun getService(): BatteryMonitorService = this@BatteryMonitorService
    }

    private val binder = LocalBinder()

    private val CHANNEL_ID = "battery_monitor_channel"
    private val NOTIFICATION_ID = 1
    private val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"

    var currentPhonePercent = 0
    var currentPhoneCharging = false
    var currentEarphonePercent: Int? = null
    var currentEarphoneL: Int? = null
    var currentEarphoneR: Int? = null
    var currentEarphoneCase: Int? = null
    var currentEarphoneName: String? = null

    val phoneHistory = mutableListOf<BatteryEntry>()
    val watchHistory = mutableListOf<BatteryEntry>()
    val earphoneHistory = mutableListOf<BatteryEntry>()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    currentPhonePercent = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0
                    currentPhoneCharging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
                    addHistory(phoneHistory, "phone", currentPhonePercent, currentPhoneCharging)
                }
                ACTION_BATTERY_LEVEL_CHANGED -> {
                    updateEarphoneInfo(intent)
                    currentEarphonePercent?.let { addHistory(earphoneHistory, "earphone", it, false) }
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
                            currentEarphonePercent = null; currentEarphoneL = null; currentEarphoneR = null; currentEarphoneCase = null; currentEarphoneName = null
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
            registerReceiver(batteryReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
        Wearable.getDataClient(this).addListener(this)
        syncAllData()
    }

    private fun fetchInitialBatteryState() {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        currentPhonePercent = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0
        currentPhoneCharging = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
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
                    addHistory(earphoneHistory, "earphone", level, false)
                    return@forEach
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun addHistory(list: MutableList<BatteryEntry>, type: String, percent: Int, isCharging: Boolean) {
        list.add(BatteryEntry(type, percent, System.currentTimeMillis(), isCharging))
        if (list.size > 500) list.removeAt(0)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/watch_battery") {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val percent = dataMap.getInt("watch_percent")
                val isCharging = dataMap.getBoolean("watch_is_charging")
                addHistory(watchHistory, "watch", percent, isCharging)
                syncAllData()
            }
        }
    }

    private fun updateEarphoneInfo(intent: Intent) {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val batteryLevel = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
        val bL = intent.getIntExtra("android.bluetooth.device.extra.LEFT_BATTERY_LEVEL", -1)
        val bR = intent.getIntExtra("android.bluetooth.device.extra.RIGHT_BATTERY_LEVEL", -1)
        val bC = intent.getIntExtra("android.bluetooth.device.extra.CASE_BATTERY_LEVEL", -1)
        
        if (device != null) {
            if (batteryLevel != -1) currentEarphonePercent = batteryLevel
            if (bL != -1) currentEarphoneL = bL
            if (bR != -1) currentEarphoneR = bR
            if (bC != -1) currentEarphoneCase = bC
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                currentEarphoneName = device.name
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
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
        currentEarphoneL?.let { putDataMapReq.dataMap.putInt("earphone_l", it) }
        currentEarphoneR?.let { putDataMapReq.dataMap.putInt("earphone_r", it) }
        currentEarphoneCase?.let { putDataMapReq.dataMap.putInt("earphone_case", it) }
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
        history.takeLast(100).forEach { entry -> // Optimization: limit sync history size
            val dm = com.google.android.gms.wearable.DataMap()
            dm.putInt("p", entry.percent)
            dm.putLong("t", entry.timestamp)
            dm.putBoolean("c", entry.isCharging)
            list.add(dm)
        }
        return list
    }
}
