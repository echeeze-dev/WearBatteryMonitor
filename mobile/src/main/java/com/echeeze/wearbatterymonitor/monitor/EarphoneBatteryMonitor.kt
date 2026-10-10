package com.echeeze.wearbatterymonitor.monitor

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat

/**
 * 接続された Bluetooth イヤホン等のデバイスのバッテリーレベルおよびデバイス名を検出・監視するクラスです。
 *
 * [BluetoothProfile.HEADSET] プロファイルおよび Bluetooth 接続・切断イベントインテントを解釈し、
 * イヤホンの状態変化を通知します。
 *
 * @property context アプリケーションコンテキスト
 * @property onEarphoneChanged イヤホン状態更新時に発火するコールバック関数 (percent, name)
 */
class EarphoneBatteryMonitor(
    private val context: Context,
    private val onEarphoneChanged: (percent: Int?, name: String?, reason: String) -> Unit
) {

    private var bluetoothHeadset: BluetoothHeadset? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = proxy as BluetoothHeadset
                Log.d("EarphoneBatteryMonitor", "BluetoothHeadset profile connected")
                updateFromProfile()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = null
                Log.d("EarphoneBatteryMonitor", "BluetoothHeadset profile disconnected")
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> {
                    updateFromIntent(intent)
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    updateFromIntent(intent)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    handleDisconnection(intent)
                }
            }
        }
    }

    /**
     * Bluetooth プロファイル接続および BroadcastReceiver の監視を開始します。
     */
    fun startMonitoring() {
        val filter = IntentFilter().apply {
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(bluetoothReceiver, filter)
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothManager?.adapter?.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
        updateFromProfile()
    }

    /**
     * Bluetooth 監視およびプロファイル接続を解除します。
     */
    fun stopMonitoring() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: IllegalArgumentException) {
            Log.d("EarphoneBatteryMonitor", "Receiver unregister ignored", e)
        }

        bluetoothHeadset?.let {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothManager?.adapter?.closeProfileProxy(BluetoothProfile.HEADSET, it)
            bluetoothHeadset = null
        }
    }

    /**
     * 指定された Bluetooth デバイスがスマートウォッチ (Wear OS 端末) であるかを判定します。
     *
     * @param device Bluetooth デバイス
     * @return スマートウォッチと判定された場合は true
     */
    private fun isWatchDevice(device: BluetoothDevice): Boolean {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val deviceClass = device.bluetoothClass?.deviceClass ?: 0
        val majorClass = device.bluetoothClass?.majorDeviceClass ?: 0

        // Bluetooth Device / Major Class による判定 (Wearable: 0x0700, Wrist Watch: 0x0704)
        return ((deviceClass and 0x0700) == 0x0700) || (majorClass == 0x0700) || (deviceClass == 0x0704)
    }

    /**
     * Bluetooth インテントからデバイス名とバッテリーレベルを抽出して状態を更新します。
     *
     * @param intent Bluetooth ブロードキャストインテント
     */
    fun updateFromIntent(intent: Intent) {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        val batteryLevel = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
        val reason = if (intent.action == "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED") "BT_BATTERY_CHANGED" else "BT_CONNECTION"

        if (device != null) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                // スマートウォッチ端末はイヤホン一覧から確実に除外
                if (isWatchDevice(device)) return

                val percent = if (batteryLevel != -1) batteryLevel else null
                val name = device.name
                onEarphoneChanged(percent, name, reason)
                return
            }
        }
        updateFromProfile()
    }

    /**
     * BluetoothHeadset プロファイルから接続済みデバイスのバッテリー残量を更新します。
     */
    fun updateFromProfile() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

        val activeDevice = bluetoothHeadset?.connectedDevices?.firstOrNull { device ->
            !isWatchDevice(device)
        }

        if (activeDevice != null) {
            try {
                val method = activeDevice.javaClass.getMethod("getBatteryLevel")
                val level = method.invoke(activeDevice) as Int
                val percent = if (level != -1) level else null
                onEarphoneChanged(percent, activeDevice.name, "BT_CONNECTION")
            } catch (e: Exception) {
                Log.d("EarphoneBatteryMonitor", "Could not reflect getBatteryLevel: ${e.message}")
                onEarphoneChanged(null, activeDevice.name, "BT_CONNECTION")
            }
        } else {
            // 接続中のデバイスが存在しない場合はリアルタイムに未接続状態を通知する
            onEarphoneChanged(null, null, "BT_CONNECTION")
        }
    }

    private fun handleDisconnection(intent: Intent) {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val deviceName = device?.name
            onEarphoneChanged(null, deviceName, "BT_DISCONNECT")
        } else {
            onEarphoneChanged(null, null, "BT_DISCONNECT")
        }
    }
}
