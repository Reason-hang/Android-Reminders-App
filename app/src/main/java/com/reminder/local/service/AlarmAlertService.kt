package com.reminder.local.service

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
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
import com.reminder.local.AlarmActivity
import com.reminder.local.R
import com.reminder.local.notification.NotificationHelper
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
                if (
                    AlarmAlertConcurrencyPolicy.shouldRetainCurrent(
                        currentReminderId?.let { currentNotificationId },
                        alarmId
                    )
                ) {
                    retainCurrentAlert()
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

                // 2026-07 复盘修复：这四步之前是顺序裸调用，任何一步抛异常都会让后面的步骤
                // （包括响铃、震动、拉起全屏页）整体静默跳过，且没有任何日志——用户只会看到
                // “通知栏有一条记录，但没有任何动静”，而开发者事后完全无法定位是哪一步失败。
                // 现在每一步单独 try/catch + Log，互不影响，出问题时 logcat 里能直接看到
                // 是 startForeground / wakeScreen / startAlert / launchAlarmActivity 里的哪一步、
                // 抛出的是什么异常。
                runCatching {
                    startForeground(
                        currentNotificationId,
                        buildNotification(
                            reminderId = reminderId,
                            alarmId = alarmId,
                            content = content,
                            activityPendingIntent = activityPendingIntent,
                            kind = kind
                        )
                    )
                }.onFailure {
                    Log.e(TAG, "startForeground 失败，alarmId=$alarmId kind=$kind", it)
                }

                runCatching { wakeScreen() }
                    .onFailure { Log.e(TAG, "wakeScreen 失败，alarmId=$alarmId", it) }

                runCatching { startAlert(sound, vibrate) }
                    .onFailure { Log.e(TAG, "startAlert(响铃/震动) 失败，alarmId=$alarmId", it) }

                runCatching { launchAlarmActivity(activityPendingIntent) }
                    .onFailure { Log.e(TAG, "launchAlarmActivity 失败，alarmId=$alarmId", it) }

                Log.d(TAG, "闹钟强提醒流程执行完毕 alarmId=$alarmId kind=$kind sound=$sound vibrate=$vibrate")
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

    private fun buildNotification(
        reminderId: Long,
        alarmId: Int,
        content: AlarmAlertContent,
        activityPendingIntent: PendingIntent,
        kind: AlarmAlertKind
    ): Notification {
        val closeIntent = Intent(this, AlarmAlertService::class.java).apply {
            action = ACTION_STOP
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
            putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this,
            alarmId + 1,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val publicPreview = NotificationCompat.Builder(this, NotificationHelper.CHANNEL_FULLSCREEN_ALERT)
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

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_FULLSCREEN_ALERT)
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
            .setOngoing(true)
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
        if (AlarmAlertLaunchPolicy.needsBackgroundActivityLaunchOptions(Build.VERSION.SDK_INT)) {
            ActivityOptions.makeBasic().apply {
                pendingIntentCreatorBackgroundActivityStartMode =
                    backgroundActivityStartMode()
            }.toBundle()
        } else {
            null
        }

    private fun senderBackgroundActivityLaunchOptions(): Bundle? =
        if (AlarmAlertLaunchPolicy.needsBackgroundActivityLaunchOptions(Build.VERSION.SDK_INT)) {
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

    private fun startAlert(sound: Boolean, vibrate: Boolean) {
        if (sound && ringtone == null) {
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
        if (vibrate && vibrator == null) {
            vibrator = (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0))
        }
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
        when (AlarmAlertLaunchPolicy.backgroundLaunchMode(Build.VERSION.SDK_INT)) {
            AlarmBackgroundLaunchMode.ALLOW_ALWAYS ->
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
            AlarmBackgroundLaunchMode.ALLOW_WHILE_ALARM_ACTIVE ->
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            AlarmBackgroundLaunchMode.LEGACY ->
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_SYSTEM_DEFINED
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
