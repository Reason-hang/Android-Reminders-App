package com.reminder.local.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.reminder.local.domain.model.ReminderListSortMode
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val defaultNotifyVibrate: Boolean = true,
    val defaultNotifySound: Boolean = true,
    val reminderListSortMode: ReminderListSortMode = ReminderListSortMode.TIME
)

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DEFAULT_VIBRATE = booleanPreferencesKey("default_notify_vibrate")
        val DEFAULT_SOUND = booleanPreferencesKey("default_notify_sound")
        val REMINDER_LIST_SORT_MODE = stringPreferencesKey("reminder_list_sort_mode")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            defaultNotifyVibrate = prefs[Keys.DEFAULT_VIBRATE] ?: true,
            defaultNotifySound = prefs[Keys.DEFAULT_SOUND] ?: true,
            reminderListSortMode = prefs[Keys.REMINDER_LIST_SORT_MODE]
                ?.let { value -> runCatching { ReminderListSortMode.valueOf(value) }.getOrNull() }
                ?: ReminderListSortMode.TIME
        )
    }

    suspend fun setDefaultVibrate(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEFAULT_VIBRATE] = enabled }
    }

    suspend fun setDefaultSound(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEFAULT_SOUND] = enabled }
    }

    suspend fun setReminderListSortMode(mode: ReminderListSortMode) {
        context.dataStore.edit { it[Keys.REMINDER_LIST_SORT_MODE] = mode.name }
    }
}
