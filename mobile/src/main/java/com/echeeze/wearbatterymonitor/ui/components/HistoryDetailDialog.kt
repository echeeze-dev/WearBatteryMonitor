package com.echeeze.wearbatterymonitor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.echeeze.wearbatterymonitor.data.BatteryEntry
import com.echeeze.wearbatterymonitor.util.CrashLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 過去のバッテリー履歴のログ一覧をダイアログ形式で詳細表示する UI コンポーネントです。
 *
 * @param title ダイアログタイトル (デバイス名)
 * @param history バッテリー履歴リスト
 * @param onDismiss ダイアログを閉じる際に発火するコールバック関数
 */
@Composable
fun HistoryDetailDialog(title: String, history: List<BatteryEntry>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()) }
    var crashLogText by remember { mutableStateOf(CrashLogger.getCrashLog(context)) }
    var showCrashLogView by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = if (showCrashLogView) "⚠ Crash Log" else "$title History", style = MaterialTheme.typography.titleLarge)
                    if (crashLogText != null) {
                        TextButton(
                            onClick = { showCrashLogView = !showCrashLogView }
                        ) {
                            Text(if (showCrashLogView) "History" else "⚠ Crash Log", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (showCrashLogView && crashLogText != null) {
                    Column(modifier = Modifier.weight(1f)) {
                        val scrollState = rememberScrollState()
                        Text(
                            text = crashLogText!!,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                CrashLogger.clearCrashLog(context)
                                crashLogText = null
                                showCrashLogView = false
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Clear Crash Log")
                        }
                    }
                } else {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).verticalScrollbar(listState)
                    ) {
                        items(history.asReversed()) { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sdf.format(Date(entry.timestamp)).substringAfter(" "),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(70.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    val reasonTitle = when (entry.recordReason) {
                                        "BATTERY_CHANGED" -> "⚡ Battery"
                                        "PERIODIC_INTERVAL" -> "⏱ Periodic" + (entry.intervalSeconds?.let { " (${formatInterval(it)})" } ?: "")
                                        "INITIAL_FETCH" -> "🚀 Initial"
                                        "WEAR_REQUEST" -> "⌚ Wear Req"
                                        "BT_BATTERY_CHANGED", "BT_UPDATE" -> "🎧 BT Battery"
                                        "BT_CONNECTION" -> "🔗 Bluetooth"
                                        "DATA_SYNC" -> "🔄 Sync"
                                        "SYSTEM_SHUTDOWN" -> "🛑 Shutdown"
                                        "BATTERY_LOW" -> "⚠️ Low Battery"
                                        "WATCH_SHUTDOWN" -> "⌚ Watch Shutdown"
                                        else -> if (entry.percent == -1) "Disconnected" else "Record"
                                    }

                                    Text(
                                        text = reasonTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    val details = mutableListOf<String>()
                                    if (entry.deviceName != null) {
                                        details.add(entry.deviceName)
                                    }
                                    if (entry.isCharging) {
                                        details.add("Charging")
                                    }
                                    if (entry.percent == -1) {
                                        details.add(if (entry.deviceName != null) "Connected" else "Disconnected")
                                    }

                                    if (details.isNotEmpty()) {
                                        Text(
                                            text = details.joinToString(" • "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (entry.isCharging) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                Text(
                                    text = if (entry.percent == -1) "—" else "${entry.percent}%",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.width(50.dp)
                                )
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}

/**
 * 描画領域の右端に縦型スクロールバーを表示する Modifier 拡張関数です。
 *
 * @param state LazyListState
 * @param width スクロールバーの幅
 */
fun Modifier.verticalScrollbar(
    state: LazyListState,
    width: Dp = 4.dp
): Modifier = drawWithContent {
    drawContent()

    val firstVisibleItem = state.layoutInfo.visibleItemsInfo.firstOrNull()
    if (firstVisibleItem != null) {
        val totalItemsCount = state.layoutInfo.totalItemsCount
        val visibleItemsCount = state.layoutInfo.visibleItemsInfo.size

        if (totalItemsCount > visibleItemsCount) {
            val elementHeight = this.size.height / totalItemsCount
            val scrollbarHeight = visibleItemsCount * elementHeight
            val scrollbarOffsetY = firstVisibleItem.index * elementHeight

            drawRect(
                color = Color.Gray.copy(alpha = 0.5f),
                topLeft = Offset(this.size.width - width.toPx(), scrollbarOffsetY),
                size = Size(width.toPx(), scrollbarHeight)
            )
        }
    }
}

private fun formatInterval(seconds: Int): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> {
            val m = seconds / 60
            val s = seconds % 60
            if (s == 0) "${m}m" else "${m}m ${s}s"
        }
        else -> {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            if (m == 0) "${h}h" else "${h}h ${m}m"
        }
    }
}
