package com.reminder.local.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
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
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.reminder.local.AlarmActivity
import com.reminder.local.R
import com.reminder.local.notification.NotificationHelper
import com.reminder.local.notification.AlarmNotificationPolicy
import com.reminder.local.receiver.NotificationActionReceiver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AlarmAlertService : Service() {

    @Inject lateinit var notificationHelper: NotificationHelper

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentNotificationId: Int = FALLBACK_NOTIFICATION_ID
    private var currentReminderId: Long? = null
    private var currentContent: AlarmAlertContent? = null

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
                val content = AlarmAlertContentFormatter.format(title, note, kind)
                val activeAlarmId = currentReminderId?.let { currentNotificationId }
                if (
                    AlarmAlertConcurrencyPolicy.shouldRetainCurrent(
                        activeAlarmId,
                        alarmId
                    )
                ) {
                    retainCurrentAlert()
                } else if (AlarmAlertConcurrencyPolicy.shouldRestartPlayback(activeAlarmId)) {
                    // 同一提醒的提前触发尚未关闭时，到点触发仍要成为一次新的强提醒。
                    stopRingtoneAndVibration()
                }
                currentNotificationId = alarmId
                currentReminderId = reminderId
                currentContent = content
                val activityIntent = alarmActivityIntent(
                    reminderId = reminderId,
                    alarmId = alarmId,
                    title = title,
                    note = note,
                    alarmTime = alarmTime,
                    kind = kind
                )
                val activityPendingIntent = activityPendingIntent(alarmId, activityIntent)

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

                runCatching { wakeScreen() }
                    .onFailure { Log.e(TAG, "wakeScreen 失败，alarmId=$alarmId", it) }

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
                } else {
                    NotificationHelper.CHANNEL_FULLSCREEN_ALERT
                }

                runCatching {
                    postAlertNotification(
                        alarmId,
                        buildAlertNotification(
                            reminderId = reminderId,
                            alarmId = alarmId,
                            content = content,
                            activityPendingIntent = activityPendingIntent,
                            kind = kind,
                            channelId = alertChannelId
                        )
                    )
                }.onFailure {
                    Log.e(TAG, "发布用户强提醒通知失败，alarmId=$alarmId kind=$kind", it)
                }

                runCatching { launchAlarmActivity(activityPendingIntent) }
                    .onFailure { Log.e(TAG, "launchAlarmActivity 失败，alarmId=$alarmId", it) }

                Log.i(
                    TAG,
                    "强提醒投递完毕 alarmId=$alarmId kind=$kind foreground=$foregroundStarted " +
                        "soundRequested=$sound soundStarted=${playback.soundStarted} " +
                        "vibrateRequested=$vibrate vibrationStarted=${playback.vibrationStarted} " +
                        "fallback=${delivery.useFallbackChannel} channel=$alertChannelId"
                )
                if (!foregroundStarted) {
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy alarmId=$currentNotificationId")
        stopRingtoneAndVibration()
        super.onDestroy()
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
        activityPendingIntent: PendingIntent,
        kind: AlarmAlertKind,
        channelId: String
    ): Notification {
        val closeIntent = Intent(this, AlarmAlertService::class.java).apply {
            action = ACTION_STOP
            data = Uri.parse(AlarmIntentIdentity.action(reminderId, "close"))
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_TITLE, content.title)
            putExtra(EXTRA_NOTE, content.previewText)
            putExtra(EXTRA_ALARM_KIND, kind.name)
        }
        val closePendingIntent = PendingIntent.getService(
            this,
            alarmId xor 0x10000000,
            closeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markDoneIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_DONE
            data = Uri.parse(AlarmIntentIdentity.action(reminderId, "done"))
            putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        val markDonePendingIntent = PendingIntent.getBroadcast(
            this,
            alarmId,
            markDoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
            data = Uri.parse(AlarmIntentIdentity.action(reminderId, "snooze"))
            putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this,
            alarmId + 1,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val publicPreview = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(content.title)
            .setContentText(content.previewText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.previewText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(activityPendingIntent)
            .build()

        return NotificationCompat.Builder(this, channelId)
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
            .setDeleteIntent(closePendingIntent)
            .setContentIntent(activityPendingIntent)
            .setFullScreenIntent(activityPendingIntent, true)
            .setPublicVersion(publicPreview)
            .addAction(0, "关闭", closePendingIntent)
            .addAction(0, getString(R.string.action_snooze), snoozePendingIntent)
            .addAction(0, getString(R.string.action_mark_done), markDonePendingIntent)
            .build()
    }

    private fun alarmActivityIntent(
        reminderId: Long,
        alarmId: Int,
        title: String,
        note: String?,
        alarmTime: Long,
        kind: AlarmAlertKind
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

    private fun launchAlarmActivity(activityPendingIntent: PendingIntent) {
        activityPendingIntent.send(
            this,
            0,
            null,
            null,
            null,
            null,
            senderBackgroundActivityLaunchOptions()
        )
        Log.d(TAG, "已请求启动全屏提醒页；若系统拦截需检查 ActivityTaskManager 的 BAL 日志")
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

    private fun senderBackgroundActivityLaunchOptions(): Bundle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(
                    backgroundActivityStartMode()
                )
            }.toBundle()
        } else {
            null
        }

    @SuppressLint("WakelockTimeout")
    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        val existingWakeLock = wakeLock
        if (existingWakeLock?.isHeld == true) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "ReminderLocal:AlarmAlert"
        ).apply {
            setReferenceCounted(false)
            acquire(AlarmAlertLaunchPolicy.WAKE_SCREEN_TIMEOUT_MILLIS)
        }
    }

    private fun startAlert(sound: Boolean, vibrate: Boolean): AlarmPlaybackResult {
        val soundStarted = if (!sound) {
            true
        } else {
            runCatching { startRingtone() }
                .onFailure { Log.e(TAG, "手动响铃启动失败", it) }
                .getOrDefault(false)
        }
        val vibrationStarted = if (!vibrate) {
            true
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
        if (
            !AlarmAlertConcurrencyPolicy.actionTargetsCurrent(
                currentReminderId?.let { currentNotificationId },
                alarmId
            )
        ) {
            Log.w(TAG, "忽略旧提醒的关闭操作 actionAlarmId=$alarmId currentAlarmId=$currentNotificationId")
            return
        }
        stopRingtoneAndVibration()
        NotificationManagerCompat.from(this).cancel(alarmId)
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (
            reminderId >= 0 &&
            AlarmAlertInteractionPolicy.shouldKeepNotification(AlarmAlertAction.CLOSE)
        ) {
            notificationHelper.showRetainedAlertNotification(
                reminderId = reminderId,
                alarmId = alarmId,
                title = title,
                previewText = previewText
            )
        }
        clearCurrentAlert()
        stopSelf()
    }

    private fun retainCurrentAlert() {
        val reminderId = currentReminderId ?: return
        val content = currentContent ?: return
        val alarmId = currentNotificationId
        stopRingtoneAndVibration()
        NotificationManagerCompat.from(this).cancel(alarmId)
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationHelper.showRetainedAlertNotification(
            reminderId = reminderId,
            alarmId = alarmId,
            title = content.title,
            previewText = content.previewText
        )
        clearCurrentAlert()
    }

    private fun clearCurrentAlert() {
        currentReminderId = null
        currentContent = null
        currentNotificationId = FALLBACK_NOTIFICATION_ID
    }

    private fun backgroundActivityStartMode(): Int =
        if (Build.VERSION.SDK_INT >= 36) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        } else {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }

    @SuppressLint("MissingPermission")
    private fun postAlertNotification(notificationId: Int, notification: Notification) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "通知权限未授权，无法发布锁屏强提醒 notificationId=$notificationId")
            return
        }
        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }

    private fun stopRingtoneAndVibration() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        releaseWakeLock()
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        private const val TAG = "AlarmAlertService"

        const val ACTION_START = "com.reminder.local.action.ALARM_ALERT_START"
        const val ACTION_STOP = "com.reminder.local.action.ALARM_ALERT_STOP"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_NOTE = "extra_note"
        const val EXTRA_ALARM_TIME = "extra_alarm_time"
        const val EXTRA_SOUND = "extra_sound"
        const val EXTRA_VIBRATE = "extra_vibrate"
        const val EXTRA_ALARM_KIND = "extra_alarm_kind"

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
            kind: AlarmAlertKind
        ): Intent =
            Intent(context, AlarmAlertService::class.java).apply {
                action = ACTION_STOP
                val content = AlarmAlertContentFormatter.format(title, note, kind)
                putExtra(EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_TITLE, content.title)
                putExtra(EXTRA_NOTE, content.previewText)
                putExtra(EXTRA_ALARM_KIND, kind.name)
            }
    }
}
