package com.echeeze.wearbatterymonitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echeeze.wearbatterymonitor.data.BatteryEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * バッテリー残量の時系列推移をスクロール可能な折れ線グラフとして描画する Canvas コンポーネントです。
 *
 * @param history バッテリー履歴リスト
 * @param rangeHours 表示時間範囲 (0は全期間)
 * @param scrollState スクロール状態
 */
@Composable
fun HistoryGraph(history: List<BatteryEntry>, rangeHours: Int, scrollState: ScrollState) {
    if (history.isEmpty()) return

    val now = System.currentTimeMillis()
    val minDataTs = history.minByOrNull { it.timestamp }?.timestamp ?: now
    val latestTimestamp = now
    val totalTimelineDuration = (latestTimestamp - minDataTs).coerceAtLeast(1L)

    val textMeasurer = rememberTextMeasurer()
    val timeSdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val daySdf = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }

    LaunchedEffect(history.size, rangeHours) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    val normalColor = Color(0xFF6200EE)
    val chargingColor = Color(0xFF4CAF50)
    val disconnectedColor = Color.Gray
    val gridColor = Color.Gray.copy(alpha = 0.3f)
    val serviceActiveBgColor = Color.Gray.copy(alpha = 0.1f)
    val boundaryColor = Color.Black.copy(alpha = 0.3f)
    val dayDividerColor = Color.Red.copy(alpha = 0.4f)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportWidthPx = with(LocalDensity.current) { maxWidth.toPx() }

        val canvasWidthPx = if (rangeHours == 0) {
            viewportWidthPx
        } else {
            val pxPerHour = viewportWidthPx / rangeHours.toFloat()
            val totalHours = totalTimelineDuration.toFloat() / 3600000f
            (totalHours * pxPerHour).coerceAtLeast(viewportWidthPx)
        }

        Column(modifier = Modifier.fillMaxSize().horizontalScroll(scrollState)) {
            val canvasWidthDp = with(LocalDensity.current) { canvasWidthPx.toDp() }

            Canvas(modifier = Modifier.height(20.dp).width(canvasWidthDp)) {
                var lastDay = -1
                val calendar = Calendar.getInstance()
                history.forEach { entry ->
                    val x = ((entry.timestamp - minDataTs).toFloat() / totalTimelineDuration) * size.width
                    calendar.timeInMillis = entry.timestamp
                    val currentDay = calendar.get(Calendar.DAY_OF_YEAR)
                    if (lastDay != -1 && currentDay != lastDay) {
                        drawLine(color = dayDividerColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1.dp.toPx())
                        val dayText = daySdf.format(Date(entry.timestamp))
                        val textResult = textMeasurer.measure(dayText, style = TextStyle(fontSize = 10.sp, color = dayDividerColor))
                        drawText(textLayoutResult = textResult, topLeft = Offset(x + 4.dp.toPx(), 2.dp.toPx()))
                    }
                    lastDay = currentDay
                }
            }

            Canvas(modifier = Modifier.weight(1f).width(canvasWidthDp)) {
                val width = size.width
                val height = size.height

                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { ratio ->
                    val y = height * ratio
                    drawLine(color = gridColor, start = Offset(0f, y), end = Offset(width, y), strokeWidth = 1.dp.toPx())
                }

                history.forEach { entry ->
                    val x = ((entry.timestamp - minDataTs).toFloat() / totalTimelineDuration) * width
                    val bgWidth = 3.dp.toPx()
                    drawRect(
                        color = serviceActiveBgColor,
                        topLeft = Offset(x - bgWidth / 2, 0f),
                        size = Size(bgWidth, height)
                    )

                    val statusColor = if (entry.percent >= 0) chargingColor else disconnectedColor
                    drawRect(
                        color = statusColor,
                        topLeft = Offset(x - bgWidth / 2, height - 3.dp.toPx()),
                        size = Size(bgWidth, 3.dp.toPx())
                    )
                }

                var lastPoint: Offset? = null
                val strokePx = 2.5.dp.toPx()
                val pointRadiusPx = 3.dp.toPx()

                history.forEach { entry ->
                    val x = ((entry.timestamp - minDataTs).toFloat() / totalTimelineDuration) * width
                    if (entry.percent >= 0) {
                        val y = height - (entry.percent / 100f * height)
                        val currentPoint = Offset(x, y)
                        val color = if (entry.isCharging) chargingColor else normalColor

                        lastPoint?.let { prev ->
                            drawLine(color = color, start = prev, end = currentPoint, strokeWidth = strokePx)
                        }
                        drawCircle(color = color, radius = pointRadiusPx, center = currentPoint)
                        lastPoint = currentPoint
                    } else {
                        lastPoint?.let { prev ->
                            drawLine(
                                color = disconnectedColor,
                                start = prev,
                                end = Offset(x, prev.y),
                                strokeWidth = strokePx,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }
                        lastPoint = null
                    }
                }

                var lastBoundaryDay = -1
                val calendar = Calendar.getInstance()
                history.forEach { entry ->
                    val x = ((entry.timestamp - minDataTs).toFloat() / totalTimelineDuration) * width
                    calendar.timeInMillis = entry.timestamp
                    val currentDay = calendar.get(Calendar.DAY_OF_YEAR)
                    if (lastBoundaryDay != -1 && currentDay != lastBoundaryDay) {
                        drawLine(color = dayDividerColor, start = Offset(x, 0f), end = Offset(x, height), strokeWidth = 1.dp.toPx())
                    }
                    lastBoundaryDay = currentDay
                }

                // デバイス名テキスト描画（接続開始時 1 回のみ ＋ テキスト＆グラフ高さによる自動段数切り替え）
                var lastDrawnDeviceName: String? = null
                var nameLevelIndex = 0
                val labelColor = Color(0xFF6200EE)

                history.forEach { entry ->
                    val name = entry.deviceName
                    if (!name.isNullOrBlank()) {
                        if (name != lastDrawnDeviceName) {
                            val x = ((entry.timestamp - minDataTs).toFloat() / totalTimelineDuration) * width
                            val textResult = textMeasurer.measure(
                                text = name,
                                style = TextStyle(fontSize = 10.sp, color = labelColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            )
                            val textHeight = textResult.size.height.toFloat().coerceAtLeast(10.dp.toPx())
                            val maxLevels = ((height - textHeight) / textHeight).toInt().coerceIn(2, 5)
                            val currentLevel = nameLevelIndex % maxLevels
                            val levelY = 2.dp.toPx() + (currentLevel * (textHeight * 0.95f))

                            drawText(
                                textLayoutResult = textResult,
                                topLeft = Offset(x + 2.dp.toPx(), levelY)
                            )
                            lastDrawnDeviceName = name
                            nameLevelIndex++
                        }
                    } else {
                        // 明示的な切断ログ時のみクリアし、単なる応答なし (No Response) ログ継続時は前回描画デバイス名を維持
                        if (entry.recordReason == "BT_DISCONNECT" || entry.recordReason == "WATCH_DISCONNECT") {
                            lastDrawnDeviceName = null
                        }
                    }
                }

                val leftmostX = 0f
                drawLine(color = boundaryColor, start = Offset(leftmostX, 0f), end = Offset(leftmostX, height), strokeWidth = 1.5.dp.toPx())
                val rightmostX = width
                drawLine(color = boundaryColor, start = Offset(rightmostX, 0f), end = Offset(rightmostX, height), strokeWidth = 1.5.dp.toPx())
            }

            Canvas(modifier = Modifier.height(20.dp).width(canvasWidthDp)) {
                var lastTimeTextX = -100f
                history.forEach { entry ->
                    val x = ((entry.timestamp - minDataTs).toFloat() / totalTimelineDuration) * size.width
                    if (x - lastTimeTextX > 50.dp.toPx()) {
                        val text = timeSdf.format(Date(entry.timestamp))
                        val textResult = textMeasurer.measure(text, style = TextStyle(fontSize = 10.sp, color = Color.Gray))
                        drawText(textLayoutResult = textResult, topLeft = Offset(x - textResult.size.width / 2, 2.dp.toPx()))
                        lastTimeTextX = x
                    }
                }
            }
        }
    }
}
