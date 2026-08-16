package com.echeeze.wearbatterymonitor

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.edit
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.echeeze.wearbatterymonitor.data.BatteryEntry
import com.echeeze.wearbatterymonitor.data.Constants
import com.echeeze.wearbatterymonitor.ui.theme.WearBatteryMonitorTheme
import com.google.android.gms.wearable.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener, DataClient.OnDataChangedListener {

    private var batteryPercent by mutableIntStateOf(0)
    private var isCharging by mutableStateOf(false)
    private var watchPercent by mutableStateOf<Int?>(null)
    private var watchIsCharging by mutableStateOf(false)
    private var earphonePercent by mutableStateOf<Int?>(null)
    private var earphoneL by mutableStateOf<Int?>(null)
    private var earphoneR by mutableStateOf<Int?>(null)
    private var earphoneCase by mutableStateOf<Int?>(null)
    private var earphoneName by mutableStateOf<String?>(null)
    private var watchName by mutableStateOf<String?>(null)
    private var isServiceRunning by mutableStateOf(false)
    private var graphRangeHours by mutableIntStateOf(1)
    private var updateIntervalSeconds by mutableIntStateOf(60)

    // In-memory history for now to fix startup crash
    private val phoneHistory = mutableStateListOf<BatteryEntry>()
    private val watchHistory = mutableStateListOf<BatteryEntry>()
    private val earphoneHistory = mutableStateListOf<BatteryEntry>()

    private var batteryMonitorService: BatteryMonitorService? by mutableStateOf(null)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BatteryMonitorService.LocalBinder
            batteryMonitorService = binder.getService()
            // Sync initial history from service
            syncHistoryFromService()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            batteryMonitorService = null
        }
    }

    private fun syncHistoryFromService() {
        batteryMonitorService?.let { service ->
            phoneHistory.clear(); phoneHistory.addAll(service.phoneHistory)
            watchHistory.clear(); watchHistory.addAll(service.watchHistory)
            earphoneHistory.clear(); earphoneHistory.addAll(service.earphoneHistory)
            
            // Sync current states
            batteryPercent = service.currentPhonePercent
            isCharging = service.currentPhoneCharging
            earphonePercent = service.currentEarphonePercent
            earphoneL = service.currentEarphoneL
            earphoneR = service.currentEarphoneR
            earphoneCase = service.currentEarphoneCase
            earphoneName = service.currentEarphoneName
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    batteryPercent = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 0
                    isCharging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1).let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
                    addHistory(phoneHistory, "phone", batteryPercent, isCharging)
                    if (!isServiceRunning) sendBatteryStatusToWear()
                }
                "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> {
                    updateEarphoneState(intent)
                    if (!isServiceRunning) sendBatteryStatusToWear()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sharedPref = getPreferences(MODE_PRIVATE)
        isServiceRunning = sharedPref.getBoolean("service_running", false)
        graphRangeHours = sharedPref.getInt("graph_range_hours", 1)
        updateIntervalSeconds = sharedPref.getInt("update_interval_seconds", 60)
        if (isServiceRunning) {
            startBatteryService()
            bindService(Intent(this, BatteryMonitorService::class.java), serviceConnection, BIND_AUTO_CREATE)
        }

        setContent {
            WearBatteryMonitorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BatteryMonitorScreen(
                        phonePercent = batteryPercent,
                        phoneIsCharging = isCharging,
                        phoneHistory = phoneHistory,
                        watchPercent = watchPercent,
                        watchIsCharging = watchIsCharging,
                        watchHistory = watchHistory,
                        earphonePercent = earphonePercent,
                        earphoneL = earphoneL,
                        earphoneR = earphoneR,
                        earphoneCase = earphoneCase,
                        earphoneName = earphoneName,
                        earphoneHistory = earphoneHistory,
                        isServiceRunning = isServiceRunning,
                        onServiceToggle = { toggleService(it) },
                        graphRangeHours = graphRangeHours,
                        onGraphRangeChange = { updateGraphRange(it) },
                        updateIntervalSeconds = updateIntervalSeconds,
                        onIntervalChange = { updateInterval(it) },
                        watchName = watchName,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun updateInterval(seconds: Int) {
        updateIntervalSeconds = seconds
        getPreferences(MODE_PRIVATE).edit {
            putInt("update_interval_seconds", seconds)
        }
        batteryMonitorService?.updateTimerInterval(seconds)
    }

    private fun updateGraphRange(hours: Int) {
        graphRangeHours = hours
        getPreferences(MODE_PRIVATE).edit {
            putInt("graph_range_hours", hours)
        }
        sendGraphRangeToWear(hours)
    }

    private fun sendGraphRangeToWear(hours: Int) {
        val putDataMapReq = PutDataMapRequest.create("/settings/graph_range")
        putDataMapReq.dataMap.putInt("range_hours", hours)
        putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis())
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        Wearable.getDataClient(this).putDataItem(putDataReq)
    }

    private fun addHistory(list: MutableList<BatteryEntry>, type: String, percent: Int, isCharging: Boolean) {
        // If service is running, it handles history. Only add here if NOT running.
        if (!isServiceRunning) {
            list.add(BatteryEntry(type, percent, System.currentTimeMillis(), isCharging))
            if (list.size > Constants.MAX_HISTORY_SIZE) list.removeAt(0)
        } else {
            // If running, we sync from service periodically or on demand
            syncHistoryFromService()
        }
    }

    private fun updateEarphoneState(intent: Intent) {
        val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
        val bL = intent.getIntExtra("android.bluetooth.device.extra.LEFT_BATTERY_LEVEL", -1)
        val bR = intent.getIntExtra("android.bluetooth.device.extra.RIGHT_BATTERY_LEVEL", -1)
        val bC = intent.getIntExtra("android.bluetooth.device.extra.CASE_BATTERY_LEVEL", -1)
        if (level != -1) { earphonePercent = level; addHistory(earphoneHistory, "earphone", level, false) }
        if (bL != -1) earphoneL = bL
        if (bR != -1) earphoneR = bR
        if (bC != -1) earphoneCase = bC
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE, android.bluetooth.BluetoothDevice::class.java)
                         else @Suppress("DEPRECATION") intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
            earphoneName = device?.name
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        Wearable.getDataClient(this).dataItems.addOnSuccessListener { items ->
            items.find { it.uri.path == "/watch_battery" }?.let { updateWatchState(DataMapItem.fromDataItem(it).dataMap) }
            items.release()
        }
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            watchName = nodes.firstOrNull()?.displayName
        }
        sendGraphRangeToWear(graphRangeHours)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.dataItem.uri.path == "/watch_battery") {
                updateWatchState(DataMapItem.fromDataItem(event.dataItem).dataMap)
                if (isServiceRunning) syncHistoryFromService()
            }
        }
        dataEvents.release()
    }

    private fun updateWatchState(dataMap: DataMap) {
        watchPercent = dataMap.getInt("watch_percent")
        watchIsCharging = dataMap.getBoolean("watch_is_charging")
        addHistory(watchHistory, "watch", watchPercent ?: 0, watchIsCharging)
    }

    private fun toggleService(enabled: Boolean) {
        if (enabled) {
            val perms = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) perms.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(), 101); return }
            startBatteryService()
            bindService(Intent(this, BatteryMonitorService::class.java), serviceConnection, BIND_AUTO_CREATE)
        } else {
            if (batteryMonitorService != null) unbindService(serviceConnection)
            stopBatteryService()
            batteryMonitorService = null
        }
        isServiceRunning = enabled
        getPreferences(MODE_PRIVATE).edit {
            putBoolean("service_running", enabled)
        }
    }

    private fun startBatteryService() = ContextCompat.startForegroundService(this, Intent(this, BatteryMonitorService::class.java))
    private fun stopBatteryService() = stopService(Intent(this, BatteryMonitorService::class.java))

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceRunning && batteryMonitorService != null) {
            unbindService(serviceConnection)
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(batteryReceiver, filter)
        }
        Wearable.getMessageClient(this).addListener(this)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(batteryReceiver)
        Wearable.getMessageClient(this).removeListener(this)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/request_battery_update") sendBatteryStatusToWear()
    }

    private fun sendBatteryStatusToWear() {
        val putDataMapReq = PutDataMapRequest.create("/battery_status")
        putDataMapReq.dataMap.putInt("battery_percent", batteryPercent)
        putDataMapReq.dataMap.putBoolean("is_charging", isCharging)
        earphonePercent?.let { putDataMapReq.dataMap.putInt("earphone_percent", it) }
        earphoneL?.let { putDataMapReq.dataMap.putInt("earphone_l", it) }
        earphoneR?.let { putDataMapReq.dataMap.putInt("earphone_r", it) }
        earphoneCase?.let { putDataMapReq.dataMap.putInt("earphone_case", it) }
        earphoneName?.let { putDataMapReq.dataMap.putString("earphone_name", it) }
        putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis())
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        Wearable.getDataClient(this).putDataItem(putDataReq)
    }
}

@Composable
fun BatteryMonitorScreen(
    phonePercent: Int, phoneIsCharging: Boolean, phoneHistory: List<BatteryEntry>,
    watchPercent: Int?, watchIsCharging: Boolean, watchHistory: List<BatteryEntry>,
    earphonePercent: Int?, earphoneL: Int?, earphoneR: Int?, earphoneCase: Int?, earphoneName: String?, earphoneHistory: List<BatteryEntry>,
    isServiceRunning: Boolean, onServiceToggle: (Boolean) -> Unit,
    graphRangeHours: Int, onGraphRangeChange: (Int) -> Unit,
    updateIntervalSeconds: Int, onIntervalChange: (Int) -> Unit,
    watchName: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var detailDialogData by remember { mutableStateOf<Pair<String, List<BatteryEntry>>?>(null) }
    
    val permsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.values.all { it }) onServiceToggle(true) else Toast.makeText(context, "Permissions required", Toast.LENGTH_SHORT).show()
    }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GraphRangeSelector(graphRangeHours, onGraphRangeChange)
        Spacer(modifier = Modifier.height(24.dp))
        
        BatterySection(
            icon = Icons.Default.Smartphone,
            title = Build.MODEL ?: "Smartphone",
            percent = phonePercent,
            isCharging = phoneIsCharging,
            history = phoneHistory,
            rangeHours = graphRangeHours,
            onGraphClick = { detailDialogData = (Build.MODEL ?: "Smartphone") to phoneHistory.toList() }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))
        BatterySection(
            icon = Icons.Default.Watch,
            title = watchName ?: "Smartwatch",
            percent = watchPercent ?: 0,
            isCharging = watchIsCharging,
            history = watchHistory,
            isDisconnected = watchPercent == null,
            rangeHours = graphRangeHours,
            onGraphClick = { detailDialogData = (watchName ?: "Smartwatch") to watchHistory.toList() }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))
        EarphoneSection(
            icon = Icons.Default.Headset,
            name = earphoneName,
            percent = earphonePercent,
            L = earphoneL,
            R = earphoneR,
            C = earphoneCase,
            history = earphoneHistory,
            rangeHours = graphRangeHours,
            onGraphClick = { detailDialogData = (earphoneName ?: "Earphones") to earphoneHistory.toList() }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        UpdateIntervalSelector(updateIntervalSeconds, onIntervalChange)
        
        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Background Monitoring")
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = isServiceRunning,
                onCheckedChange = { checked ->
                    if (checked) {
                        val perms = mutableListOf<String>()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) perms.add(android.Manifest.permission.BLUETOOTH_CONNECT)
                        val missing = perms.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
                        if (missing.isNotEmpty()) permsLauncher.launch(missing.toTypedArray()) else onServiceToggle(true)
                    } else onServiceToggle(false)
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Color(0xFF4CAF50),
                    checkedThumbColor = Color.White
                )
            )
        }
    }

    detailDialogData?.let { (title, history) ->
        HistoryDetailDialog(title, history, onDismiss = { detailDialogData = null })
    }
}

@Composable
fun UpdateIntervalSelector(seconds: Int, onIntervalChange: (Int) -> Unit) {
    val intervals = remember {
        val list = mutableListOf<Int>()
        for (i in 1..10) list.add(i)
        for (i in 15..60 step 5) list.add(i)
        for (i in 90..300 step 30) list.add(i)
        for (i in 360..600 step 60) list.add(i)
        list
    }
    
    val currentIndex = intervals.indexOf(seconds).coerceAtLeast(0)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
        Text("Update Interval: ${formatInterval(seconds)}", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = currentIndex.toFloat(),
            onValueChange = { onIntervalChange(intervals[it.toInt()]) },
            valueRange = 0f..(intervals.size - 1).toFloat(),
            steps = intervals.size - 2
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1s", style = MaterialTheme.typography.labelSmall)
            Text("10m", style = MaterialTheme.typography.labelSmall)
        }
    }
}

fun formatInterval(seconds: Int): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds % 60 == 0 -> "${seconds / 60}m"
        else -> "${seconds / 60}m ${seconds % 60}s"
    }
}

@Composable
fun GraphRangeSelector(selectedHours: Int, onRangeChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Graph Range:", style = MaterialTheme.typography.labelLarge)
        listOf(1, 3, 6, 12).forEach { hours ->
            FilterChip(
                selected = selectedHours == hours,
                onClick = { onRangeChange(hours) },
                label = { Text("${hours}h") }
            )
        }
    }
}

@Composable
fun BatterySection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    percent: Int,
    isCharging: Boolean,
    history: List<BatteryEntry>,
    isDisconnected: Boolean = false,
    rangeHours: Int,
    onGraphClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = title, style = MaterialTheme.typography.headlineSmall)
        }
        Text(text = if (isDisconnected) "Disconnected" else "$percent%", style = MaterialTheme.typography.displayLarge, color = if (isDisconnected) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary)
        if (!isDisconnected && isCharging) Text("Charging", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF4CAF50))
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.height(60.dp).fillMaxWidth(0.8f).clickable { onGraphClick() }) { 
            HistoryGraph(history, rangeHours) 
        }
        DebugInfoText(history)
    }
}

@Composable
fun EarphoneSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String?,
    percent: Int?,
    L: Int?,
    R: Int?,
    C: Int?,
    history: List<BatteryEntry>,
    rangeHours: Int,
    onGraphClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = name ?: "Earphones", style = MaterialTheme.typography.headlineSmall)
        }
        if (L != null || R != null || C != null) {
            Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                L?.let { BatterySubItem("Left", it) }
                R?.let { BatterySubItem("Right", it) }
                C?.let { BatterySubItem("Case", it) }
            }
        } else {
            Text(text = if (percent != null) "$percent%" else "Disconnected", style = MaterialTheme.typography.displayLarge, color = if (percent == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary)
        }
        if (percent != null || L != null || R != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.height(60.dp).fillMaxWidth(0.8f).clickable { onGraphClick() }) { 
                HistoryGraph(history, rangeHours) 
            }
            DebugInfoText(history)
        } else if (history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.height(60.dp).fillMaxWidth(0.8f).clickable { onGraphClick() }) { 
                HistoryGraph(history, rangeHours) 
            }
            DebugInfoText(history)
        }
    }
}

@Composable
fun DebugInfoText(history: List<BatteryEntry>) {
    val sdf = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()) }
    val count = history.size
    val oldest = history.firstOrNull()?.timestamp?.let { sdf.format(Date(it)) } ?: "N/A"
    val newest = history.lastOrNull()?.timestamp?.let { sdf.format(Date(it)) } ?: "N/A"
    
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = "Data: $count / ${Constants.MAX_HISTORY_SIZE}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = "Range: $oldest - $newest",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun HistoryDetailDialog(title: String, history: List<BatteryEntry>, onDismiss: () -> Unit) {
    val sdf = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "$title History", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(history.asReversed()) { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = sdf.format(Date(entry.timestamp)), style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = if (entry.isCharging) "Charging" else " ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                            Text(text = "${entry.percent}%", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
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

@Composable
fun BatterySubItem(label: String, percent: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
        Text(text = "$percent%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun HistoryGraph(history: List<BatteryEntry>, rangeHours: Int) {
    if (history.isEmpty()) return
    val now = System.currentTimeMillis()
    val startTime = now - (rangeHours * 3600000L)
    val data = history.filter { it.timestamp >= startTime }
    if (data.isEmpty()) return

    val normalColor = Color(0xFF6200EE)
    val chargingColor = Color(0xFF4CAF50)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width; val height = size.height
        val duration = now - startTime
        val barWidth = (width / (data.size.coerceAtLeast(1) * 1.5f)).coerceIn(1.dp.toPx(), 8.dp.toPx())
        
        data.forEach { entry ->
            val x = (entry.timestamp - startTime).toFloat() / duration * width
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
