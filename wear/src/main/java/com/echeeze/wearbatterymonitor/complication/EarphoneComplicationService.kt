package com.echeeze.wearbatterymonitor.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.echeeze.wearbatterymonitor.R
import com.echeeze.wearbatterymonitor.data.WearBatteryRepositoryImpl
import com.echeeze.wearbatterymonitor.presentation.MainActivity

/**
 * スマホに接続された Bluetooth イヤホンの残量を文字表示するコンプリケーションデータソースサービスです。
 */
class EarphoneComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) {
            return null
        }
        return createComplicationData("80%", "Earphone: 80%")
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) {
            return null
        }

        var percent: Int? = null
        var name: String? = null

        try {
            val repository = WearBatteryRepositoryImpl(this)
            val status = repository.getEarphoneBatteryStatus()
            if (status != null) {
                percent = status.percent
                name = status.name
            }
        } catch (e: Exception) {
            Log.e("EarphoneComplication", "Error fetching earphone battery status", e)
        }

        val displayName = name ?: "Bluetooth"
        val text = if (percent != null && percent >= 0) "$percent%" else "--%"
        val contentDescription = "$displayName Battery: $text"

        return createComplicationData(text, contentDescription)
    }

    private fun createComplicationData(text: String, contentDescription: String): ShortTextComplicationData {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder(contentDescription).build()
        ).setMonochromaticImage(
            MonochromaticImage.Builder(
                Icon.createWithResource(
                    this,
                    R.drawable.ic_bluetooth_battery
                )
            ).build()
        ).setTapAction(pendingIntent).build()
    }
}
