package com.echeeze.wearbatterymonitor.sync

import android.content.Context
import android.util.Log
import com.echeeze.wearbatterymonitor.data.BatteryEntry
import com.echeeze.wearbatterymonitor.data.BatteryRepository
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Google Wearable Data Layer API を通じた Wear OS 端末とのデータ送信・同期リクエスト受信を管理するクラスです。
 *
 * @property context アプリケーションコンテキスト
 * @property repository バッテリー履歴データベースを操作するリポジトリ
 * @property onWatchBatteryReceived Watch からバッテリーデータを受信した際のコールバック関数
 * @property onRequestBatteryUpdate Watch から更新リクエスト（/request_battery_update）を受信した際のコールバック関数
 */
class WearDataSyncer(
    private val context: Context,
    private val repository: BatteryRepository,
    private val scope: CoroutineScope,
    private val onWatchBatteryReceived: (percent: Int, isCharging: Boolean, deviceName: String?, recordReason: String?) -> Unit,
    private val onRequestBatteryUpdate: () -> Unit
) : DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener {

    /**
     * Wearable API の DataClient および MessageClient イベントリスナーを登録します。
     */
    fun startSyncing() {
        Wearable.getDataClient(context).addListener(this)
        Wearable.getMessageClient(context).addListener(this)
    }

    /**
     * Wearable API のイベントリスナーを解除します。
     */
    fun stopSyncing() {
        Wearable.getDataClient(context).removeListener(this)
        Wearable.getMessageClient(context).removeListener(this)
    }

    /**
     * 最新のスマホ、ウォッチ、イヤホンのバッテリー状態および履歴を Wear OS 端末へ非同期同期します。
     *
     * @param phonePercent スマホのバッテリー残量 (%)
     * @param isCharging スマホの充電中フラグ
     * @param earphonePercent イヤホンのバッテリー残量 (%)
     * @param earphoneName イヤホンのデバイス名
     * @param phoneHistory スマホのバッテリー履歴リスト
     * @param watchHistory ウォッチのバッテリー履歴リスト
     * @param earphoneHistory イヤホンのバッテリー履歴リスト
     */
    fun syncStatusToWear(
        phonePercent: Int,
        isCharging: Boolean,
        earphonePercent: Int?,
        earphoneName: String?,
        phoneHistory: List<BatteryEntry>,
        watchHistory: List<BatteryEntry> = emptyList(),
        earphoneHistory: List<BatteryEntry> = emptyList(),
        rangeHours: Int = 3
    ) {
        scope.launch(Dispatchers.IO) {
            repository.syncStatusToWear(
                phonePercent = phonePercent,
                isCharging = isCharging,
                earphonePercent = earphonePercent,
                earphoneName = earphoneName,
                phoneHistory = phoneHistory,
                watchHistory = watchHistory,
                earphoneHistory = earphoneHistory,
                rangeHours = rangeHours
            )
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d("WearDataSyncer", "onDataChanged received ${dataEvents.count} events")
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/watch_battery") {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val percent = dataMap.getInt("watch_percent")
                val isCharging = dataMap.getBoolean("watch_is_charging")
                val deviceName = if (dataMap.containsKey("device_name")) dataMap.getString("device_name") else null
                val recordReason = if (dataMap.containsKey("record_reason")) dataMap.getString("record_reason") else "DATA_SYNC"

                Log.i("WearDataSyncer", "Received watch battery: $percent% from $deviceName ($recordReason)")
                onWatchBatteryReceived(percent, isCharging, deviceName, recordReason)
            }
        }
        dataEvents.release()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/request_battery_update") {
            Log.i("WearDataSyncer", "Received /request_battery_update from Wear")
            onRequestBatteryUpdate()
        }
    }
}
