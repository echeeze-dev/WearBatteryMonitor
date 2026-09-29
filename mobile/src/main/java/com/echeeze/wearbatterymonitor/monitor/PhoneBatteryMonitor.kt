package com.echeeze.wearbatterymonitor.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log

/**
 * スマートフォン本体のバッテリー状態（残量・充電状態等）を監視・取得するクラスです。
 *
 * [Intent.ACTION_BATTERY_CHANGED] ブロードキャストを受信し、
 * バッテリー残量 (%) および充電状態をリアルタイムに計算して通知します。
 *
 * @property context アプリケーションコンテキスト
 * @property onBatteryChanged バッテリー状態変化時に発火するコールバック関数
 */
class PhoneBatteryMonitor(
    private val context: Context,
    private val onBatteryChanged: (percent: Int, isCharging: Boolean) -> Unit
) {

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val (percent, isCharging) = parseBatteryIntent(intent)
                onBatteryChanged(percent, isCharging)
            }
        }
    }

    /**
     * バッテリー監視用の BroadcastReceiver を登録し、最新のバッテリー状態を取得します。
     *
     * @return 登録直後の初期バッテリー状態（残量 % と充電フラグの Pair）
     */
    fun startMonitoring(): Pair<Int, Boolean> {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val initialIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(batteryReceiver, filter)
        }
        return parseBatteryIntent(initialIntent)
    }

    /**
     * バッテリー監視用の BroadcastReceiver を解除します。
     */
    fun stopMonitoring() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
            Log.d("PhoneBatteryMonitor", "Receiver unregister ignored", e)
        }
    }

    /**
     * [Intent.ACTION_BATTERY_CHANGED] インテントからバッテリー残量と充電状態を解析します。
     *
     * @param intent バッテリーインテント
     * @return 残量 (%) と充電状態 (isCharging) の Pair
     */
    fun parseBatteryIntent(intent: Intent?): Pair<Int, Boolean> {
        if (intent == null) return Pair(0, false)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
        return Pair(percent, isCharging)
    }
}
