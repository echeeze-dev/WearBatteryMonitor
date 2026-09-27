package com.echeeze.wearbatterymonitor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.echeeze.wearbatterymonitor.data.BatteryEntry
import com.echeeze.wearbatterymonitor.data.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * スマホおよびウォッチのバッテリー残量、充電状態、推移グラフを表示するセクション UI です。
 *
 * @param icon デバイスを表すアイコン
 * @param title デバイスタイトル名
 * @param percent バッテリー残量 (%)
 * @param isCharging 充電中フラグ
 * @param history バッテリー履歴リスト
 * @param isDisconnected 未接続フラグ
 * @param rangeHours グラフ表示時間範囲
 * @param onGraphClick グラフクリック時に発火するコールバック関数
 */
@Composable
fun BatterySection(
    icon: ImageVector,
    title: String,
    percent: Int,
    isCharging: Boolean,
    history: List<BatteryEntry>,
    isDisconnected: Boolean = false,
    rangeHours: Int,
    onGraphClick: () -> Unit
) {
    val historyDeviceName = history.lastOrNull { it.deviceName != null }?.deviceName
    val displayTitle = if (!isDisconnected && (title == "Smartwatch" || title.isBlank()) && historyDeviceName != null) {
        historyDeviceName
    } else {
        title
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = displayTitle, style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            text = if (isDisconnected) "Disconnected" else "$percent%",
            style = MaterialTheme.typography.displayLarge,
            color = if (isDisconnected) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
        )
        if (!isDisconnected && isCharging) {
            Text("Charging", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF4CAF50))
        }

        if (history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.height(120.dp).fillMaxWidth(0.9f).clickable { onGraphClick() }) {
                HistoryGraph(history, rangeHours, scrollState)
            }
            DebugInfoText(history)
        }
    }
}

/**
 * 履歴データの件数およびデータ収集期間を表示するデバッグ・サマリーテキストです。
 *
 * @param history バッテリー履歴リスト
 */
@Composable
fun DebugInfoText(history: List<BatteryEntry>) {
    val sdf = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()) }
    val count = history.size
    val oldest = history.firstOrNull()?.timestamp?.let { sdf.format(Date(it)) } ?: "N/A"
    val newest = history.lastOrNull()?.timestamp?.let { sdf.format(Date(it)) } ?: "N/A"

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = "Data: $count / ${Constants.MAX_HISTORY_SIZE}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = "Range: $oldest - $newest",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
