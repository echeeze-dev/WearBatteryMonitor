package com.echeeze.wearbatterymonitor.tile

import android.content.Context
import android.util.Log
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.Row
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
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
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

class MainTileService : TileService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        serviceScope.future {
            Log.d("MainTileService", "onTileRequest started")
            val dataClient = Wearable.getDataClient(this@MainTileService)
            
            var percent = 0
            var isCharging = false
            var timestamp = 0L
            val history = mutableListOf<Pair<Int, Boolean>>()

            try {
                val dataItems = com.google.android.gms.tasks.Tasks.await(dataClient.dataItems)
                val batteryItem = dataItems.find { it.uri.path == "/battery_status" }
                
                if (batteryItem != null) {
                    val dataMap = DataMapItem.fromDataItem(batteryItem).dataMap
                    percent = dataMap.getInt("battery_percent")
                    isCharging = dataMap.getBoolean("is_charging")
                    timestamp = dataMap.getLong("timestamp")
                    
                    dataMap.getDataMapArrayList("phone_history")?.let { list ->
                        list.takeLast(30).forEach { entryMap ->
                            history.add(entryMap.getInt("p") to entryMap.getBoolean("c"))
                        }
                    }
                }
                dataItems.release()
            } catch (e: Exception) {
                Log.e("MainTileService", "Error fetching data", e)
            }
            
            tile(requestParams, this@MainTileService, percent, isCharging, timestamp, history)
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
    history: List<Pair<Int, Boolean>>
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
                                    barChart(history)
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

private fun barChart(history: List<Pair<Int, Boolean>>): LayoutElementBuilders.LayoutElement {
    val row = Row.Builder()
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM)
    
    val chargingColor = ColorBuilders.argb(0xFF4CAF50.toInt())
    val normalColor = ColorBuilders.argb(0xFF7B1FA2.toInt()) // Deep Purple
    
    if (history.isEmpty()) {
        return Spacer.Builder().setHeight(dp(24f)).build()
    }

    history.forEach { entry ->
        val barHeight = (entry.first / 100f * 24f).coerceAtLeast(2f)
        val color = if (entry.second) chargingColor else normalColor
        row.addContent(
            Box.Builder()
                .setWidth(dp(3f))
                .setHeight(dp(barHeight))
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setBackground(
                            ModifiersBuilders.Background.Builder()
                                .setColor(color)
                                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(1.5f)).build())
                                .build()
                        )
                        .setPadding(
                            ModifiersBuilders.Padding.Builder()
                                .setStart(dp(1.5f))
                                .build()
                        )
                        .build()
                )
                .build()
        )
    }
    
    return row.build()
}

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun tilePreview(context: Context) = TilePreviewData(::resources) {
    val history = List(20) { (50..90).random() to (it % 5 == 0) }
    tile(it, context, 75, true, System.currentTimeMillis(), history)
}
