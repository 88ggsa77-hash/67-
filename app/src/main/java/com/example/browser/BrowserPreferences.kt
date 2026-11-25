package com.example.browser

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.viaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "via_browser_preferences"
)

data class BrowserSettings(
    val isAdBlockEnabled: Boolean = true,
    val isImagesDisabled: Boolean = false,
    val isNightModeEnabled: Boolean = false,
    val isDesktopModeEnabled: Boolean = false
)

class BrowserPreferences(context: Context) {

    private val dataStore = context.applicationContext.viaDataStore

    private object Keys {
        val AD_BLOCK_ENABLED = booleanPreferencesKey("ad_block_enabled")
        val IMAGES_DISABLED = booleanPreferencesKey("images_disabled")
        val NIGHT_MODE_ENABLED = booleanPreferencesKey("night_mode_enabled")
        val DESKTOP_MODE_ENABLED = booleanPreferencesKey("desktop_mode_enabled")
    }

    val settingsFlow: Flow<BrowserSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            BrowserSettings(
                isAdBlockEnabled = prefs[Keys.AD_BLOCK_ENABLED] ?: true,
                isImagesDisabled = prefs[Keys.IMAGES_DISABLED] ?: false,
                isNightModeEnabled = prefs[Keys.NIGHT_MODE_ENABLED] ?: false,
                isDesktopModeEnabled = prefs[Keys.DESKTOP_MODE_ENABLED] ?: false
            )
        }

    suspend fun setAdBlockEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.AD_BLOCK_ENABLED] = enabled
        }
    }

    suspend fun setImagesDisabled(disabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.IMAGES_DISABLED] = disabled
        }
    }

    suspend fun setNightModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.NIGHT_MODE_ENABLED] = enabled
        }
    }

    suspend fun setDesktopModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.DESKTOP_MODE_ENABLED] = enabled
        }
    }
}
