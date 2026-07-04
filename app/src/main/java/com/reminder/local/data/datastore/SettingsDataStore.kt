package com.reminder.local.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val defaultNotifyVibrate: Boolean = true,
    val defaultNotifySound: Boolean = true
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DEFAULT_VIBRATE = booleanPreferencesKey("default_notify_vibrate")
        val DEFAULT_SOUND = booleanPreferencesKey("default_notify_sound")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            defaultNotifyVibrate = prefs[Keys.DEFAULT_VIBRATE] ?: true,
            defaultNotifySound = prefs[Keys.DEFAULT_SOUND] ?: true
        )
    }

    suspend fun setDefaultVibrate(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEFAULT_VIBRATE] = enabled }
    }

    suspend fun setDefaultSound(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEFAULT_SOUND] = enabled }
    }
}
