package com.alirezabeigy.paqetng

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.alirezabeigy.paqetng.data.AppLogBuffer
import com.alirezabeigy.paqetng.data.PaqetConfig
import com.alirezabeigy.paqetng.data.SettingsRepository
import com.alirezabeigy.paqetng.data.ThemePref
import com.alirezabeigy.paqetng.data.ConfigRepository
import com.alirezabeigy.paqetng.paqet.PaqetRunner
import com.alirezabeigy.paqetng.ui.HomeScreen
import com.alirezabeigy.paqetng.ui.HomeViewModel
import com.alirezabeigy.paqetng.ui.LogViewerScreen
import com.alirezabeigy.paqetng.ui.PacketDetailActivity
import com.alirezabeigy.paqetng.ui.PacketDumpScreen
import com.alirezabeigy.paqetng.ui.SettingsScreen
import com.alirezabeigy.paqetng.ui.theme.PaqetNGTheme
import com.alirezabeigy.paqetng.vpn.PaqetNGVpnService

class MainActivity : ComponentActivity() {

    private val app by lazy { application as PaqetNGApplication }
    private val logBuffer get() = app.logBuffer
    private val paqetRunner get() = app.paqetRunner
    private val configRepository by lazy { ConfigRepository(applicationContext) }
    private val defaultNetworkInfoProvider by lazy { com.alirezabeigy.paqetng.data.DefaultNetworkInfoProvider(applicationContext) }
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }

    private val viewModel: HomeViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    configRepository,
                    paqetRunner,
                    defaultNetworkInfoProvider,
                    settingsRepository
                ) as T
            }
        }
    }

    private var pendingConnectConfig: PaqetConfig? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingVpnStartRunnable: Runnable? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingConnectConfig?.let { doConnect(it) }
        }
        pendingConnectConfig = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by settingsRepository.theme.collectAsState(initial = ThemePref.SYSTEM)
            val darkTheme = when (theme) {
                ThemePref.LIGHT -> false
                ThemePref.DARK -> true
                ThemePref.SYSTEM -> isSystemInDarkTheme()
            }
            var showSettings by remember { mutableStateOf(false) }
            var showLogViewer by remember { mutableStateOf(false) }
            var showPacketDump by remember { mutableStateOf(false) }
            val isRunning by viewModel.isRunning.collectAsState(initial = false)
            val connectionMode by settingsRepository.connectionMode.collectAsState(initial = SettingsRepository.DEFAULT_CONNECTION_MODE)
            val configs by viewModel.configs.collectAsState(initial = emptyList())
            val selectedConfigId by viewModel.selectedConfigId.collectAsState()
            val connectedConfigId by viewModel.connectedConfigId.collectAsState()
            val latencyMs by viewModel.latencyMs.collectAsState()
            val latencyTesting by viewModel.latencyTesting.collectAsState(initial = false)
            val selectedConfig: PaqetConfig? = remember(configs, selectedConfigId) {
                configs.find { it.id == selectedConfigId } ?: configs.firstOrNull()
            }
            // When user changes the selected profile while connected, restart paqet (and VPN) with the new profile
            LaunchedEffect(selectedConfigId, isRunning, connectedConfigId) {
                val newConfig = selectedConfigId?.let { id -> configs.find { it.id == id } }
                if (isRunning && newConfig != null && selectedConfigId != connectedConfigId) {
                    handleDisconnect()
                    handleConnect(newConfig, connectionMode)
                }
            }
            PaqetNGTheme(darkTheme = darkTheme) {
                when {
                    showPacketDump -> {
                        BackHandler { showPacketDump = false }
                        PacketDumpScreen(
                            tcpdumpBuffer = app.tcpdumpBuffer,
                            tcpdumpRunner = app.tcpdumpRunner,
                            isPaqetRunning = isRunning,
                            connectionMode = connectionMode,
                            connectedConfig = selectedConfig,
                            latencyMs = latencyMs,
                            latencyTesting = latencyTesting,
                            onTestConnection = { viewModel.testLatency(selectedConfig) },
                            onBack = { showPacketDump = false },
                            onOpenPacketDetail = { packetText ->
                                startActivity(
                                    Intent(this, PacketDetailActivity::class.java)
                                        .putExtra(PacketDetailActivity.EXTRA_PACKET_TEXT, packetText)
                                )
                            }
                        )
                    }
                    showLogViewer -> {
                        BackHandler { showLogViewer = false }
                        LogViewerScreen(
                            logBuffer = logBuffer,
                            onBack = { showLogViewer = false },
                            isVpnConnected = isRunning,
                            onPacketDumpClick = { showPacketDump = true }
                        )
                    }
                    showSettings -> {
                        BackHandler { showSettings = false }
                        SettingsScreen(
                            settingsRepository = settingsRepository,
                            onBack = { showSettings = false }
                        )
                    }
                    else -> {
                        HomeScreen(
                            viewModel = viewModel,
                            onSettingsClick = { showSettings = true },
                            onLogsClick = { showLogViewer = true },
                            onConnect = { handleConnect(it, connectionMode) },
                            onDisconnect = { handleDisconnect() }
                        )
                    }
                }
            }
        }
    }

    private fun handleConnect(config: PaqetConfig?, connectionMode: String) {
        if (config == null) return
        logBuffer.clear()
        if (connectionMode == "socks") {
            // SOCKS-only mode: start paqet only, no VPN
            logBuffer.append(AppLogBuffer.TAG_VPN, "Starting paqet (SOCKS-only mode); VPN will not start.")
            viewModel.connect(config, null)
            return
        }
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            pendingConnectConfig = config
            vpnPermissionLauncher.launch(prepare)
        } else {
            doConnect(config)
        }
    }

    private fun doConnect(config: PaqetConfig) {
        cancelPendingVpnStart()
        logBuffer.append(AppLogBuffer.TAG_VPN, "Starting paqet; VPN will start once SOCKS is listening on port ${config.socksPort()}")
        viewModel.connect(config) {
            logBuffer.append(AppLogBuffer.TAG_VPN, "Paqet started; waiting for SOCKS to bind then starting VPN…")
            // Give paqet time to bind to 127.0.0.1:socksPort before tun2socks tries to connect (same idea as v2rayNG).
            val runnable = Runnable {
                pendingVpnStartRunnable = null
                logBuffer.append(AppLogBuffer.TAG_VPN, "VPN starting port=${config.socksPort()} config=${config.name.ifEmpty { config.serverAddr }}")
                val intent = Intent(this, PaqetNGVpnService::class.java)
                    .putExtra(PaqetNGVpnService.EXTRA_SOCKS_PORT, config.socksPort())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }
            pendingVpnStartRunnable = runnable
            mainHandler.postDelayed(runnable, 800)
        }
    }

    private fun cancelPendingVpnStart() {
        pendingVpnStartRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingVpnStartRunnable = null
    }

    private fun handleDisconnect() {
        cancelPendingVpnStart()
        // Stop Paqet first (and kill any paqet by name), then tell VPN service to tear down
        viewModel.disconnect()
        startService(
            Intent(this, PaqetNGVpnService::class.java).setAction(PaqetNGVpnService.ACTION_STOP)
        )
        stopService(Intent(this, PaqetNGVpnService::class.java))
    }
}
