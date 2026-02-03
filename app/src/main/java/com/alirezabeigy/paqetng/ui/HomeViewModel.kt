package com.alirezabeigy.paqetng.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alirezabeigy.paqetng.data.PaqetConfig
import com.alirezabeigy.paqetng.data.ConfigRepository
import com.google.gson.Gson
import com.alirezabeigy.paqetng.data.KcpBlockOptions
import com.alirezabeigy.paqetng.data.DefaultNetworkInfoProvider
import com.alirezabeigy.paqetng.data.SettingsRepository
import com.alirezabeigy.paqetng.data.toPaqetYaml
import kotlinx.coroutines.flow.first
import com.alirezabeigy.paqetng.paqet.PaqetRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.util.Log
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

class HomeViewModel(
    private val configRepository: ConfigRepository,
    private val paqetRunner: PaqetRunner,
    private val defaultNetworkInfoProvider: DefaultNetworkInfoProvider,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private companion object {
        private const val TAG = "HomeViewModel"
    }

    val configs: StateFlow<List<PaqetConfig>> = configRepository.configs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val isRunning: StateFlow<Boolean> = paqetRunner.isRunning
    val rootAvailable: StateFlow<Boolean?> = paqetRunner.rootAvailable

    private val _selectedConfigId = MutableStateFlow<String?>(null)
    val selectedConfigId: StateFlow<String?> = _selectedConfigId.asStateFlow()

    private val _editorConfig = MutableStateFlow<PaqetConfig?>(null)
    val editorConfig: StateFlow<PaqetConfig?> = _editorConfig.asStateFlow()

    private val _latencyMs = MutableStateFlow<Int?>(null)
    val latencyMs: StateFlow<Int?> = _latencyMs.asStateFlow()

    private val _latencyTesting = MutableStateFlow(false)
    val latencyTesting: StateFlow<Boolean> = _latencyTesting.asStateFlow()

    val showLatencyInUi: StateFlow<Boolean> = settingsRepository.showLatencyInUi
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _showAddConfigDialog = MutableStateFlow(false)
    val showAddConfigDialog: StateFlow<Boolean> = _showAddConfigDialog.asStateFlow()

    private val _showRootRequiredDialog = MutableStateFlow(false)
    val showRootRequiredDialog: StateFlow<Boolean> = _showRootRequiredDialog.asStateFlow()

    private val gson = Gson()

    init {
        paqetRunner.checkRoot()
        viewModelScope.launch {
            paqetRunner.rootAvailable.collectLatest { available ->
                if (available == false) _showRootRequiredDialog.value = true
            }
        }
        viewModelScope.launch {
            val lastId = settingsRepository.lastSelectedConfigId.first()
            if (!lastId.isNullOrEmpty()) _selectedConfigId.value = lastId
        }
    }

    fun selectConfig(id: String?) {
        _selectedConfigId.value = id
        viewModelScope.launch { settingsRepository.setLastSelectedConfigId(id) }
    }

    fun openAddConfigDialog() { _showAddConfigDialog.value = true }

    fun dismissAddConfigDialog() { _showAddConfigDialog.value = false }

    fun dismissRootRequiredDialog() { _showRootRequiredDialog.value = false }

    /** Parse text (paqet:// or JSON), add config. Returns true if parse succeeded (add is async). */
    fun addConfigFromImport(text: String?): Boolean {
        val config = PaqetConfig.parseFromImport(text, gson) ?: return false
        viewModelScope.launch {
            val port = settingsRepository.socksPort.first()
            val withPort = config.copy(
                id = "",
                socksListen = config.socksListen.ifEmpty { "127.0.0.1:$port" }
            )
            configRepository.add(withPort)
            _showAddConfigDialog.value = false
        }
        return true
    }

    fun openAdd() {
        _showAddConfigDialog.value = false
        viewModelScope.launch {
            val port = settingsRepository.socksPort.first()
            _editorConfig.value = PaqetConfig(
                id = "",
                name = "",
                serverAddr = "",
                networkInterface = "wlan0",
                ipv4Addr = "",
                routerMac = "",
                kcpKey = "",
                kcpBlock = KcpBlockOptions.default,
                socksListen = "127.0.0.1:$port"
            )
            val info = defaultNetworkInfoProvider.getDefaultNetworkInfo() ?: return@launch
            _editorConfig.value = (_editorConfig.value ?: return@launch).copy(
                networkInterface = info.interfaceName,
                ipv4Addr = info.ipv4Addr,
                routerMac = info.routerMac
            )
        }
    }

    fun openEdit(config: PaqetConfig) { _editorConfig.value = config }

    fun dismissEditor() { _editorConfig.value = null }

    fun saveConfig(config: PaqetConfig) {
        viewModelScope.launch {
            if (config.id.isEmpty()) configRepository.add(config)
            else configRepository.update(config)
            _editorConfig.value = null
        }
    }

    fun deleteConfig(id: String) {
        viewModelScope.launch {
            configRepository.delete(id)
            if (_selectedConfigId.value == id) {
                _selectedConfigId.value = null
                settingsRepository.setLastSelectedConfigId(null)
            }
        }
    }

    /**
     * Starts paqet with the given config. When paqet is successfully running, [onPaqetStarted]
     * is invoked so the VPN can be started (traffic will then go TUN → tun2socks → SOCKS → paqet).
     */
    fun connect(config: PaqetConfig?, onPaqetStarted: (() -> Unit)? = null) {
        if (config == null) return
        viewModelScope.launch {
            val allowLan = settingsRepository.socksListenLan.first()
            val logLevel = settingsRepository.logLevel.first()
            val port = config.socksPort()
            val socksListen = if (allowLan) "0.0.0.0:$port" else "127.0.0.1:$port"
            val info = defaultNetworkInfoProvider.getDefaultNetworkInfo()
            val configWithNetwork = (if (info != null) {
                config.copy(
                    networkInterface = info.interfaceName,
                    ipv4Addr = info.ipv4Addr,
                    routerMac = info.routerMac,
                    socksListen = socksListen
                )
            } else {
                config.copy(socksListen = socksListen)
            })
            val summary = "server=${configWithNetwork.serverAddr} iface=${configWithNetwork.networkInterface} ip=${configWithNetwork.ipv4Addr} routerMac=${configWithNetwork.routerMac} socks=${configWithNetwork.socksListen}"
            val started = paqetRunner.startWithYaml(configWithNetwork.toPaqetYaml(logLevel = logLevel), summary)
            if (started) onPaqetStarted?.invoke()
        }
    }

    fun disconnect() {
        _latencyMs.value = null
        paqetRunner.stop()
    }

    fun testLatency(config: PaqetConfig?) {
        Log.d(TAG, "testLatency called: config=${config?.serverAddr}, configNull=${config == null}")
        if (config == null) {
            Log.d(TAG, "testLatency: config is null, returning")
            return
        }
        viewModelScope.launch {
            Log.d(TAG, "testLatency: starting, setting latencyTesting=true")
            _latencyTesting.value = true
            _latencyMs.value = null
            var checkUrl = settingsRepository.connectionCheckUrl.first().trim()
            if (checkUrl.isNotEmpty() && !checkUrl.startsWith("http://") && !checkUrl.startsWith("https://")) {
                checkUrl = "https://$checkUrl"
            }
            if (checkUrl.isEmpty()) checkUrl = SettingsRepository.DEFAULT_CONNECTION_CHECK_URL
            Log.d(TAG, "testLatency: connection check URL=$checkUrl via SOCKS 127.0.0.1:${config.socksPort()}")
            val socksPort = config.socksPort()
            val ms = withContext(Dispatchers.IO) {
                runCatching {
                    val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort))
                    val url = URL(checkUrl)
                    val conn = url.openConnection(proxy) as? HttpURLConnection ?: return@runCatching null
                    try {
                        conn.requestMethod = "GET"
                        conn.connectTimeout = 10_000
                        conn.readTimeout = 10_000
                        conn.instanceFollowRedirects = true
                        val start = System.currentTimeMillis()
                        conn.connect()
                        conn.responseCode
                        (System.currentTimeMillis() - start).toInt()
                    } finally {
                        conn.disconnect()
                    }
                }.getOrElse { e: Throwable ->
                    Log.w(TAG, "testLatency: connection check failed (via proxy)", e)
                    null
                }
            }
            Log.d(TAG, "testLatency: result ms=$ms, setting _latencyMs and latencyTesting=false")
            _latencyMs.value = ms ?: -1
            _latencyTesting.value = false
        }
    }
}
