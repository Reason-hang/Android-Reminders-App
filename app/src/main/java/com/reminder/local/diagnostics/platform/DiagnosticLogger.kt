package com.reminder.local.diagnostics.platform

import android.Manifest
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import com.reminder.local.diagnostics.core.DiagnosticEvent
import com.reminder.local.diagnostics.core.DiagnosticLevel
import com.reminder.local.diagnostics.core.DiagnosticRedactor
import com.reminder.local.diagnostics.core.DiagnosticStage
import com.reminder.local.util.PermissionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

interface DiagnosticLogger {
    fun record(
        stage: DiagnosticStage,
        name: String,
        traceId: String? = null,
        level: DiagnosticLevel = DiagnosticLevel.INFO,
        outcome: String = "ok",
        details: Map<String, Any?> = emptyMap(),
        captureSnapshot: Boolean = false
    )
}

@Singleton
class AppDiagnosticLogger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: DiagnosticStore,
    preferences: DiagnosticPreferences
) : DiagnosticLogger {
    @Volatile private var enhancedUntilMillis = 0L

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            preferences.enhancedUntil.collect { enhancedUntilMillis = it }
        }
    }

    override fun record(
        stage: DiagnosticStage,
        name: String,
        traceId: String?,
        level: DiagnosticLevel,
        outcome: String,
        details: Map<String, Any?>,
        captureSnapshot: Boolean
    ) {
        val snapshot = if (captureSnapshot && (isEnhanced() || name in BASELINE_SNAPSHOT_EVENTS)) {
            snapshot()
        } else emptyMap()
        store.append(
            DiagnosticEvent(
                id = UUID.randomUUID().toString(),
                recordedAtMillis = System.currentTimeMillis(),
                level = level,
                stage = stage,
                name = name,
                outcome = outcome,
                traceId = traceId,
                details = DiagnosticRedactor.safeDetails(details + snapshot)
            )
        )
    }

    private fun isEnhanced(): Boolean = enhancedUntilMillis > System.currentTimeMillis()

    private companion object {
        val BASELINE_SNAPSHOT_EVENTS = setOf(
            "receiver_entered",
            "foreground_service_failed",
            "full_screen_request_failed",
            "timer_expired"
        )
    }

    private fun snapshot(): Map<String, Any?> = runCatching {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        val audioManager = context.getSystemService(AudioManager::class.java)
        val activityManager = context.getSystemService(ActivityManager::class.java)
        mapOf(
            "sdk" to Build.VERSION.SDK_INT,
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "exactAlarmAllowed" to PermissionUtils.canScheduleExactAlarms(context),
            "fullScreenIntentAllowed" to PermissionUtils.canUseFullScreenIntent(context),
            "notificationsEnabled" to NotificationManagerCompat.from(context).areNotificationsEnabled(),
            "notificationPermission" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true,
            "interactive" to powerManager.isInteractive,
            "keyguardLocked" to keyguardManager.isKeyguardLocked,
            "deviceLocked" to keyguardManager.isDeviceLocked,
            "powerSave" to powerManager.isPowerSaveMode,
            "backgroundRestricted" to activityManager.isBackgroundRestricted,
            "alarmVolume" to audioManager.getStreamVolume(AudioManager.STREAM_ALARM),
            "alarmMaxVolume" to audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            "ringerMode" to audioManager.ringerMode,
            "fullScreenApiAllowed" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                notificationManager.canUseFullScreenIntent()
            } else true
        )
    }.getOrDefault(mapOf("snapshotError" to "unavailable"))
}
