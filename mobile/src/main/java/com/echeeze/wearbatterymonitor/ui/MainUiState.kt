package com.echeeze.wearbatterymonitor.ui

import com.echeeze.wearbatterymonitor.data.BatteryEntry

/**
 * モバイルアプリ メイン画面 ([com.echeeze.wearbatterymonitor.MainActivity]) の全 UI 状態を一元保持するデータクラスです。
 *
 * @property isLoading 初期データ読み込み中フラグ
 * @property phonePercent スマホのバッテリー残量 (%)
 * @property phoneIsCharging スマホの充電中フラグ
 * @property phoneHistory スマホのバッテリー履歴リスト
 * @property watchPercent ウォッチのバッテリー残量 (%)
 * @property watchIsCharging ウォッチの充電中フラグ
 * @property watchHistory ウォッチのバッテリー履歴リスト
 * @property earphonePercent イヤホンのバッテリー残量 (%)
 * @property earphoneName イヤホンのデバイス名
 * @property earphoneHistory イヤホンのバッテリー履歴リスト
 * @property isServiceRunning フォアグラウンドサービス起動中フラグ
 * @property graphRangeHours グラフ表示時間範囲 (時間、0は全期間)
 * @property updateIntervalSeconds 定期監視・更新間隔 (秒)
 * @property watchName 接続中ウォッチの端末名
 */
data class MainUiState(
    val isLoading: Boolean = true,
    val phonePercent: Int = 0,
    val phoneIsCharging: Boolean = false,
    val phoneHistory: List<BatteryEntry> = emptyList(),
    val watchPercent: Int? = null,
    val watchIsCharging: Boolean = false,
    val watchHistory: List<BatteryEntry> = emptyList(),
    val earphonePercent: Int? = null,
    val earphoneName: String? = null,
    val earphoneHistory: List<BatteryEntry> = emptyList(),
    val isServiceRunning: Boolean = false,
    val graphRangeHours: Int = 3,
    val updateIntervalSeconds: Int = 60,
    val watchName: String? = null
)
