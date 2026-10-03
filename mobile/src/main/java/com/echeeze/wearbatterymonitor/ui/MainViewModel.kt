package com.echeeze.wearbatterymonitor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echeeze.wearbatterymonitor.data.BatteryEntry
import com.echeeze.wearbatterymonitor.data.BatteryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * モバイルメイン画面の UI 状態 ([MainUiState]) の管理および操作イベントの受配を担当する ViewModel です。
 *
 * @property repository バッテリーデータアクセスを担当するリポジトリ
 */
class MainViewModel(
    private val repository: BatteryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())

    /**
     * UI 状態の読み取り専用 StateFlow です。
     */
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /**
     * データベースから初期履歴データを復元し、UI 状態を更新します。
     */
    fun loadHistoryFromDb() {
        viewModelScope.launch(Dispatchers.IO) {
            val phoneData = repository.getHistoryByType("phone")
            val watchData = repository.getHistoryByType("watch")
            val earphoneData = repository.getHistoryByType("earphone")

            withContext(Dispatchers.Main) {
                _uiState.update { current ->
                    current.copy(
                        phoneHistory = phoneData,
                        watchHistory = watchData,
                        earphoneHistory = earphoneData
                    )
                }

                // Compose のフレーム計算完了待機ディレイ
                delay(200.milliseconds)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * サービス起動状態を設定します。
     *
     * @param running 起動中フラグ
     */
    fun setServiceRunning(running: Boolean) {
        _uiState.update { it.copy(isServiceRunning = running) }
    }

    /**
     * グラフ表示時間範囲を更新します。
     *
     * @param hours 表示範囲時間 (0は全期間)
     */
    fun setGraphRangeHours(hours: Int) {
        _uiState.update { it.copy(graphRangeHours = hours) }
    }

    /**
     * 更新インターバル秒数を更新します。
     *
     * @param seconds 更新インターバル (秒)
     */
    fun setUpdateIntervalSeconds(seconds: Int) {
        _uiState.update { it.copy(updateIntervalSeconds = seconds) }
    }

    /**
     * スマホのバッテリー状態を更新します。
     *
     * @param percent 残量 (%)
     * @param isCharging 充電中フラグ
     */
    fun updatePhoneBattery(percent: Int, isCharging: Boolean) {
        _uiState.update { it.copy(phonePercent = percent, phoneIsCharging = isCharging) }
    }

    /**
     * イヤホンのバッテリー状態を更新します。
     *
     * @param percent 残量 (%)
     * @param name デバイス名
     */
    fun updateEarphoneBattery(percent: Int?, name: String?) {
        _uiState.update { it.copy(earphonePercent = percent, earphoneName = name) }
    }

    /**
     * ウォッチのバッテリー状態を更新します。
     *
     * @param percent 残量 (%)
     * @param isCharging 充電中フラグ
     */
    fun updateWatchBattery(percent: Int?, isCharging: Boolean) {
        _uiState.update { it.copy(watchPercent = percent, watchIsCharging = isCharging) }
    }

    /**
     * 接続中ウォッチのデバイス名を更新します。
     *
     * @param name ウォッチのデバイス名
     */
    fun updateWatchName(name: String?) {
        _uiState.update { it.copy(watchName = name) }
    }

    /**
     * ウォッチのリアルタイム接続状態を更新します。
     *
     * @param isConnected リアルタイム接続中フラグ
     * @param watchName ウォッチのデバイス名
     */
    fun updateWatchConnected(isConnected: Boolean, watchName: String? = null) {
        _uiState.update { current ->
            current.copy(
                isWatchConnected = isConnected,
                watchName = watchName ?: current.watchName
            )
        }
    }

    /**
     * 履歴リストに新しいエントリーを追加します。
     *
     * @param entry バッテリーエントリー
     */
    fun addHistoryEntry(entry: BatteryEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertEntry(entry)
            val updated = repository.getHistoryByType(entry.deviceType)
            withContext(Dispatchers.Main) {
                _uiState.update { current ->
                    when (entry.deviceType) {
                        "phone" -> current.copy(phoneHistory = updated)
                        "watch" -> current.copy(watchHistory = updated)
                        "earphone" -> current.copy(earphoneHistory = updated)
                        else -> current
                    }
                }
            }
        }
    }
}
