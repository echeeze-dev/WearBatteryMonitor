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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
    data class Active(val percent: Int, val isCharging: Boolean, val timestamp: Long, val earphonePercent: Int? = null, val earphoneL: Int? = null, val earphoneR: Int? = null, val earphoneCase: Int? = null, val earphoneName: String? = null) : ConnectionState()
    data class Offline(val percent: Int, val isCharging: Boolean, val timestamp: Long, val earphonePercent: Int? = null, val earphoneL: Int? = null, val earphoneR: Int? = null, val earphoneCase: Int? = null, val earphoneName: String? = null) : ConnectionState()
}

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener, CapabilityClient.OnCapabilityChangedListener {

    private var connectionState by mutableStateOf<ConnectionState>(ConnectionState.Initializing)
    private var watchBatteryPercent by mutableStateOf(0)
    private var watchIsCharging by mutableStateOf(false)
    private var graphRangeHours by mutableStateOf(1)

    // In-memory history for now to fix startup crash
    private val phoneHistory = mutableStateListOf<BatteryEntry>()
    private val earphoneHistory = mutableStateListOf<BatteryEntry>()
    private val watchHistory = mutableStateListOf<BatteryEntry>()

    private val watchBatteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { updateWatchBattery() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
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
        watchBatteryPercent = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0
        watchIsCharging = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
        addHistory(watchHistory, "watch", watchBatteryPercent)
        sendWatchBatteryToPhone()
    }

    private fun addHistory(list: MutableList<BatteryEntry>, type: String, percent: Int) {
        list.add(BatteryEntry(type, percent, System.currentTimeMillis()))
        if (list.size > 500) list.removeAt(0)
    }

    private fun sendWatchBatteryToPhone() {
        val putDataReq = PutDataMapRequest.create("/watch_battery").apply {
            dataMap.putInt("watch_percent", watchBatteryPercent)
            dataMap.putBoolean("watch_is_charging", watchIsCharging)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(this).putDataItem(putDataReq)
    }

    private fun checkStatus() {
        val capabilityClient = Wearable.getCapabilityClient(this)
        capabilityClient.getCapability("battery_monitor_phone_app", CapabilityClient.FILTER_REACHABLE).addOnSuccessListener { info ->
            if (info.nodes.isEmpty()) {
                Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { connectionState = if (it.isEmpty()) ConnectionState.Disconnected else ConnectionState.AppNotInstalled }
            } else {
                Wearable.getDataClient(this).dataItems.addOnSuccessListener { items ->
                    items.find { it.uri.path == "/battery_status" }?.let { updateStateFromDataMap(DataMapItem.fromDataItem(it).dataMap, info.nodes.any { n -> n.isNearby }) } ?: run { connectionState = ConnectionState.NoData }
                }
                info.nodes.forEach { Wearable.getMessageClient(this).sendMessage(it.id, "/request_battery_update", null) }
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
        val eL = if (dataMap.containsKey("earphone_l")) dataMap.getInt("earphone_l") else null
        val eR = if (dataMap.containsKey("earphone_r")) dataMap.getInt("earphone_r") else null
        val eC = if (dataMap.containsKey("earphone_case")) dataMap.getInt("earphone_case") else null
        val en = dataMap.getString("earphone_name")
        connectionState = if (isOnline) ConnectionState.Active(p, c, t, ep, eL, eR, eC, en) else ConnectionState.Offline(p, c, t, ep, eL, eR, eC, en)
        addHistory(phoneHistory, "phone", p)
        ep?.let { addHistory(earphoneHistory, "earphone", it) }
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
                            is ConnectionState.Active -> EarphoneCard(state.earphonePercent, state.earphoneL, state.earphoneR, state.earphoneCase, state.earphoneName, state.timestamp, false, earphoneHistory, screenHeight, rangeHours)
                            is ConnectionState.Offline -> EarphoneCard(state.earphonePercent, state.earphoneL, state.earphoneR, state.earphoneCase, state.earphoneName, state.timestamp, true, earphoneHistory, screenHeight, rangeHours)
                            else -> EarphoneCard(null, null, null, null, null, 0L, true, earphoneHistory, screenHeight, rangeHours)
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
fun EarphoneCard(ep: Int?, L: Int?, R: Int?, C: Int?, name: String?, timestamp: Long, isOffline: Boolean, history: List<BatteryEntry>, screenHeight: androidx.compose.ui.unit.Dp, rangeHours: Int) {
    Column(modifier = Modifier.fillMaxWidth().height(screenHeight * 0.85f).padding(horizontal = 8.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainer), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.Headset, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (isOffline) Color.Gray else MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = name ?: "Earphones", style = MaterialTheme.typography.labelMedium, color = if (isOffline) Color.Gray else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (L != null || R != null || C != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                L?.let { SmallBatteryItemFixed("L", it, isOffline) }
                R?.let { SmallBatteryItemFixed("R", it, isOffline) }
                C?.let { SmallBatteryItemFixed("C", it, isOffline) }
            }
        } else {
            Text(text = if (ep != null) "$ep%" else "---", style = MaterialTheme.typography.displaySmall, color = Color.Gray)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Box(modifier = Modifier.height(40.dp).fillMaxWidth(0.8f)) { HistoryGraphFixed(history, isOffline, rangeHours) }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = if (timestamp > 0) formatRelativeTime(timestamp) else "--", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
fun SmallBatteryItemFixed(label: String, percent: Int, isOffline: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Text(text = "$percent%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isOffline) Color.Gray else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun HistoryGraphFixed(history: List<BatteryEntry>, isOffline: Boolean, rangeHours: Int) {
    if (history.isEmpty()) return
    val now = System.currentTimeMillis()
    val startTime = now - (rangeHours * 3600000L)
    val data = history.filter { it.timestamp >= startTime }
    if (data.isEmpty()) return

    val color = if (isOffline) Color.Gray else MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width; val height = size.height; val path = Path()
        val duration = now - startTime
        
        data.forEachIndexed { index, entry ->
            val x = (entry.timestamp - startTime).toFloat() / duration * width
            val y = height - (entry.percent / 100f * height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
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
    WearApp(ConnectionState.Active(75, true, System.currentTimeMillis(), 80, 70, 70, 100, "My Buds"), 90, false, emptyList(), emptyList(), emptyList(), 1)
}
