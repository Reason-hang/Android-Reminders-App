package com.reminder.local.diagnostics.platform

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.diagnosticDataStore by preferencesDataStore(name = "diagnostic_settings")

@Singleton
class DiagnosticPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val enhancedUntil: Flow<Long> = context.diagnosticDataStore.data.map { it[ENHANCED_UNTIL] ?: 0L }

    suspend fun enableFor24Hours(now: Long = System.currentTimeMillis()) {
        context.diagnosticDataStore.edit { it[ENHANCED_UNTIL] = now + ENHANCED_DURATION_MILLIS }
    }

    suspend fun disable() { context.diagnosticDataStore.edit { it.remove(ENHANCED_UNTIL) } }

    companion object {
        const val ENHANCED_DURATION_MILLIS = 24L * 60L * 60L * 1000L
        private val ENHANCED_UNTIL = longPreferencesKey("enhanced_until")
    }
}
