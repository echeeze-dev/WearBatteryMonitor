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
import androidx.compose.animation.Crossfade
import androidx.core.content.edit
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.echeeze.wearbatterymonitor.data.AppDatabase
import com.echeeze.wearbatterymonitor.data.BatteryDao
import com.echeeze.wearbatterymonitor.data.BatteryEntry
import com.echeeze.wearbatterymonitor.data.BatteryRepository
import com.echeeze.wearbatterymonitor.data.BatteryRepositoryImpl
import com.echeeze.wearbatterymonitor.data.Constants
import com.echeeze.wearbatterymonitor.ui.theme.WearBatteryMonitorTheme
import com.google.android.gms.wearable.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity(), MessageClient.OnMessageReceivedListener, DataClient.OnDataChangedListener {

    private var batteryPercent by mutableIntStateOf(0)
    private var isCharging by mutableStateOf(false)
    private var watchPercent by mutableStateOf<Int?>(null)
    private var watchIsCharging by mutableStateOf(false)
    private var earphonePercent by mutableStateOf<Int?>(null)
    private var earphoneName by mutableStateOf<String?>(null)
    private var watchName by mutableStateOf<String?>(null)
    private var isServiceRunning by mutableStateOf(false)
    private var graphRangeHours by mutableIntStateOf(3)
    private var updateIntervalSeconds by mutableIntStateOf(60)
    private var isLoading by mutableStateOf(true)

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var database: AppDatabase
    private lateinit var batteryDao: BatteryDao
    private lateinit var repository: BatteryRepository

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
            val phoneData = service.phoneHistory.toList()
            val watchData = service.watchHistory.toList()
            val earphoneData = service.earphoneHistory.toList()
            
            activityScope.launch(Dispatchers.Main) {
                if (phoneHistory != phoneData) {
                    phoneHistory.clear()
                    phoneHistory.addAll(phoneData)
                }
                if (watchHistory != watchData) {
                    watchHistory.clear()
                    watchHistory.addAll(watchData)
                }
                if (earphoneHistory != earphoneData) {
                    earphoneHistory.clear()
                    earphoneHistory.addAll(earphoneData)
                }
                
                batteryPercent = service.currentPhonePercent
                isCharging = service.currentPhoneCharging
                earphonePercent = service.currentEarphonePercent
                earphoneName = service.currentEarphoneName
            }
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
                    addHistory(phoneHistory, "phone", batteryPercent, isCharging, Build.MODEL, "BATTERY_CHANGED")
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
        database = AppDatabase.getDatabase(this)
        batteryDao = database.batteryDao()
        repository = BatteryRepositoryImpl(this, batteryDao)
        
        enableEdgeToEdge()
        val sharedPref = getPreferences(MODE_PRIVATE)
        isServiceRunning = sharedPref.getBoolean("service_running", false)
        graphRangeHours = sharedPref.getInt("graph_range_hours", 3)
        updateIntervalSeconds = sharedPref.getInt("update_interval_seconds", 60)
        
        loadHistoryFromDb()
        
        if (isServiceRunning) {
            startBatteryService()
            bindService(Intent(this, BatteryMonitorService::class.java), serviceConnection, BIND_AUTO_CREATE)
        }

        setContent {
            WearBatteryMonitorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Crossfade(
                        targetState = isLoading,
                        modifier = Modifier.padding(innerPadding)
                    ) { loading ->
                        if (loading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "読み込み中...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        } else {
                            BatteryMonitorScreen(
                                phonePercent = batteryPercent,
                                phoneIsCharging = isCharging,
                                phoneHistory = phoneHistory,
                                watchPercent = watchPercent,
                                watchIsCharging = watchIsCharging,
                                watchHistory = watchHistory,
                                earphonePercent = earphonePercent,
                                earphoneName = earphoneName,
                                earphoneHistory = earphoneHistory,
                                isServiceRunning = isServiceRunning,
                                onServiceToggle = { toggleService(it) },
                                graphRangeHours = graphRangeHours,
                                onGraphRangeChange = { updateGraphRange(it) },
                                updateIntervalSeconds = updateIntervalSeconds,
                                onIntervalChange = { updateInterval(it) },
                                watchName = watchName
                            )
                        }
                    }
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

    private fun addHistory(list: MutableList<BatteryEntry>, type: String, percent: Int, isCharging: Boolean, deviceName: String? = null, recordReason: String? = null, intervalSeconds: Int? = null) {
        // If service is running, it handles history. Only add here if NOT running.
        if (!isServiceRunning) {
            val entry = BatteryEntry(type, percent, System.currentTimeMillis(), isCharging, deviceName, recordReason, intervalSeconds)
            list.add(entry)
            if (list.size > Constants.MAX_HISTORY_SIZE) list.removeAt(0)
            
            activityScope.launch(Dispatchers.IO) {
                repository.insertEntry(entry)
            }
        } else {
            // If running, we sync from service periodically or on demand
            syncHistoryFromService()
        }
    }

    private fun fetchCurrentBatteryState() {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level != -1 && scale != -1) {
            batteryPercent = (level * 100 / scale.toFloat()).toInt()
        }
        batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)?.let {
            isCharging = (it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL)
        }
    }

    private fun loadHistoryFromDb() {
        activityScope.launch(Dispatchers.IO) {
            val phoneData = repository.getHistoryByType("phone")
            val watchData = repository.getHistoryByType("watch")
            val earphoneData = repository.getHistoryByType("earphone")
            
            withContext(Dispatchers.Main) {
                if (phoneHistory != phoneData) {
                    phoneHistory.clear()
                    phoneHistory.addAll(phoneData)
                }
                if (watchHistory != watchData) {
                    watchHistory.clear()
                    watchHistory.addAll(watchData)
                }
                if (earphoneHistory != earphoneData) {
                    earphoneHistory.clear()
                    earphoneHistory.addAll(earphoneData)
                }
                
                fetchCurrentBatteryState()
                
                // Ensure Compose recomposition and layout calculation complete before hiding loading indicator
                delay(200)
                isLoading = false
            }
        }
    }

    private fun updateEarphoneState(intent: Intent) {
        val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
        
        var name: String? = null
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE, android.bluetooth.BluetoothDevice::class.java)
                         else @Suppress("DEPRECATION") intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
            name = device?.name
            earphoneName = name
        }

        if (level != -1) { earphonePercent = level; addHistory(earphoneHistory, "earphone", level, false, name, "BT_BATTERY_CHANGED") }
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
        val percent = dataMap.getInt("watch_percent")
        val isCharging = dataMap.getBoolean("watch_is_charging")
        val deviceName = if (dataMap.containsKey("device_name")) dataMap.getString("device_name") else null
        val recordReason = if (dataMap.containsKey("record_reason")) dataMap.getString("record_reason") else "DATA_SYNC"

        watchPercent = percent
        watchIsCharging = isCharging
        addHistory(watchHistory, "watch", percent, isCharging, deviceName, recordReason)
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
        activityScope.cancel()
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
        if (messageEvent.path == "/request_battery_update") {
            addHistory(phoneHistory, "phone", batteryPercent, isCharging, Build.MODEL, "WEAR_REQUEST")
            sendBatteryStatusToWear()
        }
    }

    private fun sendBatteryStatusToWear() {
        val putDataMapReq = PutDataMapRequest.create("/battery_status")
        putDataMapReq.dataMap.putInt("battery_percent", batteryPercent)
        putDataMapReq.dataMap.putBoolean("is_charging", isCharging)
        earphonePercent?.let { putDataMapReq.dataMap.putInt("earphone_percent", it) }
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
    earphonePercent: Int?, earphoneName: String?, earphoneHistory: List<BatteryEntry>,
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
            icon = Icons.Default.Bluetooth,
            name = earphoneName,
            percent = earphonePercent,
            history = earphoneHistory,
            rangeHours = graphRangeHours,
            onGraphClick = { detailDialogData = (earphoneName ?: "Bluetooth Device") to earphoneHistory.toList() }
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
        // New steps for 10m to 1h
        for (i in 900..1800 step 300) list.add(i) // 15, 20, 25, 30m
        for (i in 2400..3600 step 600) list.add(i) // 40, 50, 60m
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
            Text("1h", style = MaterialTheme.typography.labelSmall)
        }
    }
}

fun formatInterval(seconds: Int): String {
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

@Composable
fun GraphRangeSelector(selectedHours: Int, onRangeChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Graph Range:", style = MaterialTheme.typography.labelLarge)
        listOf(3, 6, 12, 24, 0).forEach { hours ->
            FilterChip(
                selected = selectedHours == hours,
                onClick = { onRangeChange(hours) },
                label = { Text(if (hours == 0) "ALL" else "${hours}h") }
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
        if (history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.height(120.dp).fillMaxWidth(0.9f).clickable { onGraphClick() }) { 
                HistoryGraph(history, rangeHours, scrollState) 
            }
            DebugInfoText(history)
        }
    }
}

@Composable
fun EarphoneSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String?,
    percent: Int?,
    history: List<BatteryEntry>,
    rangeHours: Int,
    onGraphClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = name ?: "Bluetooth Device", style = MaterialTheme.typography.headlineSmall)
        }
        val isConnected = name != null
        Text(
            text = if (isConnected) (if (percent != null && percent != -1) "$percent%" else "Connected") else "Disconnected",
            style = MaterialTheme.typography.displayLarge,
            color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        if (history.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.height(120.dp).fillMaxWidth(0.9f).clickable { onGraphClick() }) { 
                HistoryGraph(history, rangeHours, scrollState) 
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
                            // Column 1: Time (Fixed Width)
                            Text(
                                text = sdf.format(Date(entry.timestamp)).substringAfter(" "),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(70.dp)
                            )

                            // Column 2: Reason and Details (Flexible)
                            Column(modifier = Modifier.weight(1f)) {
                                val reasonTitle = when (entry.recordReason) {
                                    "BATTERY_CHANGED" -> "⚡ Battery"
                                    "PERIODIC_INTERVAL" -> "⏱ Periodic" + (entry.intervalSeconds?.let { " (${formatInterval(it)})" } ?: "")
                                    "INITIAL_FETCH" -> "🚀 Initial"
                                    "WEAR_REQUEST" -> "⌚ Wear Req"
                                    "BT_BATTERY_CHANGED", "BT_UPDATE" -> "🎧 BT Battery"
                                    "BT_CONNECTION" -> "🔗 Bluetooth"
                                    "DATA_SYNC" -> "🔄 Sync"
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

                            // Column 3: Percentage (Fixed Width, Right Aligned)
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
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}

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


@Composable
fun HistoryGraph(history: List<BatteryEntry>, rangeHours: Int, scrollState: ScrollState) {
    if (history.isEmpty()) return
    
    val now = System.currentTimeMillis()
    // 1. Determine the stable timeline range
    val minDataTs = history.minByOrNull { it.timestamp }?.timestamp ?: now
    val latestTimestamp = now
    val totalTimelineDuration = (latestTimestamp - minDataTs).coerceAtLeast(1L)
    
    val textMeasurer = rememberTextMeasurer()
    val timeSdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val daySdf = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }

    // Scroll to end on first load or when new data arrives
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
        
        // 2. Calculate Canvas Width based on time density
        val canvasWidthPx = if (rangeHours == 0) {
            viewportWidthPx
        } else {
            val pxPerHour = viewportWidthPx / rangeHours.toFloat()
            val totalHours = totalTimelineDuration.toFloat() / 3600000f
            (totalHours * pxPerHour).coerceAtLeast(viewportWidthPx)
        }
        
        Column(modifier = Modifier.fillMaxSize().horizontalScroll(scrollState)) {
            val canvasWidthDp = with(LocalDensity.current) { canvasWidthPx.toDp() }
            
            // Layer 0: Date Header (Outside the main graph area)
            Canvas(modifier = Modifier.height(20.dp).width(canvasWidthDp)) {
                var lastDay: Int = -1
                val calendar = Calendar.getInstance()
                history.forEach { entry ->
                    val x = ((entry.timestamp - minDataTs).toFloat() / totalTimelineDuration) * size.width
                    calendar.timeInMillis = entry.timestamp
                    val currentDay = calendar.get(Calendar.DAY_OF_YEAR)
                    if (lastDay != -1 && currentDay != lastDay) {
                        drawLine(color = dayDividerColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1.dp.toPx())
                        drawText(
                            textMeasurer = textMeasurer,
                            text = daySdf.format(Date(entry.timestamp)),
                            topLeft = Offset(x + 4.dp.toPx(), 2.dp.toPx()),
                            style = TextStyle(fontSize = 11.sp, color = dayDividerColor, fontWeight = FontWeight.Bold)
                        )
                    }
                    lastDay = currentDay
                }
            }

            // Main Graph Area
            Canvas(modifier = Modifier.weight(1f).width(canvasWidthDp)) {
                val width = size.width
                val height = size.height
                
                // Draw horizontal grid lines
                drawLine(gridColor, start = Offset(0f, 0f), end = Offset(width, 0f), strokeWidth = 0.5.dp.toPx())
                drawLine(gridColor, start = Offset(0f, height / 2), end = Offset(width, height / 2), strokeWidth = 0.5.dp.toPx())
                drawLine(gridColor, start = Offset(0f, height), end = Offset(width, height), strokeWidth = 0.5.dp.toPx())

                var lastDeviceName: String? = null
                var deviceRowIndex = -1
                var lastTimeLabelEndX = -100f

                val barWidth = (width / (history.size.coerceAtLeast(1) * 1.2f)).coerceIn(2.dp.toPx(), 12.dp.toPx())

                history.forEachIndexed { index, entry ->
                    val x = ((entry.timestamp - minDataTs).toFloat() / totalTimelineDuration) * width
                    
                    // Device Boundary Lines & Labels (5-row rotation to prevent overlap)
                    if (index == 0 || entry.deviceName != lastDeviceName) {
                        if (index > 0) {
                            drawLine(
                                color = boundaryColor,
                                start = Offset(x, 0f),
                                end = Offset(x, height),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }
                        entry.deviceName?.let { name ->
                            deviceRowIndex = (deviceRowIndex + 1) % 5
                            val yOffset = 2.dp.toPx() + (deviceRowIndex * 16.dp.toPx())
                            
                            drawText(
                                textMeasurer = textMeasurer,
                                text = name,
                                topLeft = Offset(x + 4.dp.toPx(), yOffset),
                                style = TextStyle(fontSize = 10.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold)
                            )
                        }
                        lastDeviceName = entry.deviceName
                    }

                    // Periodic Time Labels
                    if (index % 60 == 0 || index == history.size - 1) {
                        val timeStr = timeSdf.format(Date(entry.timestamp))
                        val textStyle = TextStyle(fontSize = 9.sp, color = gridColor)
                        val measured = textMeasurer.measure(timeStr, textStyle)
                        if (x > lastTimeLabelEndX + 32.dp.toPx()) {
                            drawText(
                                textMeasurer = textMeasurer,
                                text = timeStr,
                                topLeft = Offset(x, height / 2 + 2.dp.toPx()),
                                style = textStyle
                            )
                            lastTimeLabelEndX = x + measured.size.width
                        }
                    }

                    // Background for active service
                    drawRect(color = serviceActiveBgColor, topLeft = Offset(x - barWidth / 2, 0f), size = Size(barWidth, height))

                    // Status line at bottom
                    val statusColor = if (entry.percent >= 0) chargingColor else disconnectedColor
                    drawRect(color = statusColor, topLeft = Offset(x - barWidth / 2, height - 2.dp.toPx()), size = Size(barWidth, 2.dp.toPx()))

                    // Battery Percent Bar
                    if (entry.percent >= 0) {
                        val barHeight = (entry.percent / 100f) * height
                        drawRect(
                            color = if (entry.isCharging) chargingColor else normalColor,
                            topLeft = Offset(x - barWidth / 2, height - barHeight),
                            size = Size(barWidth, barHeight)
                        )
                    }
                }
            }
        }
    }
}
