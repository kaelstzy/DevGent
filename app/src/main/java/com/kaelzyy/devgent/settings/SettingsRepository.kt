package com.kaelzyy.devgent.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val hasApiKey: Boolean,
    val baseUrl: String,
    val model: String,
    val provider: String,
    val reasoning: String,
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        const val DEFAULT_PROVIDER = "openai"
        const val DEFAULT_REASONING = "off"
        val DEFAULT = AppSettings(
            hasApiKey = false,
            baseUrl = DEFAULT_BASE_URL,
            model = DEFAULT_MODEL,
            provider = DEFAULT_PROVIDER,
            reasoning = DEFAULT_REASONING,
        )
    }
}

class SettingsRepository(private val context: Context) {

    val appSettings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            hasApiKey = prefs[KEY_API_KEY]?.isNotEmpty() == true,
            baseUrl = prefs[KEY_BASE_URL] ?: AppSettings.DEFAULT_BASE_URL,
            model = prefs[KEY_MODEL] ?: AppSettings.DEFAULT_MODEL,
            provider = prefs[KEY_PROVIDER] ?: AppSettings.DEFAULT_PROVIDER,
            reasoning = prefs[KEY_REASONING] ?: AppSettings.DEFAULT_REASONING,
        )
    }

    suspend fun saveApiKey(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("API key kosong.")
        val encrypted = KeyCrypto.encrypt(trimmed)
        context.settingsDataStore.edit { it[KEY_API_KEY] = encrypted }
    }

    suspend fun getApiKey(): String? {
        val stored = context.settingsDataStore.data.first()[KEY_API_KEY] ?: return null
        return runCatching { KeyCrypto.decrypt(stored) }.getOrNull()
    }

    suspend fun clearApiKey() {
        context.settingsDataStore.edit { it.remove(KEY_API_KEY) }
        runCatching { KeyCrypto.removeKey() }
    }

    suspend fun saveBaseUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        context.settingsDataStore.edit { it[KEY_BASE_URL] = trimmed }
    }

    suspend fun saveModel(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        context.settingsDataStore.edit { it[KEY_MODEL] = trimmed }
    }

    suspend fun saveProvider(id: String) {
        val trimmed = id.trim()
        if (trimmed.isEmpty()) return
        context.settingsDataStore.edit { it[KEY_PROVIDER] = trimmed }
    }

    suspend fun saveReasoning(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        context.settingsDataStore.edit { it[KEY_REASONING] = trimmed }
    }

    private companion object {
        val KEY_API_KEY = stringPreferencesKey("api_key_encrypted")
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_MODEL = stringPreferencesKey("model")
        val KEY_PROVIDER = stringPreferencesKey("provider")
        val KEY_REASONING = stringPreferencesKey("reasoning")
    }
}
