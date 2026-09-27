package com.echeeze.wearbatterymonitor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.echeeze.wearbatterymonitor.data.BatteryEntry

/**
 * 接続された Bluetooth イヤホンのバッテリー状態および推移グラフを表示するセクション UI です。
 *
 * @param icon Bluetooth アイコン
 * @param name イヤホンのデバイス名
 * @param percent バッテリー残量 (%)
 * @param history イヤホンのバッテリー履歴リスト
 * @param rangeHours グラフ表示時間範囲
 * @param onGraphClick グラフクリック時に発火するコールバック関数
 */
@Composable
fun EarphoneSection(
    icon: ImageVector,
    name: String?,
    percent: Int?,
    history: List<BatteryEntry>,
    rangeHours: Int,
    onGraphClick: () -> Unit
) {
    val isConnected = name != null

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = name ?: "Bluetooth Device", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            text = if (isConnected) (if (percent != null && percent != -1) "$percent%" else "Connected") else "Disconnected",
            style = MaterialTheme.typography.displayLarge,
            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )

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
