package com.reminder.local.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.reminder.local.AlarmActivity
import com.reminder.local.R
import com.reminder.local.notification.NotificationHelper
import com.reminder.local.notification.AlarmNotificationPolicy
import com.reminder.local.receiver.NotificationActionReceiver
import com.reminder.local.util.PermissionUtils
import com.reminder.local.diagnostics.core.DiagnosticEventName
import com.reminder.local.diagnostics.core.DiagnosticLevel
import com.reminder.local.diagnostics.core.DiagnosticStage
import com.reminder.local.diagnostics.core.DiagnosticTraceId
import com.reminder.local.diagnostics.platform.DiagnosticLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AlarmAlertService : Service() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var diagnosticLogger: DiagnosticLogger

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var currentNotificationId: Int = FALLBACK_NOTIFICATION_ID
    private var currentReminderId: Long? = null
    private var currentContent: AlarmAlertContent? = null
    private var currentInstance: AlarmAlertInstanceKey? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoStopRunnable: Runnable? = null
    private var overlayController: AlarmOverlayController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_STOP -> {
                acknowledgeAlert(intent)
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
                val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, FALLBACK_NOTIFICATION_ID)
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "提醒事项" }
                val note = intent.getStringExtra(EXTRA_NOTE)
                val alarmTime = intent.getLongExtra(EXTRA_ALARM_TIME, -1L)
                val sound = intent.getBooleanExtra(EXTRA_SOUND, true)
                val vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true)
                val kind = AlarmAlertKind.fromWireValue(intent.getStringExtra(EXTRA_ALARM_KIND))
                val incomingInstance = AlarmAlertInstanceKey(alarmId, kind, alarmTime)
                val traceId = DiagnosticTraceId.alert(reminderId, alarmId, kind.name, alarmTime)
                val content = AlarmAlertContentFormatter.format(title, note, kind)
                diagnosticLogger.record(
                    DiagnosticStage.FOREGROUND_SERVICE,
                    DiagnosticEventName.ALERT_SESSION_STARTED,
                    traceId = traceId,
                    details = mapOf("kind" to kind.name, "soundRequested" to sound, "vibrateRequested" to vibrate),
                    captureSnapshot = true
                )
                val activeAlarmId = currentReminderId?.let { currentNotificationId }
                if (
                    AlarmAlertConcurrencyPolicy.shouldRetainCurrent(
                        activeAlarmId,
                        alarmId
                    )
                ) {
                    currentInstance?.let { previousInstance ->
                        diagnosticLogger.record(
                            DiagnosticStage.FOREGROUND_SERVICE,
                            DiagnosticEventName.ALERT_PREEMPTED,
                            traceId = DiagnosticTraceId.alert(
                                currentReminderId ?: -1L,
                                previousInstance.alarmId,
                                previousInstance.kind.name,
                                previousInstance.occurrenceTime
                            ),
                            outcome = "replaced_by_new_alert",
                            details = mapOf(
                                "nextAlarmId" to alarmId,
                                "nextKind" to kind.name,
                                "nextOccurrenceTime" to alarmTime
                            ),
                            captureSnapshot = true
                        )
                    }
                    runCatching { retainCurrentAlert(removeForeground = false) }
                        .onFailure { Log.e(TAG, "保留上一条提醒失败，继续投递新提醒", it) }
                } else if (AlarmAlertConcurrencyPolicy.shouldRestartPlayback(activeAlarmId)) {
                    // 同一提醒的提前触发尚未关闭时，到点触发仍要成为一次新的强提醒。
                    dismissOverlay("restarted_by_new_occurrence")
                    stopRingtoneAndVibration()
                }
                currentNotificationId = alarmId
                currentReminderId = reminderId
                currentContent = content
                currentInstance = incomingInstance
                val foregroundStarted = runCatching {
                    startForeground(
                        AlarmNotificationPolicy.FOREGROUND_SERVICE_NOTIFICATION_ID,
                        buildServiceNotification(content)
                    )
                    true
                }.getOrElse {
                    Log.e(TAG, "startForeground 失败，alarmId=$alarmId kind=$kind", it)
                    false
                }
                diagnosticLogger.record(
                    DiagnosticStage.FOREGROUND_SERVICE,
                    if (foregroundStarted) DiagnosticEventName.FOREGROUND_SERVICE_STARTED else DiagnosticEventName.FOREGROUND_SERVICE_FAILED,
                    traceId = traceId,
                    level = if (foregroundStarted) DiagnosticLevel.INFO else DiagnosticLevel.ERROR,
                    outcome = if (foregroundStarted) "ok" else "start_foreground_failed",
                    captureSnapshot = true
                )

                val fullScreenActivityIntent = alarmActivityIntent(
                    reminderId = reminderId,
                    alarmId = alarmId,
                    title = title,
                    note = note,
                    alarmTime = alarmTime,
                    kind = kind,
                    launchSource = AlarmActivity.LAUNCH_SOURCE_NOTIFICATION_FULL_SCREEN
                )
                val fullScreenPendingIntent = runCatching {
                    activityPendingIntent(alarmId, fullScreenActivityIntent)
                }.onFailure {
                    Log.e(TAG, "创建全屏 PendingIntent 失败，继续声音/震动/通知链路", it)
                }.getOrNull()
                val contentActivityIntent = alarmActivityIntent(
                    reminderId = reminderId,
                    alarmId = alarmId,
                    title = title,
                    note = note,
                    alarmTime = alarmTime,
                    kind = kind,
                    launchSource = AlarmActivity.LAUNCH_SOURCE_ALERT_NOTIFICATION
                )
                val contentPendingIntent = runCatching {
                    activityPendingIntent(alarmId xor 0x20000000, contentActivityIntent)
                }.onFailure {
                    Log.e(TAG, "创建通知点击 PendingIntent 失败，继续全屏通知链路", it)
                }.getOrNull()

                val powerManager = getSystemService(PowerManager::class.java)
                val keyguardManager = getSystemService(KeyguardManager::class.java)
                val visualRoute = AlarmVisualRoutePolicy.decide(
                    interactive = powerManager.isInteractive,
                    keyguardLocked = keyguardManager.isKeyguardLocked,
                    overlayAllowed = PermissionUtils.canDrawOverlays(this)
                )
                if (visualRoute != AlarmVisualRoute.SYSTEM_FULL_SCREEN) {
                    diagnosticLogger.record(
                        DiagnosticStage.FULL_SCREEN,
                        DiagnosticEventName.OVERLAY_REQUESTED,
                        traceId = traceId,
                        details = mapOf("route" to visualRoute.name.lowercase())
                    )
                }
                val overlayResult = when {
                    visualRoute == AlarmVisualRoute.SYSTEM_FULL_SCREEN -> null
                    !foregroundStarted -> AlarmOverlayShowResult.ADD_VIEW_FAILED
                    visualRoute == AlarmVisualRoute.SYSTEM_NOTIFICATION_FALLBACK ->
                        AlarmOverlayShowResult.PERMISSION_MISSING
                    else -> {
                        val controller = overlayController ?: AlarmOverlayController(this).also {
                            overlayController = it
                        }
                        controller.show(
                            title = title,
                            note = note,
                            kind = kind,
                            occurrenceTime = alarmTime,
                            onClose = {
                                acknowledgeAlert(
                                    stopIntent(
                                        context = this,
                                        reminderId = reminderId,
                                        alarmId = alarmId,
                                        title = title,
                                        note = note,
                                        kind = kind,
                                        occurrenceTime = alarmTime,
                                        source = STOP_SOURCE_OVERLAY_CLOSE
                                    )
                                )
                            },
                            onSnooze = {
                                sendOverlayAction(
                                    NotificationActionReceiver.ACTION_SNOOZE,
                                    reminderId,
                                    alarmId,
                                    kind,
                                    alarmTime,
                                    STOP_SOURCE_OVERLAY_SNOOZE
                                )
                            },
                            onDone = {
                                sendOverlayAction(
                                    NotificationActionReceiver.ACTION_MARK_DONE,
                                    reminderId,
                                    alarmId,
                                    kind,
                                    alarmTime,
                                    STOP_SOURCE_OVERLAY_DONE
                                )
                            }
                        )
                    }
                }
                val overlayShown = overlayResult == AlarmOverlayShowResult.SHOWN
                if (overlayResult != null) {
                    diagnosticLogger.record(
                        DiagnosticStage.FULL_SCREEN,
                        if (overlayShown) {
                            DiagnosticEventName.OVERLAY_SHOWN
                        } else {
                            DiagnosticEventName.OVERLAY_FAILED
                        },
                        traceId = traceId,
                        level = if (overlayShown) DiagnosticLevel.INFO else DiagnosticLevel.ERROR,
                        outcome = overlayResult.name.lowercase(),
                        details = mapOf("route" to visualRoute.name.lowercase()),
                        captureSnapshot = true
                    )
                }

                val playback = if (foregroundStarted) {
                    runCatching { startAlert(sound, vibrate) }
                        .getOrElse {
                            Log.e(TAG, "startAlert(响铃/震动) 失败，alarmId=$alarmId", it)
                            AlarmPlaybackResult(
                                soundStarted = false,
                                vibrationStarted = false
                            )
                        }
                } else {
                    AlarmPlaybackResult(soundStarted = false, vibrationStarted = false)
                }
                diagnosticLogger.record(
                    DiagnosticStage.PLAYBACK,
                    DiagnosticEventName.PLAYBACK_STARTED,
                    traceId = traceId,
                    outcome = when {
                        playback.soundStarted || playback.vibrationStarted -> "started"
                        !sound && !vibrate -> "not_requested"
                        else -> "not_started"
                    },
                    details = mapOf(
                        "soundRequested" to sound,
                        "vibrateRequested" to vibrate,
                        "soundStarted" to playback.soundStarted,
                        "vibrationStarted" to playback.vibrationStarted
                    )
                )
                val delivery = AlarmDeliveryPolicy.decide(
                    foregroundStarted = foregroundStarted,
                    soundRequested = sound,
                    vibrateRequested = vibrate,
                    playback = playback
                )
                val alertChannelId = if (delivery.useFallbackChannel) {
                    AlarmNotificationPolicy.fallbackChannelId(
                        sound = delivery.fallbackSound,
                        vibrate = delivery.fallbackVibration
                    )
                } else if (overlayShown) {
                    NotificationHelper.CHANNEL_UNLOCKED_OVERLAY_ALERT
                } else {
                    NotificationHelper.CHANNEL_FULLSCREEN_ALERT
                }
                val notificationFullScreenIntent = if (overlayShown) null else fullScreenPendingIntent

                diagnosticLogger.record(
                    DiagnosticStage.NOTIFICATION,
                    DiagnosticEventName.ALERT_NOTIFICATION_REQUESTED,
                    traceId = traceId,
                    details = mapOf("channel" to alertChannelId)
                )
                val notificationPosted = runCatching {
                    postAlertNotification(
                        alarmId,
                        buildAlertNotification(
                            reminderId = reminderId,
                            alarmId = alarmId,
                            content = content,
                            contentPendingIntent = contentPendingIntent,
                            fullScreenPendingIntent = notificationFullScreenIntent,
                            kind = kind,
                            occurrenceTime = alarmTime,
                            channelId = alertChannelId
                        )
                    )
                }.onFailure {
                    Log.e(TAG, "发布用户强提醒通知失败，alarmId=$alarmId kind=$kind", it)
                }.getOrDefault(false)
                diagnosticLogger.record(
                    DiagnosticStage.NOTIFICATION,
                    DiagnosticEventName.ALERT_NOTIFICATION_POSTED,
                    traceId = traceId,
                    level = if (notificationPosted) DiagnosticLevel.INFO else DiagnosticLevel.ERROR,
                    outcome = if (notificationPosted) "requested" else "failed",
                    details = mapOf("channel" to alertChannelId),
                    captureSnapshot = true
                )

                if (!overlayShown) {
                    diagnosticLogger.record(
                        DiagnosticStage.FULL_SCREEN,
                        if (notificationPosted && notificationFullScreenIntent != null) {
                            DiagnosticEventName.FULL_SCREEN_REQUESTED
                        } else {
                            DiagnosticEventName.FULL_SCREEN_REQUEST_FAILED
                        },
                        traceId = traceId,
                        level = if (notificationPosted && notificationFullScreenIntent != null) {
                            DiagnosticLevel.INFO
                        } else {
                            DiagnosticLevel.ERROR
                        },
                        outcome = if (notificationPosted && notificationFullScreenIntent != null) {
                            "notification_manager_delivery"
                        } else {
                            "notification_or_pending_intent_failed"
                        },
                        details = mapOf("route" to visualRoute.name.lowercase()),
                        captureSnapshot = true
                    )
                }

                Log.i(
                    TAG,
                    "强提醒投递完毕 alarmId=$alarmId kind=$kind foreground=$foregroundStarted " +
                        "soundRequested=$sound soundStarted=${playback.soundStarted} " +
                        "vibrateRequested=$vibrate vibrationStarted=${playback.vibrationStarted} " +
                        "visualRoute=$visualRoute overlayShown=$overlayShown " +
                        "fallback=${delivery.useFallbackChannel} channel=$alertChannelId"
                )
                if (!foregroundStarted) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                scheduleAutoStop(incomingInstance)
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy alarmId=$currentNotificationId")
        cancelAutoStop()
        dismissOverlay("service_destroyed")
        stopRingtoneAndVibration()
        super.onDestroy()
    }

    private fun scheduleAutoStop(instance: AlarmAlertInstanceKey) {
        cancelAutoStop()
        val runnable = Runnable { autoStopAlert(instance) }
        autoStopRunnable = runnable
        mainHandler.postDelayed(runnable, AlarmAlertLaunchPolicy.ALERT_AUTO_STOP_TIMEOUT_MILLIS)
        Log.d(TAG, "已安排强提醒自动结束 alarmId=${instance.alarmId} kind=${instance.kind}")
        diagnosticLogger.record(
            DiagnosticStage.TIMER,
            DiagnosticEventName.TIMER_SCHEDULED,
            traceId = traceFor(instance),
            details = mapOf("timeoutMillis" to AlarmAlertLaunchPolicy.ALERT_AUTO_STOP_TIMEOUT_MILLIS)
        )
    }

    private fun cancelAutoStop() {
        autoStopRunnable?.let(mainHandler::removeCallbacks)
        autoStopRunnable = null
    }

    private fun autoStopAlert(scheduledInstance: AlarmAlertInstanceKey) {
        if (
            !AlarmAlertConcurrencyPolicy.timeoutTargetsCurrent(
                currentInstance,
                scheduledInstance
            )
        ) {
            Log.d(
                TAG,
                "忽略旧提醒的自动结束 alarmId=${scheduledInstance.alarmId} " +
                    "kind=${scheduledInstance.kind}"
            )
            diagnosticLogger.record(
                DiagnosticStage.TIMER,
                DiagnosticEventName.TIMER_STALE_IGNORED,
                traceId = traceFor(scheduledInstance),
                level = DiagnosticLevel.WARN
            )
            return
        }

        val reminderId = currentReminderId
        val alarmId = currentNotificationId
        val content = currentContent
        dismissOverlay("timer_expired")
        stopRingtoneAndVibration()
        diagnosticLogger.record(
            DiagnosticStage.TIMER,
            DiagnosticEventName.TIMER_EXPIRED,
            traceId = traceFor(scheduledInstance),
            captureSnapshot = true
        )
        runCatching { NotificationManagerCompat.from(this).cancel(alarmId) }
            .onFailure { Log.e(TAG, "自动结束时取消通知失败 alarmId=$alarmId", it) }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            .onFailure { Log.e(TAG, "自动结束时停止前台状态失败 alarmId=$alarmId", it) }
        if (reminderId != null && content != null) {
            runCatching {
                notificationHelper.showRetainedAlertNotification(
                    reminderId = reminderId,
                    alarmId = alarmId,
                    title = content.title,
                    previewText = content.previewText
                )
            }.onFailure { Log.e(TAG, "自动结束时保留静音通知失败 alarmId=$alarmId", it) }
        }
        sendAutoDismissBroadcast(scheduledInstance)
        clearCurrentAlert()
        Log.i(
            TAG,
            "强提醒已自动结束 alarmId=${scheduledInstance.alarmId} kind=${scheduledInstance.kind}"
        )
        stopSelf()
    }

    private fun sendAutoDismissBroadcast(instance: AlarmAlertInstanceKey) {
        sendBroadcast(
            Intent(ACTION_AUTO_DISMISS).apply {
                setPackage(packageName)
                putExtra(EXTRA_ALARM_ID, instance.alarmId)
                putExtra(EXTRA_ALARM_KIND, instance.kind.name)
                putExtra(EXTRA_ALARM_TIME, instance.occurrenceTime)
            }
        )
    }

    private fun buildServiceNotification(content: AlarmAlertContent): Notification =
        NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ALARM_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("提醒正在进行")
            .setContentText(content.title)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun buildAlertNotification(
        reminderId: Long,
        alarmId: Int,
        content: AlarmAlertContent,
        contentPendingIntent: PendingIntent?,
        fullScreenPendingIntent: PendingIntent?,
        kind: AlarmAlertKind,
        occurrenceTime: Long,
        channelId: String
    ): Notification {
        val closeIntent = Intent(this, AlarmAlertService::class.java).apply {
            action = ACTION_STOP
            data = Uri.parse(
                AlarmIntentIdentity.action(reminderId, alarmId, kind, occurrenceTime, "close")
            )
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_TITLE, content.title)
            putExtra(EXTRA_NOTE, content.previewText)
            putExtra(EXTRA_ALARM_KIND, kind.name)
            putExtra(EXTRA_ALARM_TIME, occurrenceTime)
            putExtra(EXTRA_RETAIN_NOTIFICATION, true)
            putExtra(EXTRA_STOP_SOURCE, STOP_SOURCE_NOTIFICATION_CLOSE_ACTION)
        }
        val closePendingIntent = PendingIntent.getService(
            this,
            alarmId xor 0x10000000,
            closeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissedIntent = Intent(this, AlarmAlertService::class.java).apply {
            action = ACTION_STOP
            data = AlarmIntentIdentity.action(
                reminderId,
                alarmId,
                kind,
                occurrenceTime,
                "dismiss"
            ).toUri()
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_TITLE, content.title)
            putExtra(EXTRA_NOTE, content.previewText)
            putExtra(EXTRA_ALARM_KIND, kind.name)
            putExtra(EXTRA_ALARM_TIME, occurrenceTime)
            putExtra(EXTRA_RETAIN_NOTIFICATION, true)
            putExtra(EXTRA_STOP_SOURCE, STOP_SOURCE_NOTIFICATION_DISMISSED)
        }
        val dismissedPendingIntent = PendingIntent.getService(
            this,
            alarmId xor 0x11000000,
            dismissedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markDoneIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_DONE
            data = Uri.parse(
                AlarmIntentIdentity.action(reminderId, alarmId, kind, occurrenceTime, "done")
            )
            putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(NotificationActionReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(NotificationActionReceiver.EXTRA_ALARM_KIND, kind.name)
            putExtra(NotificationActionReceiver.EXTRA_OCCURRENCE_TIME, occurrenceTime)
        }
        val markDonePendingIntent = PendingIntent.getBroadcast(
            this,
            alarmId,
            markDoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
            data = Uri.parse(
                AlarmIntentIdentity.action(reminderId, alarmId, kind, occurrenceTime, "snooze")
            )
            putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(NotificationActionReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(NotificationActionReceiver.EXTRA_ALARM_KIND, kind.name)
            putExtra(NotificationActionReceiver.EXTRA_OCCURRENCE_TIME, occurrenceTime)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this,
            alarmId + 1,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val publicPreviewBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(content.title)
            .setContentText(content.previewText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.previewText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
        if (contentPendingIntent != null) {
            publicPreviewBuilder.setContentIntent(contentPendingIntent)
        }
        val publicPreview = publicPreviewBuilder.build()

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(content.title)
            .setContentText(content.previewText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.previewText))
            .setTicker("${content.title}：${content.previewText}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setOngoing(false)
            .setAutoCancel(false)
            .setDeleteIntent(dismissedPendingIntent)
            .setPublicVersion(publicPreview)
            .addAction(0, "关闭", closePendingIntent)
            .addAction(0, getString(R.string.action_snooze), snoozePendingIntent)
            .addAction(0, getString(R.string.action_mark_done), markDonePendingIntent)
        if (contentPendingIntent != null) {
            builder.setContentIntent(contentPendingIntent)
        }
        if (fullScreenPendingIntent != null) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }
        return builder.build()
    }

    private fun alarmActivityIntent(
        reminderId: Long,
        alarmId: Int,
        title: String,
        note: String?,
        alarmTime: Long,
        kind: AlarmAlertKind,
        launchSource: String
    ): Intent =
        Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse(AlarmIntentIdentity.alert(reminderId, kind))
            putExtra(AlarmActivity.EXTRA_REMINDER_ID, reminderId)
            putExtra(AlarmActivity.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmActivity.EXTRA_TITLE, title)
            putExtra(AlarmActivity.EXTRA_NOTE, note)
            putExtra(AlarmActivity.EXTRA_ALARM_TIME, alarmTime)
            putExtra(AlarmActivity.EXTRA_ALARM_KIND, kind.name)
            putExtra(AlarmActivity.EXTRA_LAUNCH_SOURCE, launchSource)
        }

    private fun activityPendingIntent(requestCode: Int, intent: Intent): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val options = creatorBackgroundActivityLaunchOptions()
        return if (options != null) {
            runCatching {
                PendingIntent.getActivity(this, requestCode, intent, flags, options)
            }.getOrElse {
                PendingIntent.getActivity(this, requestCode, intent, flags)
            }
        } else {
            PendingIntent.getActivity(this, requestCode, intent, flags)
        }
    }

    private fun creatorBackgroundActivityLaunchOptions(): Bundle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                pendingIntentCreatorBackgroundActivityStartMode =
                    backgroundActivityStartMode()
            }.toBundle()
        } else {
            null
        }

    private fun startAlert(sound: Boolean, vibrate: Boolean): AlarmPlaybackResult {
        val soundStarted = if (!sound) {
            false
        } else {
            runCatching { startRingtone() }
                .onFailure { Log.e(TAG, "手动响铃启动失败", it) }
                .getOrDefault(false)
        }
        val vibrationStarted = if (!vibrate) {
            false
        } else {
            runCatching { startVibration() }
                .onFailure { Log.e(TAG, "手动震动启动失败", it) }
                .getOrDefault(false)
        }
        return AlarmPlaybackResult(
            soundStarted = soundStarted,
            vibrationStarted = vibrationStarted
        )
    }

    private fun startRingtone(): Boolean {
        if (ringtone == null) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val alarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxAlarmVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val candidateUris = linkedSetOf(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ).filterNotNull()
            var selectedUri: String? = null
            ringtone = candidateUris.firstNotNullOfOrNull { uri ->
                runCatching {
                    RingtoneManager.getRingtone(this, uri)?.apply {
                        audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        isLooping = true
                        volume = 1f
                        play()
                    }?.takeIf { candidate ->
                        candidate.isPlaying.also { playing ->
                            if (playing) selectedUri = uri.toString() else candidate.stop()
                        }
                    }
                }.onFailure {
                    Log.e(TAG, "铃声播放失败，尝试下一个系统铃声 uri=$uri", it)
                }.getOrNull()
            }
            Log.d(
                TAG,
                "响铃启动 uri=$selectedUri alarmVolume=$alarmVolume/$maxAlarmVolume " +
                    "ringtoneCreated=${ringtone != null} isPlaying=${ringtone?.isPlaying == true}"
            )
            if (ringtone == null) {
                Log.e(TAG, "系统闹钟铃声和通知铃声均无法播放")
            }
            if (alarmVolume == 0) {
                Log.w(TAG, "系统闹钟音量为 0，App 已请求响铃但系统输出仍会静音")
            }
        }
        return ringtone?.isPlaying == true
    }

    private fun startVibration(): Boolean {
        if (vibrator == null) {
            val candidate =
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator
            if (candidate.hasVibrator()) {
                candidate.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0))
                vibrator = candidate
            }
        }
        return vibrator?.hasVibrator() == true
    }

    private fun acknowledgeAlert(intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, currentNotificationId)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "提醒事项" }
        val previewText = intent.getStringExtra(EXTRA_NOTE).orEmpty().ifBlank { "提醒时间到了" }
        val kind = AlarmAlertKind.fromWireValue(intent.getStringExtra(EXTRA_ALARM_KIND))
        val occurrenceTime = intent.getLongExtra(EXTRA_ALARM_TIME, -1L)
        val actionInstance = AlarmAlertInstanceKey(alarmId, kind, occurrenceTime)
        val stopSource = intent.getStringExtra(EXTRA_STOP_SOURCE).orEmpty().ifBlank {
            STOP_SOURCE_UNKNOWN
        }
        if (
            !AlarmAlertConcurrencyPolicy.actionTargetsCurrent(
                currentInstance,
                actionInstance
            )
        ) {
            Log.w(TAG, "忽略旧提醒的关闭操作 actionAlarmId=$alarmId currentAlarmId=$currentNotificationId")
            return
        }
        cancelAutoStop()
        dismissOverlay(stopSource)
        stopRingtoneAndVibration()
        runCatching { NotificationManagerCompat.from(this).cancel(alarmId) }
            .onFailure { Log.e(TAG, "取消当前通知失败 alarmId=$alarmId", it) }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            .onFailure { Log.e(TAG, "停止前台状态失败 alarmId=$alarmId", it) }
        if (
            intent.getBooleanExtra(EXTRA_RETAIN_NOTIFICATION, true) &&
            reminderId >= 0 &&
            AlarmAlertInteractionPolicy.shouldKeepNotification(AlarmAlertAction.CLOSE)
        ) {
            runCatching { notificationHelper.showRetainedAlertNotification(
                reminderId = reminderId,
                alarmId = alarmId,
                title = title,
                previewText = previewText
            ) }.onFailure { Log.e(TAG, "保留已关闭通知失败 alarmId=$alarmId", it) }
        }
        clearCurrentAlert()
        diagnosticLogger.record(
            DiagnosticStage.USER_ACTION,
            DiagnosticEventName.ALERT_CLOSED,
            traceId = DiagnosticTraceId.alert(reminderId, alarmId, kind.name, occurrenceTime),
            details = mapOf("source" to stopSource)
        )
        stopSelf()
    }

    private fun retainCurrentAlert(removeForeground: Boolean = true) {
        val reminderId = currentReminderId ?: return
        val content = currentContent ?: return
        val alarmId = currentNotificationId
        cancelAutoStop()
        dismissOverlay("alert_retained")
        stopRingtoneAndVibration()
        runCatching { NotificationManagerCompat.from(this).cancel(alarmId) }
        if (removeForeground) runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        runCatching { notificationHelper.showRetainedAlertNotification(
            reminderId = reminderId,
            alarmId = alarmId,
            title = content.title,
            previewText = content.previewText
        ) }.onFailure { Log.e(TAG, "切换提醒时保留上一条通知失败 alarmId=$alarmId", it) }
        clearCurrentAlert()
    }

    private fun sendOverlayAction(
        action: String,
        reminderId: Long,
        alarmId: Int,
        kind: AlarmAlertKind,
        occurrenceTime: Long,
        source: String
    ) {
        sendBroadcast(
            Intent(this, NotificationActionReceiver::class.java).apply {
                this.action = action
                data = Uri.parse(
                    AlarmIntentIdentity.action(
                        reminderId,
                        alarmId,
                        kind,
                        occurrenceTime,
                        source
                    )
                )
                putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, reminderId)
                putExtra(NotificationActionReceiver.EXTRA_ALARM_ID, alarmId)
                putExtra(NotificationActionReceiver.EXTRA_ALARM_KIND, kind.name)
                putExtra(NotificationActionReceiver.EXTRA_OCCURRENCE_TIME, occurrenceTime)
                putExtra(NotificationActionReceiver.EXTRA_ACTION_SOURCE, source)
            }
        )
    }

    private fun dismissOverlay(source: String) {
        val instance = currentInstance
        if (overlayController?.dismiss() == true && instance != null) {
            diagnosticLogger.record(
                DiagnosticStage.FULL_SCREEN,
                DiagnosticEventName.OVERLAY_DISMISSED,
                traceId = traceFor(instance),
                details = mapOf("source" to source)
            )
        }
    }

    private fun clearCurrentAlert() {
        currentReminderId = null
        currentContent = null
        currentInstance = null
        currentNotificationId = FALLBACK_NOTIFICATION_ID
    }

    private fun traceFor(instance: AlarmAlertInstanceKey): String = DiagnosticTraceId.alert(
        currentReminderId ?: -1L,
        instance.alarmId,
        instance.kind.name,
        instance.occurrenceTime
    )

    private fun backgroundActivityStartMode(): Int =
        if (Build.VERSION.SDK_INT >= 36) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        } else {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }

    @SuppressLint("MissingPermission")
    private fun postAlertNotification(notificationId: Int, notification: Notification): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "通知权限未授权，无法发布锁屏强提醒 notificationId=$notificationId")
            return false
        }
        NotificationManagerCompat.from(this).notify(notificationId, notification)
        return true
    }

    private fun stopRingtoneAndVibration() {
        val activeRingtone = ringtone
        ringtone = null
        runCatching { activeRingtone?.stop() }
            .onFailure { Log.e(TAG, "停止铃声失败", it) }
        val activeVibrator = vibrator
        vibrator = null
        runCatching { activeVibrator?.cancel() }
            .onFailure { Log.e(TAG, "停止震动失败", it) }
    }

    companion object {
        private const val TAG = "AlarmAlertService"

        const val ACTION_START = "com.reminder.local.action.ALARM_ALERT_START"
        const val ACTION_STOP = "com.reminder.local.action.ALARM_ALERT_STOP"
        const val ACTION_AUTO_DISMISS = "com.reminder.local.action.ALARM_ALERT_AUTO_DISMISS"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_NOTE = "extra_note"
        const val EXTRA_ALARM_TIME = "extra_alarm_time"
        const val EXTRA_SOUND = "extra_sound"
        const val EXTRA_VIBRATE = "extra_vibrate"
        const val EXTRA_ALARM_KIND = "extra_alarm_kind"
        const val EXTRA_RETAIN_NOTIFICATION = "extra_retain_notification"
        const val EXTRA_STOP_SOURCE = "extra_stop_source"

        const val STOP_SOURCE_UNKNOWN = "unknown"
        const val STOP_SOURCE_ALARM_PAGE_CLOSE = "alarm_page_close"
        const val STOP_SOURCE_ALARM_PAGE_SNOOZE = "alarm_page_snooze"
        const val STOP_SOURCE_ALARM_PAGE_DONE = "alarm_page_done"
        const val STOP_SOURCE_NOTIFICATION_CLOSE_ACTION = "notification_close_action"
        const val STOP_SOURCE_NOTIFICATION_DISMISSED = "notification_dismissed"
        const val STOP_SOURCE_NOTIFICATION_ACTION_SNOOZE = "notification_action_snooze"
        const val STOP_SOURCE_NOTIFICATION_ACTION_DONE = "notification_action_done"
        const val STOP_SOURCE_OVERLAY_CLOSE = "overlay_close"
        const val STOP_SOURCE_OVERLAY_SNOOZE = "overlay_snooze"
        const val STOP_SOURCE_OVERLAY_DONE = "overlay_done"

        private const val FALLBACK_NOTIFICATION_ID = 4001

        fun startIntent(
            context: Context,
            reminderId: Long,
            alarmId: Int,
            title: String,
            note: String?,
            alarmTime: Long,
            sound: Boolean,
            vibrate: Boolean,
            kind: AlarmAlertKind = AlarmAlertKind.DUE
        ): Intent =
            Intent(context, AlarmAlertService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_NOTE, note)
                putExtra(EXTRA_ALARM_TIME, alarmTime)
                putExtra(EXTRA_SOUND, sound)
                putExtra(EXTRA_VIBRATE, vibrate)
                putExtra(EXTRA_ALARM_KIND, kind.name)
            }

        fun stopIntent(
            context: Context,
            reminderId: Long,
            alarmId: Int,
            title: String,
            note: String?,
            kind: AlarmAlertKind,
            occurrenceTime: Long,
            retainNotification: Boolean = true,
            source: String = STOP_SOURCE_UNKNOWN
        ): Intent =
            Intent(context, AlarmAlertService::class.java).apply {
                action = ACTION_STOP
                val content = AlarmAlertContentFormatter.format(title, note, kind)
                putExtra(EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_TITLE, content.title)
                putExtra(EXTRA_NOTE, content.previewText)
                putExtra(EXTRA_ALARM_KIND, kind.name)
                putExtra(EXTRA_ALARM_TIME, occurrenceTime)
                putExtra(EXTRA_RETAIN_NOTIFICATION, retainNotification)
                putExtra(EXTRA_STOP_SOURCE, source)
            }
    }
}
