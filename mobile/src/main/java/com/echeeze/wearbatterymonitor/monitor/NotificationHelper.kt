package com.echeeze.wearbatterymonitor.monitor

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.echeeze.wearbatterymonitor.MainActivity

/**
 * バッテリー常駐監視（フォアグラウンドサービス）用の通知チャンネル作成および通知の構築を行うヘルパークラスです。
 *
 * @property context アプリケーションコンテキスト
 */
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "battery_monitor_channel"
        const val NOTIFICATION_ID = 1
    }

    /**
     * フォアグラウンド通知に必要な通知チャンネルを OS に登録します。
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Battery Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * 現在のバッテリー状態を表示するフォアグラウンド通知を生成します。
     *
     * @param percent スマホのバッテリー残量 (%)
     * @param isCharging 充電中フラグ
     * @return 構築された Notification インスタンス
     */
    fun createNotification(percent: Int, isCharging: Boolean): Notification {
        val notificationIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val chargingText = if (isCharging) " (Charging)" else ""

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Battery Monitoring Active")
            .setContentText("Current Battery: $percent%$chargingText")
            .setSmallIcon(R.drawable.ic_lock_idle_low_battery)
            .setContentIntent(pendingIntent)
            .build()
    }

    /**
     * 既存のフォアグラウンド通知の表示内容を最新状態に更新します。
     *
     * @param percent スマホのバッテリー残量 (%)
     * @param isCharging 充電中フラグ
     */
    fun updateNotification(percent: Int, isCharging: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(NOTIFICATION_ID, createNotification(percent, isCharging))
    }
}
