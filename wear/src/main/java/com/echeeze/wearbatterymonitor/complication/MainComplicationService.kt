package com.echeeze.wearbatterymonitor.complication

import android.graphics.drawable.Icon
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

/**
 * Skeleton for complication data source that returns short text.
 */
class MainComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) {
            return null
        }
        return createComplicationData("85%", "Phone: 85%", false)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) {
            return null
        }

        var percent = -1
        var isCharging = false

        try {
            val dataClient = Wearable.getDataClient(this)
            val dataItems = dataClient.dataItems.await()
            val batteryItem = dataItems.find { it.uri.path == "/battery_status" }
            if (batteryItem != null) {
                val dataMap = DataMapItem.fromDataItem(batteryItem).dataMap
                percent = dataMap.getInt("battery_percent")
                isCharging = dataMap.getBoolean("is_charging")
            }
            dataItems.release()
        } catch (e: Exception) {
            Log.e("MainComplication", "Error fetching battery status", e)
        }

        val text = if (percent >= 0) "$percent%" else "--%"
        val contentDescription = if (percent >= 0) "Phone Battery: $percent%" else "Phone Battery Unknown"
        
        return createComplicationData(text, contentDescription, isCharging)
    }

    private fun createComplicationData(text: String, contentDescription: String, isCharging: Boolean) =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder(contentDescription).build()
        ).setMonochromaticImage(
            MonochromaticImage.Builder(
                Icon.createWithResource(
                    this,
                    if (isCharging) com.echeeze.wearbatterymonitor.R.drawable.ic_phone_charging
                    else com.echeeze.wearbatterymonitor.R.drawable.ic_phone_battery
                )
            ).build()
        ).build()
}
