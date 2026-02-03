package com.alirezabeigy.paqetng.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val THEME_KEY = stringPreferencesKey("theme")
private val SOCKS_PORT_KEY = intPreferencesKey("socks_port")
private val LAST_SELECTED_CONFIG_ID_KEY = stringPreferencesKey("last_selected_config_id")
private val CONNECTION_CHECK_URL_KEY = stringPreferencesKey("connection_check_url")
private val CONNECTION_CHECK_TIMEOUT_KEY = intPreferencesKey("connection_check_timeout_seconds")
private val START_ON_BOOT_KEY = booleanPreferencesKey("start_on_boot")
private val PERSISTENT_NOTIFICATION_KEY = booleanPreferencesKey("persistent_notification")
private val SHOW_LATENCY_IN_UI_KEY = booleanPreferencesKey("show_latency_in_ui")
private val AUTO_RECONNECT_KEY = booleanPreferencesKey("auto_reconnect")
private val SOCKS_LISTEN_LAN_KEY = booleanPreferencesKey("socks_listen_lan")
private val LOG_LEVEL_KEY = stringPreferencesKey("log_level")
private val CONNECTION_MODE_KEY = stringPreferencesKey("connection_mode")

enum class ThemePref(val value: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    companion object {
        fun from(value: String?) = entries.find { it.value == value } ?: SYSTEM
    }
}

class SettingsRepository(private val context: Context) {

    val theme: Flow<ThemePref> = context.settingsDataStore.data.map { prefs ->
        ThemePref.from(prefs[THEME_KEY])
    }

    val socksPort: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[SOCKS_PORT_KEY] ?: DEFAULT_SOCKS_PORT
    }

    val lastSelectedConfigId: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[LAST_SELECTED_CONFIG_ID_KEY]
    }

    val connectionCheckUrl: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[CONNECTION_CHECK_URL_KEY] ?: DEFAULT_CONNECTION_CHECK_URL
    }

    val connectionCheckTimeoutSeconds: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[CONNECTION_CHECK_TIMEOUT_KEY] ?: DEFAULT_CONNECTION_CHECK_TIMEOUT_SECONDS
    }

    val startOnBoot: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[START_ON_BOOT_KEY] ?: DEFAULT_START_ON_BOOT
    }

    val persistentNotification: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[PERSISTENT_NOTIFICATION_KEY] ?: DEFAULT_PERSISTENT_NOTIFICATION
    }

    val showLatencyInUi: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[SHOW_LATENCY_IN_UI_KEY] ?: DEFAULT_SHOW_LATENCY_IN_UI
    }

    val autoReconnect: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[AUTO_RECONNECT_KEY] ?: DEFAULT_AUTO_RECONNECT
    }

    val socksListenLan: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[SOCKS_LISTEN_LAN_KEY] ?: DEFAULT_SOCKS_LISTEN_LAN
    }

    val logLevel: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[LOG_LEVEL_KEY] ?: DEFAULT_LOG_LEVEL
    }

    val connectionMode: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[CONNECTION_MODE_KEY] ?: DEFAULT_CONNECTION_MODE
    }

    suspend fun setTheme(theme: ThemePref) {
        context.settingsDataStore.edit { it[THEME_KEY] = theme.value }
    }

    suspend fun setSocksPort(port: Int) {
        context.settingsDataStore.edit { it[SOCKS_PORT_KEY] = port.coerceIn(1, 65535) }
    }

    suspend fun setLastSelectedConfigId(id: String?) {
        context.settingsDataStore.edit { prefs ->
            if (id != null) prefs[LAST_SELECTED_CONFIG_ID_KEY] = id
            else prefs.remove(LAST_SELECTED_CONFIG_ID_KEY)
        }
    }

    suspend fun setConnectionCheckUrl(url: String) {
        context.settingsDataStore.edit {
            it[CONNECTION_CHECK_URL_KEY] = url.trim().ifEmpty { DEFAULT_CONNECTION_CHECK_URL }
        }
    }

    suspend fun setConnectionCheckTimeoutSeconds(seconds: Int) {
        context.settingsDataStore.edit {
            it[CONNECTION_CHECK_TIMEOUT_KEY] = seconds.coerceIn(MIN_CONNECTION_CHECK_TIMEOUT, MAX_CONNECTION_CHECK_TIMEOUT)
        }
    }

    suspend fun setStartOnBoot(enabled: Boolean) {
        context.settingsDataStore.edit { it[START_ON_BOOT_KEY] = enabled }
    }

    suspend fun setPersistentNotification(enabled: Boolean) {
        context.settingsDataStore.edit { it[PERSISTENT_NOTIFICATION_KEY] = enabled }
    }

    suspend fun setShowLatencyInUi(enabled: Boolean) {
        context.settingsDataStore.edit { it[SHOW_LATENCY_IN_UI_KEY] = enabled }
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.settingsDataStore.edit { it[AUTO_RECONNECT_KEY] = enabled }
    }

    suspend fun setSocksListenLan(enabled: Boolean) {
        context.settingsDataStore.edit { it[SOCKS_LISTEN_LAN_KEY] = enabled }
    }

    suspend fun setLogLevel(level: String) {
        context.settingsDataStore.edit {
            it[LOG_LEVEL_KEY] = if (level in LOG_LEVELS) level else DEFAULT_LOG_LEVEL
        }
    }

    suspend fun setConnectionMode(mode: String) {
        context.settingsDataStore.edit {
            it[CONNECTION_MODE_KEY] = if (mode in CONNECTION_MODES) mode else DEFAULT_CONNECTION_MODE
        }
    }

    companion object {
        const val DEFAULT_SOCKS_PORT = 1284
        const val DEFAULT_CONNECTION_CHECK_URL = "https://www.gstatic.com/generate_204"
        const val DEFAULT_CONNECTION_CHECK_TIMEOUT_SECONDS = 10
        const val MIN_CONNECTION_CHECK_TIMEOUT = 3
        const val MAX_CONNECTION_CHECK_TIMEOUT = 60
        const val DEFAULT_START_ON_BOOT = false
        const val DEFAULT_PERSISTENT_NOTIFICATION = true
        const val DEFAULT_SHOW_LATENCY_IN_UI = true
        const val DEFAULT_AUTO_RECONNECT = false
        const val DEFAULT_SOCKS_LISTEN_LAN = false
        const val DEFAULT_LOG_LEVEL = "debug"
        const val DEFAULT_CONNECTION_MODE = "vpn"

        val LOG_LEVELS = listOf("none", "debug", "info", "warn", "error", "fatal")
        val CONNECTION_MODES = listOf("vpn", "socks")
    }
}
