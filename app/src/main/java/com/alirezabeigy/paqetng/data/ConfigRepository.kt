package com.alirezabeigy.paqetng.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "paqet_configs")

private val CONFIGS_KEY = stringPreferencesKey("configs")
private val gson = Gson()
private val type = object : TypeToken<List<PaqetConfig>>() {}.type

class ConfigRepository(private val context: Context) {

    val configs: Flow<List<PaqetConfig>> = context.dataStore.data.map { prefs ->
        val json = prefs[CONFIGS_KEY] ?: "[]"
        (gson.fromJson<List<PaqetConfig>>(json, type) ?: emptyList())
    }

    suspend fun add(config: PaqetConfig) {
        context.dataStore.edit { prefs ->
            val list = (gson.fromJson<List<PaqetConfig>>(prefs[CONFIGS_KEY] ?: "[]", type) ?: emptyList()).toMutableList()
            list.add(config.copy(id = config.id.ifEmpty { UUID.randomUUID().toString() }))
            prefs[CONFIGS_KEY] = gson.toJson(list)
        }
    }

    suspend fun update(config: PaqetConfig) {
        context.dataStore.edit { prefs ->
            val list = (gson.fromJson<List<PaqetConfig>>(prefs[CONFIGS_KEY] ?: "[]", type) ?: emptyList()).toMutableList()
            val index = list.indexOfFirst { it.id == config.id }
            if (index >= 0) list[index] = config
            prefs[CONFIGS_KEY] = gson.toJson(list)
        }
    }

    suspend fun delete(id: String) {
        context.dataStore.edit { prefs ->
            val list = (gson.fromJson<List<PaqetConfig>>(prefs[CONFIGS_KEY] ?: "[]", type) ?: emptyList())
                .filter { it.id != id }
            prefs[CONFIGS_KEY] = gson.toJson(list)
        }
    }

    suspend fun getById(id: String): PaqetConfig? = configs.first().find { it.id == id }
}
