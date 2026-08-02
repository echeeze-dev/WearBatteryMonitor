package com.echeeze.wearbatterymonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class BatteryMonitorService : Service() {

    private val CHANNEL_ID = "battery_monitor_channel"
    private val NOTIFICATION_ID = 1
    private val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"

    private var currentPhonePercent = 0
    private var currentPhoneCharging = false
    private var currentEarphonePercent: Int? = null
    private var currentEarphoneL: Int? = null
    private var currentEarphoneR: Int? = null
    private var currentEarphoneCase: Int? = null
    private var currentEarphoneName: String? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    currentPhonePercent = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0
                    currentPhoneCharging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
                }
                ACTION_BATTERY_LEVEL_CHANGED -> {
                    updateEarphoneInfo(intent)
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
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(ACTION_BATTERY_LEVEL_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
        syncAllData()
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

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
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq)
    }
}
