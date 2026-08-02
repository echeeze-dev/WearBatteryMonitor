package com.echeeze.wearbatterymonitor.tile

import android.content.Context
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.Typography.BODY_MEDIUM
import androidx.wear.protolayout.material3.Typography.DISPLAY_MEDIUM
import androidx.wear.protolayout.material3.Typography.LABEL_SMALL
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val RESOURCES_VERSION = "0"

class MainTileService : TileService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        serviceScope.future {
            val dataClient = Wearable.getDataClient(this@MainTileService)
            val dataItems = com.google.android.gms.tasks.Tasks.await(dataClient.dataItems)
            val batteryItem = dataItems.find { it.uri.path == "/battery_status" }
            
            val (percent, isCharging, timestamp) = if (batteryItem != null) {
                val dataMap = DataMapItem.fromDataItem(batteryItem).dataMap
                Triple(
                    dataMap.getInt("battery_percent"),
                    dataMap.getBoolean("is_charging"),
                    dataMap.getLong("timestamp")
                )
            } else {
                Triple(0, false, 0L)
            }
            
            tile(requestParams, this@MainTileService, percent, isCharging, timestamp)
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
    timestamp: Long
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
                                        "Phone Battery".layoutString,
                                        typography = BODY_MEDIUM,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                )
                                .addContent(
                                    text(
                                        "$percent%".layoutString,
                                        typography = DISPLAY_MEDIUM,
                                        color = colorScheme.onSurface
                                    )
                                )
                                .addContent(
                                    text(
                                        ((if (isCharging) "Charging • " else "") + "Updated: $timeStr").layoutString,
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

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun tilePreview(context: Context) = TilePreviewData(::resources) {
    tile(it, context, 75, true, System.currentTimeMillis())
}
