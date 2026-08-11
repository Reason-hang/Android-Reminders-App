package com.reminder.local

import android.app.Application
import com.reminder.local.notification.NotificationHelper
import com.reminder.local.diagnostics.core.DiagnosticEventName
import com.reminder.local.diagnostics.core.DiagnosticStage
import com.reminder.local.diagnostics.platform.DiagnosticLogger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var diagnosticLogger: DiagnosticLogger

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createNotificationChannels()
        diagnosticLogger.record(DiagnosticStage.SYSTEM, "app_started", captureSnapshot = true)

    }
}
