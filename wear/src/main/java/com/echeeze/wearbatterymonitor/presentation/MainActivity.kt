package com.echeeze.wearbatterymonitor.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.echeeze.wearbatterymonitor.data.BatteryEntry
import com.echeeze.wearbatterymonitor.data.Constants
import com.echeeze.wearbatterymonitor.data.WearBatteryRepository
import com.echeeze.wearbatterymonitor.data.WearBatteryRepositoryImpl
import com.echeeze.wearbatterymonitor.presentation.theme.WearBatteryMonitorTheme
import com.google.android.gms.wearable.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed class ConnectionState {
    object Initializing : ConnectionState()
    object Disconnected : ConnectionState()
    object AppNotInstalled : ConnectionState()
    object NoData : ConnectionState()
    data class Active(val percent: Int, val isCharging: Boolean, val timestamp: Long, val earphonePercent: Int? = null, val earphoneName: String? = null) : ConnectionState()
    data class Offline(val percent: Int, val isCharging: Boolean, val timestamp: Long, val earphonePercent: Int? = null, val earphoneName: String? = null) : ConnectionState()
}

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener, CapabilityClient.OnCapabilityChangedListener {

    private var connectionState by mutableStateOf<ConnectionState>(ConnectionState.Initializing)
    private var watchBatteryPercent by mutableStateOf(0)
    private var watchIsCharging by mutableStateOf(false)
    private var graphRangeHours by mutableStateOf(3)

    // In-memory history for now to fix startup crash
    private val phoneHistory = mutableStateListOf<BatteryEntry>()
    private val earphoneHistory = mutableStateListOf<BatteryEntry>()
    private val watchHistory = mutableStateListOf<BatteryEntry>()

    private val watchBatteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { updateWatchBattery() }
    }

    private lateinit var repository: WearBatteryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        repository = WearBatteryRepositoryImpl(this)
        updateWatchBattery()
        setContent {
            WearApp(connectionState, watchBatteryPercent, watchIsCharging, phoneHistory, earphoneHistory, watchHistory, graphRangeHours)
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        Wearable.getCapabilityClient(this).addListener(this, "battery_monitor_phone_app")
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(watchBatteryReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(watchBatteryReceiver, filter)
        }
        updateWatchBattery()
        checkStatus()
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
        Wearable.getCapabilityClient(this).removeListener(this)
        unregisterReceiver(watchBatteryReceiver)
    }

    private fun updateWatchBattery() {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val oldPercent = watchBatteryPercent
        val oldCharging = watchIsCharging
        
        watchBatteryPercent = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0
        watchIsCharging = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
        
        val reason = if (oldPercent != watchBatteryPercent || oldCharging != watchIsCharging) "BATTERY_CHANGED" else "PERIODIC_INTERVAL"
        addHistory(watchHistory, "watch", watchBatteryPercent, watchIsCharging)
        sendWatchBatteryToPhone(reason)
    }

    private fun addHistory(list: MutableList<BatteryEntry>, type: String, percent: Int, isCharging: Boolean = false) {
        val now = System.currentTimeMillis()
        val last = list.lastOrNull()
        // Only add if battery changed or at least 1 minute passed
        if (last == null || last.percent != percent || last.isCharging != isCharging || now - last.timestamp > 60000) {
            list.add(BatteryEntry(type, percent, now, isCharging))
            if (list.size > Constants.MAX_HISTORY_SIZE) list.removeAt(0)
        }
    }

    private fun sendWatchBatteryToPhone(reason: String = "INITIAL_FETCH") {
        lifecycleScope.launch {
            repository.sendWatchBatteryToPhone(
                percent = watchBatteryPercent,
                isCharging = watchIsCharging,
                deviceName = Build.MODEL,
                recordReason = reason
            )
        }
    }

    private fun checkStatus() {
        val capabilityClient = Wearable.getCapabilityClient(this)
        capabilityClient.getCapability("battery_monitor_phone_app", CapabilityClient.FILTER_REACHABLE).addOnSuccessListener { info ->
            if (info.nodes.isEmpty()) {
                Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { connectionState = if (it.isEmpty()) ConnectionState.Disconnected else ConnectionState.AppNotInstalled }
            } else {
                Wearable.getDataClient(this).dataItems.addOnSuccessListener { items ->
                    items.find { it.uri.path == "/battery_status" }?.let { updateStateFromDataMap(DataMapItem.fromDataItem(it).dataMap, info.nodes.any { n -> n.isNearby }) } ?: run { connectionState = ConnectionState.NoData }
                    items.release()
                }
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                when (event.dataItem.uri.path) {
                    "/battery_status" -> updateStateFromDataMap(DataMapItem.fromDataItem(event.dataItem).dataMap, true)
                    "/settings/graph_range" -> graphRangeHours = DataMapItem.fromDataItem(event.dataItem).dataMap.getInt("range_hours", 1)
                }
            }
        }
    }

    private fun updateStateFromDataMap(dataMap: DataMap, isOnline: Boolean) {
        val p = dataMap.getInt("battery_percent"); val c = dataMap.getBoolean("is_charging"); val t = dataMap.getLong("timestamp")
        val ep = if (dataMap.containsKey("earphone_percent")) dataMap.getInt("earphone_percent") else null
        val en = dataMap.getString("earphone_name")
        connectionState = if (isOnline) ConnectionState.Active(p, c, t, ep, en) else ConnectionState.Offline(p, c, t, ep, en)
        
        // Sync history if available in the dataMap
        if (dataMap.containsKey("phone_history")) {
            syncHistoryFromDataMap(phoneHistory, dataMap.getDataMapArrayList("phone_history")!!, "phone")
        } else {
            addHistory(phoneHistory, "phone", p, c)
        }
        
        if (dataMap.containsKey("watch_history")) {
            syncHistoryFromDataMap(watchHistory, dataMap.getDataMapArrayList("watch_history")!!, "watch")
        } else {
            addHistory(watchHistory, "watch", watchBatteryPercent, watchIsCharging)
        }
        
        if (dataMap.containsKey("earphone_history")) {
            syncHistoryFromDataMap(earphoneHistory, dataMap.getDataMapArrayList("earphone_history")!!, "earphone")
        } else {
            ep?.let { addHistory(earphoneHistory, "earphone", it, false) }
        }
    }

    private fun syncHistoryFromDataMap(list: MutableList<BatteryEntry>, dataList: ArrayList<DataMap>, type: String) {
        // Convert the DataMap list to BatteryEntry list efficiently
        val newList = dataList.map { dm ->
            BatteryEntry(type, dm.getInt("p"), dm.getLong("t"), dm.getBoolean("c"))
        }
        
        // Update the state list in one go to minimize recompositions and lag
        list.clear()
        list.addAll(newList)
    }

    override fun onCapabilityChanged(info: CapabilityInfo) { checkStatus() }
}

@Composable
fun WearApp(state: ConnectionState, watchPercent: Int, watchCharging: Boolean, phoneHistory: List<BatteryEntry>, earphoneHistory: List<BatteryEntry>, watchHistory: List<BatteryEntry>, rangeHours: Int) {
    WearBatteryMonitorTheme {
        AppScaffold {
            val listState = rememberScalingLazyListState()
            val coroutineScope = rememberCoroutineScope()
            val focusRequester = remember { FocusRequester() }
            val screenHeight = LocalConfiguration.current.screenHeightDp.dp
            ScreenScaffold(scrollState = listState) { _ ->
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize().onRotaryScrollEvent { coroutineScope.launch { listState.scrollBy(it.verticalScrollPixels) }; true }.focusRequester(focusRequester).focusable(),
                    state = listState, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                    item {
                        when (state) {
                            is ConnectionState.Active -> BatteryCard(state.percent, state.isCharging, state.timestamp, false, "Phone", Icons.Default.Smartphone, phoneHistory, screenHeight, rangeHours)
                            is ConnectionState.Offline -> BatteryCard(state.percent, state.isCharging, state.timestamp, true, "Phone", Icons.Default.Smartphone, phoneHistory, screenHeight, rangeHours)
                            else -> BatteryCard(0, false, 0L, true, "Phone", Icons.Default.Smartphone, phoneHistory, screenHeight, rangeHours)
                        }
                    }
                    item { BatteryCard(watchPercent, watchCharging, System.currentTimeMillis(), false, "Watch", Icons.Default.Watch, watchHistory, screenHeight, rangeHours) }
                    item {
                        when (state) {
                            is ConnectionState.Active -> EarphoneCard(state.earphonePercent, state.earphoneName, state.timestamp, false, earphoneHistory, screenHeight, rangeHours)
                            is ConnectionState.Offline -> EarphoneCard(state.earphonePercent, state.earphoneName, state.timestamp, true, earphoneHistory, screenHeight, rangeHours)
                            else -> EarphoneCard(null, null, 0L, true, earphoneHistory, screenHeight, rangeHours)
                        }
                    }
                    item {
                        when (state) {
                            is ConnectionState.Disconnected -> Text("Disconnected", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            is ConnectionState.AppNotInstalled -> Text("No App on Phone", style = MaterialTheme.typography.labelSmall, color = Color.Yellow)
                            is ConnectionState.Initializing -> CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            else -> {}
                        }
                    }
                    item { Spacer(modifier = Modifier.height(40.dp)) }
                }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
            }
        }
    }
}

@Composable
fun BatteryCard(percent: Int, isCharging: Boolean, timestamp: Long, isOffline: Boolean, label: String, icon: ImageVector, history: List<BatteryEntry>, screenHeight: androidx.compose.ui.unit.Dp, rangeHours: Int) {
    Column(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.85f).padding(horizontal = 8.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (isOffline) Color.Gray else MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = if (isOffline) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "$percent%", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = if (isOffline) Color.Gray else if (percent < 20) Color.Red else MaterialTheme.colorScheme.onSurface)
            if (isCharging) { Spacer(modifier = Modifier.width(4.dp)); Icon(imageVector = Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(24.dp), tint = if (isOffline) Color.Gray else Color.Green) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.height(40.dp).fillMaxWidth(0.8f)) { HistoryGraphFixed(history, isOffline, rangeHours) }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = if (timestamp > 0) formatRelativeTime(timestamp) else "--", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
fun EarphoneCard(ep: Int?, name: String?, timestamp: Long, isOffline: Boolean, history: List<BatteryEntry>, screenHeight: androidx.compose.ui.unit.Dp, rangeHours: Int) {
    Column(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.85f).padding(horizontal = 8.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (isOffline) Color.Gray else MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = name ?: "Bluetooth Device", style = MaterialTheme.typography.labelMedium, color = if (isOffline) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = if (ep != null) "$ep%" else "---", style = MaterialTheme.typography.displaySmall, color = if (isOffline) Color.Gray else MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(12.dp))
        Box(modifier = Modifier.height(40.dp).fillMaxWidth(0.8f)) { HistoryGraphFixed(history, isOffline, rangeHours) }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = if (timestamp > 0) formatRelativeTime(timestamp) else "--", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}


@Composable
fun HistoryGraphFixed(history: List<BatteryEntry>, isOffline: Boolean, rangeHours: Int) {
    val now = System.currentTimeMillis()
    val startTime = if (rangeHours == 0) {
        history.minByOrNull { it.timestamp }?.timestamp ?: now
    } else {
        now - (rangeHours * 3600000L)
    }

    // Filter by time range and valid battery levels
    val filteredData = history.filter { it.timestamp >= startTime }
    val validBatteryData = filteredData.filter { it.percent >= 0 }

    // If no valid data is available, display a clean "No Data" state instead of incomplete graph
    if (history.isEmpty() || filteredData.isEmpty() || validBatteryData.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "データなし",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray.copy(alpha = 0.5f)
            )
        }
        return
    }

    val oldestTimestamp = history.minByOrNull { it.timestamp }?.timestamp ?: now
    val totalDuration = (now - oldestTimestamp).coerceAtLeast(1L)
    val viewportDuration = if (rangeHours == 0) totalDuration else (rangeHours * 3600000L)

    // Downsample for rendering performance if there are too many items
    val maxBars = 150
    val data = if (filteredData.size > maxBars) {
        val step = filteredData.size.toDouble() / maxBars
        List(maxBars) { i ->
            filteredData[(i * step).toInt().coerceAtMost(filteredData.size - 1)]
        }
    } else {
        filteredData
    }

    val normalColor = if (isOffline) Color.Gray else MaterialTheme.colorScheme.primary
    val chargingColor = if (isOffline) Color.Gray else Color.Green
    val disconnectedColor = Color.Gray
    val gridColor = Color.Gray.copy(alpha = 0.3f)
    val serviceActiveBgColor = Color.Gray.copy(alpha = 0.1f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width; val height = size.height
        
        // Optimize bar width: Ensure bars are at least 1 physical pixel and have minimal overlap
        val barWidth = (width / (data.size.coerceAtLeast(1) * 1.2f)).coerceIn(1f, 8.dp.toPx())

        // Draw grid lines (0%, 50%, 100%)
        drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(width, 0f), strokeWidth = 0.5.dp.toPx())
        drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, height / 2), end = androidx.compose.ui.geometry.Offset(width, height / 2), strokeWidth = 0.5.dp.toPx())
        drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, height), end = androidx.compose.ui.geometry.Offset(width, height), strokeWidth = 0.5.dp.toPx())

        data.forEach { entry ->
            val x = (entry.timestamp - startTime).toFloat() / viewportDuration * width
            
            // 1. Draw service active background
            drawRect(
                color = serviceActiveBgColor,
                topLeft = androidx.compose.ui.geometry.Offset(x - barWidth / 2, 0f),
                size = androidx.compose.ui.geometry.Size(barWidth, height)
            )

            // 2. Draw status line at bottom (2dp height)
            val statusColor = if (entry.percent >= 0) chargingColor else disconnectedColor
            drawRect(
                color = statusColor,
                topLeft = androidx.compose.ui.geometry.Offset(x - barWidth / 2, height - 1.5.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(barWidth, 1.5.dp.toPx())
            )

            // 3. Draw battery bar (only if connected)
            if (entry.percent >= 0) {
                val barHeight = entry.percent / 100f * height
                val color = if (entry.isCharging) chargingColor else normalColor
                drawRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(x - barWidth / 2, height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                )
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val diffMin = diff / 60000
    return when {
        diff < 60000 -> "たった今"
        diffMin < 60 -> "${diffMin}分前"
        else -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun DefaultPreview() {
    WearApp(ConnectionState.Active(75, true, System.currentTimeMillis(), 80, "My Buds"), 90, false, emptyList(), emptyList(), emptyList(), 1)
}
