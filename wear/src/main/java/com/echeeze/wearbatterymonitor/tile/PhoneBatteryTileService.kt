package com.echeeze.wearbatterymonitor.tile

import android.content.Context
import android.util.Log
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.Typography.BODY_MEDIUM
import androidx.wear.protolayout.material3.Typography.DISPLAY_MEDIUM
import androidx.wear.protolayout.material3.Typography.LABEL_SMALL
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.types.argb
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val RESOURCES_VERSION = "1"

class PhoneBatteryTileService : TileService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.i("PhoneBatteryTileService", "onCreate")
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        serviceScope.future {
            Log.d("PhoneBatteryTileService", "onTileRequest started")
            val dataClient = Wearable.getDataClient(this@PhoneBatteryTileService)
            
            var percent = 0
            var isCharging = false
            var timestamp = 0L
            var rangeHours = 3
            val history = mutableListOf<Triple<Int, Boolean, Long>>()

            try {
                val dataItems = com.google.android.gms.tasks.Tasks.await(dataClient.dataItems)
                
                // Get range settings
                dataItems.find { it.uri.path == "/settings/graph_range" }?.let { item ->
                    rangeHours = DataMapItem.fromDataItem(item).dataMap.getInt("range_hours", 1)
                }

                val batteryItem = dataItems.find { it.uri.path == "/battery_status" }
                if (batteryItem != null) {
                    val dataMap = DataMapItem.fromDataItem(batteryItem).dataMap
                    percent = dataMap.getInt("battery_percent")
                    isCharging = dataMap.getBoolean("is_charging")
                    timestamp = dataMap.getLong("timestamp")
                    
                    dataMap.getDataMapArrayList("phone_history")?.let { list ->
                        list.forEach { entryMap ->
                            history.add(Triple(entryMap.getInt("p"), entryMap.getBoolean("c"), entryMap.getLong("t")))
                        }
                    }
                }
                dataItems.release()
            } catch (e: Exception) {
                Log.e("PhoneBatteryTileService", "Error fetching data", e)
            }
            
            tile(requestParams, this@PhoneBatteryTileService, percent, isCharging, timestamp, history, rangeHours)
        }

    override fun onTileResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        Futures.immediateFuture(resources(requestParams))
}

private fun resources(requestParams: ResourcesRequest): Resources {
    return Resources.Builder()
        .setVersion(RESOURCES_VERSION)
        .build()
}

private fun tile(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
    percent: Int,
    isCharging: Boolean,
    timestamp: Long,
    history: List<Triple<Int, Boolean, Long>>,
    rangeHours: Int
): TileBuilders.Tile {
    val timeStr = if (timestamp > 0) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    } else "--:--"

    return TileBuilders.Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setTileTimeline(
            TimelineBuilders.Timeline.fromLayoutElement(
                materialScope(context, requestParams.deviceConfiguration) {
                    primaryLayout(
                        mainSlot = {
                            Column.Builder()
                                .addContent(
                                    text(
                                        "Phone".layoutString,
                                        typography = BODY_MEDIUM,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                )
                                .addContent(
                                    text(
                                        "$percent%".layoutString,
                                        typography = DISPLAY_MEDIUM,
                                        color = if (isCharging) 0xFF4CAF50.toInt().argb
                                               else if (percent < 20) colorScheme.error
                                               else colorScheme.primary
                                    )
                                )
                                .addContent(
                                    barChart(history, rangeHours)
                                )
                                .addContent(
                                    Spacer.Builder().setHeight(dp(4f)).build()
                                )
                                .addContent(
                                    text(
                                        ((if (isCharging) "Charging • " else "") + "$timeStr").layoutString,
                                        typography = LABEL_SMALL,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                )
                                .build()
                        }
                    )
                }
            )
        )
        .build()
}

private fun barChart(history: List<Triple<Int, Boolean, Long>>, rangeHours: Int): LayoutElementBuilders.LayoutElement {
    val now = System.currentTimeMillis()
    val oldestTimestamp = history.minByOrNull { it.third }?.third ?: now
    val totalDuration = (now - oldestTimestamp).coerceAtLeast(1L)
    
    val startTime = if (rangeHours == 0) oldestTimestamp else now - (rangeHours * 3600000L)
    val viewportDuration = if (rangeHours == 0) totalDuration else (rangeHours * 3600000L)
    
    val data = history.filter { it.third >= startTime }

    val fullWidth = 120f
    val containerHeight = 24f
    
    val container = Box.Builder()
        .setWidth(dp(fullWidth))
        .setHeight(dp(containerHeight))
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)

    // Draw background grid lines (0%, 50%, 100%)
    val gridColor = ColorBuilders.argb(0x40808080) // 25% Opacity Gray
    
    listOf(0f, containerHeight / 2f, containerHeight - 1f).forEach { y ->
        container.addContent(
            Box.Builder()
                .setWidth(dp(fullWidth))
                .setHeight(dp(1f))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(ModifiersBuilders.Background.Builder().setColor(gridColor).build())
                        .setPadding(ModifiersBuilders.Padding.Builder().setTop(dp(y)).build())
                        .build()
                )
                .build()
        )
    }

    if (data.isEmpty()) {
        return container.build()
    }

    // Downsample data for tile performance (ProtoLayout element limit)
    val targetTileSize = 100
    val displayData = if (data.size > targetTileSize) {
        val step = data.size.toDouble() / targetTileSize
        val result = mutableListOf<Triple<Int, Boolean, Long>>()
        for (i in 0 until targetTileSize) {
            val index = (i * step).toInt().coerceAtMost(data.size - 1)
            result.add(data[index])
        }
        // Ensure last item is always there
        if (result.last().third != data.last().third) {
            result.removeAt(result.size - 1)
            result.add(data.last())
        }
        result
    } else {
        data
    }

    // Dynamic bar width based on reduced data count
    val barWidth = (fullWidth / (displayData.size.coerceAtLeast(1) * 1.5f)).coerceIn(1f, 3f)

    displayData.forEach { entry ->
        // Calculate X position based on timestamp and viewport duration
        val x = (entry.third - startTime).toFloat() / viewportDuration * fullWidth
        
        // 1. Draw status line at bottom (1.5dp height)
        val statusColor = if (entry.first >= 0) 0xFF4CAF50.toInt() else 0xFF808080.toInt()
        container.addContent(
            Box.Builder()
                .setWidth(dp(fullWidth))
                .setHeight(dp(containerHeight))
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setPadding(
                            ModifiersBuilders.Padding.Builder()
                                .setStart(dp(x.coerceIn(0f, fullWidth - barWidth)))
                                .setTop(dp(containerHeight - 1.5f))
                                .build()
                        )
                        .build()
                )
                .addContent(
                    Box.Builder()
                        .setWidth(dp(barWidth))
                        .setHeight(dp(1.5f))
                        .setModifiers(
                            ModifiersBuilders.Modifiers.Builder()
                                .setBackground(
                                    ModifiersBuilders.Background.Builder()
                                        .setColor(ColorBuilders.argb(statusColor))
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()
        )

        // 2. Draw battery bar (only if connected)
        if (entry.first >= 0) {
            val barHeight = (entry.first / 100f * containerHeight).coerceAtLeast(2f)
            container.addContent(
                Box.Builder()
                    .setWidth(dp(fullWidth))
                    .setHeight(dp(containerHeight))
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setPadding(
                                ModifiersBuilders.Padding.Builder()
                                    .setStart(dp(x.coerceIn(0f, fullWidth - barWidth)))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        Box.Builder()
                            .setWidth(dp(barWidth))
                            .setHeight(dp(barHeight))
                            .setModifiers(
                                ModifiersBuilders.Modifiers.Builder()
                                    .setBackground(
                                        ModifiersBuilders.Background.Builder()
                                            .setColor(if (entry.second) ColorBuilders.argb(0xFF4CAF50.toInt()) else ColorBuilders.argb(0xFF7B1FA2.toInt()))
                                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(1.5f)).build())
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
                    .build()
            )
        }
    }
    
    return container.build()
}
