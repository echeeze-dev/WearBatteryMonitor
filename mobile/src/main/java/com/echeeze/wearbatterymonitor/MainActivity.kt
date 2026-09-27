package com.echeeze.wearbatterymonitor

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.echeeze.wearbatterymonitor.data.AppDatabase
import com.echeeze.wearbatterymonitor.data.BatteryEntry
import com.echeeze.wearbatterymonitor.data.BatteryRepositoryImpl
import com.echeeze.wearbatterymonitor.monitor.EarphoneBatteryMonitor
import com.echeeze.wearbatterymonitor.monitor.PhoneBatteryMonitor
import com.echeeze.wearbatterymonitor.sync.WearDataSyncer
import com.echeeze.wearbatterymonitor.ui.MainUiState
import com.echeeze.wearbatterymonitor.ui.MainViewModel
import com.echeeze.wearbatterymonitor.ui.components.BatterySection
import com.echeeze.wearbatterymonitor.ui.components.EarphoneSection
import com.echeeze.wearbatterymonitor.ui.components.HistoryDetailDialog
import com.echeeze.wearbatterymonitor.ui.theme.WearBatteryMonitorTheme
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * モバイルアプリのエントリーポイントとなるメイン Activity です。
 *
 * MVVM アーキテクチャの View 層として機能し、[MainViewModel] の状態を観察して
 * UI ([BatteryMonitorScreen]) をレンダリングします。
 */
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    private var batteryMonitorService: BatteryMonitorService? = null
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var phoneMonitor: PhoneBatteryMonitor
    private lateinit var earphoneMonitor: EarphoneBatteryMonitor
    private lateinit var wearDataSyncer: WearDataSyncer

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BatteryMonitorService.LocalBinder
            batteryMonitorService = binder.getService()
            syncFromService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            batteryMonitorService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = AppDatabase.getDatabase(this)
        val repository = BatteryRepositoryImpl(this, database.batteryDao())
        viewModel = MainViewModel(repository)

        enableEdgeToEdge()
        val sharedPref = getPreferences(MODE_PRIVATE)
        val isServiceRunning = sharedPref.getBoolean("service_running", false)
        val graphRangeHours = sharedPref.getInt("graph_range_hours", 3)
        val updateIntervalSeconds = sharedPref.getInt("update_interval_seconds", 60)

        viewModel.setServiceRunning(isServiceRunning)
        viewModel.setGraphRangeHours(graphRangeHours)
        viewModel.setUpdateIntervalSeconds(updateIntervalSeconds)

        phoneMonitor = PhoneBatteryMonitor(this) { percent, isCharging ->
            viewModel.updatePhoneBattery(percent, isCharging)
            if (!viewModel.uiState.value.isServiceRunning) {
                viewModel.addHistoryEntry(BatteryEntry("phone", percent, System.currentTimeMillis(), isCharging, Build.MODEL, "BATTERY_CHANGED"))
            }
        }

        earphoneMonitor = EarphoneBatteryMonitor(this) { percent, name, reason ->
            viewModel.updateEarphoneBattery(percent, name)
            if (!viewModel.uiState.value.isServiceRunning && (percent != null || name != null)) {
                viewModel.addHistoryEntry(BatteryEntry("earphone", percent ?: -1, System.currentTimeMillis(), false, name, reason))
            }
        }

        wearDataSyncer = WearDataSyncer(
            context = this,
            repository = repository,
            scope = activityScope,
            onWatchBatteryReceived = { percent, isCharging, deviceName, recordReason ->
                viewModel.updateWatchBattery(percent, isCharging)
                if (deviceName != null) {
                    viewModel.updateWatchName(deviceName)
                }
                if (!viewModel.uiState.value.isServiceRunning) {
                    viewModel.addHistoryEntry(BatteryEntry("watch", percent, System.currentTimeMillis(), isCharging, deviceName, recordReason))
                }
            },
            onRequestBatteryUpdate = {
                syncStatusToWear()
            }
        )

        viewModel.loadHistoryFromDb()

        if (isServiceRunning) {
            startBatteryService()
            bindService(Intent(this, BatteryMonitorService::class.java), serviceConnection, BIND_AUTO_CREATE)
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            WearBatteryMonitorTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Crossfade(
                        targetState = uiState.isLoading,
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
                                uiState = uiState,
                                onServiceToggle = { toggleService(it) },
                                onGraphRangeChange = { updateGraphRange(it) },
                                onIntervalChange = { updateInterval(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val (percent, isCharging) = phoneMonitor.startMonitoring()
        viewModel.updatePhoneBattery(percent, isCharging)
        earphoneMonitor.startMonitoring()
        wearDataSyncer.startSyncing()

        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.firstOrNull()?.displayName?.let { name ->
                viewModel.updateWatchName(name)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        phoneMonitor.stopMonitoring()
        earphoneMonitor.stopMonitoring()
        wearDataSyncer.stopSyncing()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (viewModel.uiState.value.isServiceRunning) {
            try {
                unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                // Ignore if not bound
            }
        }
        activityScope.cancel()
    }

    private fun syncFromService() {
        batteryMonitorService?.let { service ->
            viewModel.updatePhoneBattery(service.currentPhonePercent, service.currentPhoneCharging)
            viewModel.updateEarphoneBattery(service.currentEarphonePercent, service.currentEarphoneName)
            if (service.currentWatchName != null) {
                viewModel.updateWatchName(service.currentWatchName)
            }
            if (service.phoneHistory.isNotEmpty()) {
                viewModel.loadHistoryFromDb()
            }
        }
    }

    private fun syncStatusToWear() {
        val state = viewModel.uiState.value
        wearDataSyncer.syncStatusToWear(
            phonePercent = state.phonePercent,
            isCharging = state.phoneIsCharging,
            earphonePercent = state.earphonePercent,
            earphoneName = state.earphoneName,
            phoneHistory = state.phoneHistory,
            watchHistory = state.watchHistory,
            earphoneHistory = state.earphoneHistory,
            rangeHours = state.graphRangeHours
        )
    }

    private fun toggleService(enable: Boolean) {
        viewModel.setServiceRunning(enable)
        getPreferences(MODE_PRIVATE).edit {
            putBoolean("service_running", enable)
        }
        if (enable) {
            startBatteryService()
            bindService(Intent(this, BatteryMonitorService::class.java), serviceConnection, BIND_AUTO_CREATE)
        } else {
            try {
                unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                // Ignore
            }
            stopService(Intent(this, BatteryMonitorService::class.java))
        }
    }

    private fun startBatteryService() {
        val serviceIntent = Intent(this, BatteryMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun updateInterval(seconds: Int) {
        viewModel.setUpdateIntervalSeconds(seconds)
        getPreferences(MODE_PRIVATE).edit {
            putInt("update_interval_seconds", seconds)
        }
        batteryMonitorService?.updateTimerInterval(seconds)
    }

    private fun updateGraphRange(hours: Int) {
        viewModel.setGraphRangeHours(hours)
        getPreferences(MODE_PRIVATE).edit {
            putInt("graph_range_hours", hours)
        }
        syncStatusToWear()
    }
}

/**
 * メイン画面全体のレイアウトを表示する Composable です。
 *
 * @param uiState UI 状態 ([MainUiState])
 * @param onServiceToggle 常駐サービス切替時のコールバック
 * @param onGraphRangeChange グラフ範囲変更時のコールバック
 * @param onIntervalChange 更新間隔変更時のコールバック
 */
@Composable
fun BatteryMonitorScreen(
    uiState: MainUiState,
    onServiceToggle: (Boolean) -> Unit,
    onGraphRangeChange: (Int) -> Unit,
    onIntervalChange: (Int) -> Unit
) {
    val context = LocalContext.current
    var detailDialogData by remember { mutableStateOf<Pair<String, List<BatteryEntry>>?>(null) }

    val permsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.values.all { it }) onServiceToggle(true) else Toast.makeText(context, "Permissions required", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GraphRangeSelector(uiState.graphRangeHours, onGraphRangeChange)
        Spacer(modifier = Modifier.height(24.dp))

        BatterySection(
            icon = Icons.Default.Smartphone,
            title = Build.MODEL ?: "Smartphone",
            percent = uiState.phonePercent,
            isCharging = uiState.phoneIsCharging,
            history = uiState.phoneHistory,
            rangeHours = uiState.graphRangeHours,
            onGraphClick = { detailDialogData = (Build.MODEL ?: "Smartphone") to uiState.phoneHistory }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

        BatterySection(
            icon = Icons.Default.Watch,
            title = uiState.watchName ?: "Smartwatch",
            percent = uiState.watchPercent ?: 0,
            isCharging = uiState.watchIsCharging,
            history = uiState.watchHistory,
            isDisconnected = uiState.watchPercent == null,
            rangeHours = uiState.graphRangeHours,
            onGraphClick = { detailDialogData = (uiState.watchName ?: "Smartwatch") to uiState.watchHistory }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

        EarphoneSection(
            icon = Icons.Default.Bluetooth,
            name = uiState.earphoneName,
            percent = uiState.earphonePercent,
            history = uiState.earphoneHistory,
            rangeHours = uiState.graphRangeHours,
            onGraphClick = { detailDialogData = (uiState.earphoneName ?: "Bluetooth Device") to uiState.earphoneHistory }
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

        IntervalSlider(uiState.updateIntervalSeconds, onIntervalChange)
        Spacer(modifier = Modifier.height(24.dp))

        ServiceToggleButton(uiState.isServiceRunning) { enable ->
            if (enable) {
                val perms = mutableListOf<String>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (perms.isNotEmpty()) permsLauncher.launch(perms.toTypedArray()) else onServiceToggle(true)
            } else {
                onServiceToggle(false)
            }
        }
    }

    detailDialogData?.let { (title, history) ->
        HistoryDetailDialog(title = title, history = history, onDismiss = { detailDialogData = null })
    }
}

@Composable
fun ServiceToggleButton(isServiceRunning: Boolean, onToggle: (Boolean) -> Unit) {
    Button(
        onClick = { onToggle(!isServiceRunning) },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isServiceRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    ) {
        Text(if (isServiceRunning) "Stop Background Monitoring" else "Start Background Monitoring")
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
fun IntervalSlider(seconds: Int, onIntervalChange: (Int) -> Unit) {
    val intervals = remember {
        val list = mutableListOf<Int>()
        for (i in 1..10) list.add(i)
        for (i in 15..60 step 5) list.add(i)
        for (i in 120..300 step 60) list.add(i)
        for (i in 360..600 step 60) list.add(i)
        for (i in 900..1800 step 300) list.add(i)
        for (i in 2400..3600 step 600) list.add(i)
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

private fun formatInterval(seconds: Int): String {
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
