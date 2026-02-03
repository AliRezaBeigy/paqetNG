package com.alirezabeigy.paqetng.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.alirezabeigy.paqetng.R
import com.alirezabeigy.paqetng.data.SettingsRepository
import com.alirezabeigy.paqetng.data.ThemePref
import kotlinx.coroutines.launch

private object SettingsSpacing {
    val screenHorizontal = 24.dp
    val screenVertical = 24.dp
    val sectionSpacing = 28.dp
    val sectionTitleBottom = 12.dp
    val cardPadding = 16.dp
    val itemSpacing = 8.dp
    val textFieldSpacing = 16.dp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme by settingsRepository.theme.collectAsState(initial = ThemePref.SYSTEM)
    val socksPort by settingsRepository.socksPort.collectAsState(initial = SettingsRepository.DEFAULT_SOCKS_PORT)
    var portInput by remember(socksPort) { mutableStateOf(socksPort.toString()) }
    val connectionCheckUrl by settingsRepository.connectionCheckUrl.collectAsState(initial = SettingsRepository.DEFAULT_CONNECTION_CHECK_URL)
    var connectionCheckUrlInput by remember(connectionCheckUrl) { mutableStateOf(connectionCheckUrl) }
    val connectionCheckTimeout by settingsRepository.connectionCheckTimeoutSeconds.collectAsState(initial = SettingsRepository.DEFAULT_CONNECTION_CHECK_TIMEOUT_SECONDS)
    var timeoutInput by remember(connectionCheckTimeout) { mutableStateOf(connectionCheckTimeout.toString()) }
    val socksListenLan by settingsRepository.socksListenLan.collectAsState(initial = SettingsRepository.DEFAULT_SOCKS_LISTEN_LAN)
    val logLevel by settingsRepository.logLevel.collectAsState(initial = SettingsRepository.DEFAULT_LOG_LEVEL)
    val connectionMode by settingsRepository.connectionMode.collectAsState(initial = SettingsRepository.DEFAULT_CONNECTION_MODE)
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name) + " Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(vertical = SettingsSpacing.screenVertical)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SettingsSpacing.sectionSpacing)
        ) {
            // ——— General ———
            PreferenceSection(title = stringResource(R.string.pref_category_general)) {
                PreferenceThemeRow(
                    theme = theme,
                    onThemeSelected = { scope.launch { settingsRepository.setTheme(it) } }
                )
            }

            // ——— Connection ———
            PreferenceSection(title = stringResource(R.string.pref_category_connection)) {
                PreferenceSwitchRow(
                    title = stringResource(R.string.pref_allow_lan),
                    summary = stringResource(R.string.pref_allow_lan_summary),
                    checked = socksListenLan,
                    onCheckedChange = { scope.launch { settingsRepository.setSocksListenLan(it) } }
                )
                PreferenceLogLevelRow(
                    logLevel = logLevel,
                    onLogLevelSelected = { scope.launch { settingsRepository.setLogLevel(it) } }
                )
                PreferenceConnectionModeRow(
                    connectionMode = connectionMode,
                    onModeSelected = { scope.launch { settingsRepository.setConnectionMode(it) } }
                )

                var portFocused by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = portInput,
                    onValueChange = { portInput = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text(stringResource(R.string.pref_socks_port)) },
                    supportingText = { Text(stringResource(R.string.pref_socks_port_summary)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SettingsSpacing.textFieldSpacing)
                        .onFocusChanged { fs ->
                            if (portFocused && !fs.hasFocus) {
                                scope.launch {
                                    val port = portInput.toIntOrNull()?.coerceIn(1, 65535) ?: SettingsRepository.DEFAULT_SOCKS_PORT
                                    settingsRepository.setSocksPort(port)
                                    portInput = port.toString()
                                }
                            }
                            portFocused = fs.hasFocus
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                var urlFocused by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = connectionCheckUrlInput,
                    onValueChange = { connectionCheckUrlInput = it },
                    label = { Text(stringResource(R.string.pref_connection_check_url)) },
                    supportingText = { Text(stringResource(R.string.pref_connection_check_url_summary)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { fs ->
                            if (urlFocused && !fs.hasFocus) {
                                scope.launch {
                                    val normalized = connectionCheckUrlInput.trim().ifEmpty { SettingsRepository.DEFAULT_CONNECTION_CHECK_URL }
                                    settingsRepository.setConnectionCheckUrl(normalized)
                                    connectionCheckUrlInput = normalized
                                }
                            }
                            urlFocused = fs.hasFocus
                        },
                    singleLine = true,
                    placeholder = { Text(SettingsRepository.DEFAULT_CONNECTION_CHECK_URL) }
                )

                var timeoutFocused by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = timeoutInput,
                    onValueChange = { timeoutInput = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text(stringResource(R.string.pref_connection_check_timeout)) },
                    supportingText = { Text(stringResource(R.string.pref_connection_check_timeout_summary)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { fs ->
                            if (timeoutFocused && !fs.hasFocus) {
                                scope.launch {
                                    val timeout = timeoutInput.toIntOrNull()?.coerceIn(
                                        SettingsRepository.MIN_CONNECTION_CHECK_TIMEOUT,
                                        SettingsRepository.MAX_CONNECTION_CHECK_TIMEOUT
                                    ) ?: SettingsRepository.DEFAULT_CONNECTION_CHECK_TIMEOUT_SECONDS
                                    settingsRepository.setConnectionCheckTimeoutSeconds(timeout)
                                    timeoutInput = timeout.toString()
                                }
                            }
                            timeoutFocused = fs.hasFocus
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
private fun PreferenceSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = SettingsSpacing.cardPadding)
                .padding(bottom = SettingsSpacing.sectionTitleBottom)
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SettingsSpacing.cardPadding),
                verticalArrangement = Arrangement.spacedBy(SettingsSpacing.itemSpacing)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun PreferenceThemeRow(
    theme: ThemePref,
    onThemeSelected: (ThemePref) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val themeLabel = when (theme) {
        ThemePref.LIGHT -> context.getString(R.string.pref_theme_light)
        ThemePref.DARK -> context.getString(R.string.pref_theme_dark)
        ThemePref.SYSTEM -> context.getString(R.string.pref_theme_system)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            headlineContent = { Text(stringResource(R.string.pref_theme), style = MaterialTheme.typography.bodyLarge) },
            supportingContent = { Text(themeLabel, style = MaterialTheme.typography.bodySmall) },
            colors = ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                headlineColor = MaterialTheme.colorScheme.onSurface,
                supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ThemePref.entries.forEach { pref ->
                val label = when (pref) {
                    ThemePref.LIGHT -> context.getString(R.string.pref_theme_light)
                    ThemePref.DARK -> context.getString(R.string.pref_theme_dark)
                    ThemePref.SYSTEM -> context.getString(R.string.pref_theme_system)
                }
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onThemeSelected(pref)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = { Text(summary, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.onSurface,
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
private fun PreferenceLogLevelRow(
    logLevel: String,
    onLogLevelSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val levelLabel = when (logLevel) {
        "none" -> context.getString(R.string.pref_log_level_none)
        "debug" -> context.getString(R.string.pref_log_level_debug)
        "info" -> context.getString(R.string.pref_log_level_info)
        "warn" -> context.getString(R.string.pref_log_level_warn)
        "error" -> context.getString(R.string.pref_log_level_error)
        "fatal" -> context.getString(R.string.pref_log_level_fatal)
        else -> logLevel
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            headlineContent = { Text(stringResource(R.string.pref_log_level), style = MaterialTheme.typography.bodyLarge) },
            supportingContent = { Text(levelLabel, style = MaterialTheme.typography.bodySmall) },
            colors = ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                headlineColor = MaterialTheme.colorScheme.onSurface,
                supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SettingsRepository.LOG_LEVELS.forEach { level ->
                val label = when (level) {
                    "none" -> context.getString(R.string.pref_log_level_none)
                    "debug" -> context.getString(R.string.pref_log_level_debug)
                    "info" -> context.getString(R.string.pref_log_level_info)
                    "warn" -> context.getString(R.string.pref_log_level_warn)
                    "error" -> context.getString(R.string.pref_log_level_error)
                    "fatal" -> context.getString(R.string.pref_log_level_fatal)
                    else -> level
                }
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onLogLevelSelected(level)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun PreferenceConnectionModeRow(
    connectionMode: String,
    onModeSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val modeLabel = when (connectionMode) {
        "vpn" -> context.getString(R.string.pref_connection_mode_vpn)
        "socks" -> context.getString(R.string.pref_connection_mode_socks)
        else -> connectionMode
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            headlineContent = { Text(stringResource(R.string.pref_connection_mode), style = MaterialTheme.typography.bodyLarge) },
            supportingContent = { Text(modeLabel, style = MaterialTheme.typography.bodySmall) },
            colors = ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                headlineColor = MaterialTheme.colorScheme.onSurface,
                supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SettingsRepository.CONNECTION_MODES.forEach { mode ->
                val label = when (mode) {
                    "vpn" -> context.getString(R.string.pref_connection_mode_vpn)
                    "socks" -> context.getString(R.string.pref_connection_mode_socks)
                    else -> mode
                }
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onModeSelected(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}
