package com.reminder.local.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.reminder.local.AlarmActivity
import com.reminder.local.MainActivity
import com.reminder.local.R
import com.reminder.local.data.repository.CategoryRepository
import com.reminder.local.domain.model.Reminder
import com.reminder.local.receiver.NotificationActionReceiver
import com.reminder.local.service.AlarmAlertKind
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知渠道说明：Android 8+ 一旦创建了渠道，声音/震动就由渠道设置决定，
 * NotificationCompat.Builder.setSound()/setVibrate() 会被系统忽略。
 * 为了保留"每条提醒可单独选择响铃/震动"的能力，这里按四种组合各建一个渠道。
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val categoryRepository: CategoryRepository
) {

    companion object {
        const val CHANNEL_SOUND_VIBRATE = "reminder_sound_vibrate"
        const val CHANNEL_SOUND_ONLY = "reminder_sound_only"
        const val CHANNEL_VIBRATE_ONLY = "reminder_vibrate_only"
        const val CHANNEL_SILENT = "reminder_silent"
        const val CHANNEL_FULLSCREEN_ALERT = "reminder_fullscreen_alert_v2"
        const val CHANNEL_ADVANCE = "reminder_advance"
    }

    fun createNotificationChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val soundVibrate = NotificationChannel(
            CHANNEL_SOUND_VIBRATE,
            context.getString(R.string.notification_channel_sound_vibrate),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { enableVibration(true) }

        val soundOnly = NotificationChannel(
            CHANNEL_SOUND_ONLY,
            context.getString(R.string.notification_channel_sound_only),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { enableVibration(false) }

        val vibrateOnly = NotificationChannel(
            CHANNEL_VIBRATE_ONLY,
            context.getString(R.string.notification_channel_vibrate_only),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            setSound(null, null)
        }

        val silent = NotificationChannel(
            CHANNEL_SILENT,
            context.getString(R.string.notification_channel_silent),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            enableVibration(false)
            setSound(null, null)
        }

        val fullScreenAlert = NotificationChannel(
            CHANNEL_FULLSCREEN_ALERT,
            context.getString(R.string.notification_channel_fullscreen_alert),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        val advance = NotificationChannel(
            CHANNEL_ADVANCE,
            context.getString(R.string.notification_channel_advance),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            enableVibration(false)
            setSound(null, null)
        }

        manager.createNotificationChannels(
            listOf(soundVibrate, soundOnly, vibrateOnly, silent, fullScreenAlert, advance)
        )
    }

    private fun channelFor(reminder: Reminder): String = when {
        reminder.notifySound && reminder.notifyVibrate -> CHANNEL_SOUND_VIBRATE
        reminder.notifySound -> CHANNEL_SOUND_ONLY
        reminder.notifyVibrate -> CHANNEL_VIBRATE_ONLY
        else -> CHANNEL_SILENT
    }

    @SuppressLint("MissingPermission")
    suspend fun showReminderNotification(reminder: Reminder) {
        showFullScreenReminderNotification(reminder)
    }

    @SuppressLint("MissingPermission")
    suspend fun showFullScreenReminderNotification(reminder: Reminder) {
        val notificationId = reminder.alarmId

        val contentText = when {
            !reminder.note.isNullOrBlank() -> reminder.note
            reminder.categoryId != null -> categoryRepository.getById(reminder.categoryId)?.name
            else -> null
        } ?: "点击查看详情"

        val title = if (reminder.isRepeating) "🔁 ${reminder.title}" else reminder.title

        val contentIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmActivity.EXTRA_REMINDER_ID, reminder.id)
            putExtra(AlarmActivity.EXTRA_ALARM_TIME, reminder.effectiveTime)
            putExtra(AlarmActivity.EXTRA_ALARM_KIND, AlarmAlertKind.DUE.name)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            reminder.alarmId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markDoneIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_DONE
            data = Uri.parse("reminder://${reminder.id}/done")
            putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, reminder.id)
        }
        val markDonePendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.alarmId,
            markDoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
            data = Uri.parse("reminder://${reminder.id}/snooze")
            putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, reminder.id)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.alarmId + 1,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_FULLSCREEN_ALERT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(false)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(contentPendingIntent, true)
            .addAction(0, context.getString(R.string.action_mark_done), markDonePendingIntent)
            .addAction(0, context.getString(R.string.action_snooze), snoozePendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    @SuppressLint("MissingPermission")
    suspend fun showAdvanceReminderNotification(reminder: Reminder) {
        val contentText = when {
            !reminder.note.isNullOrBlank() -> reminder.note
            reminder.categoryId != null -> categoryRepository.getById(reminder.categoryId)?.name
            else -> null
        } ?: "点击查看详情"

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_REMINDER_ID, reminder.id)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            reminder.alarmId + 2,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ADVANCE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("提前提醒：${reminder.title}")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(reminder.alarmId + 2, notification)
    }

    fun cancelNotification(reminder: Reminder) {
        NotificationManagerCompat.from(context).cancel(reminder.alarmId)
        NotificationManagerCompat.from(context).cancel(reminder.alarmId + 2)
    }
}
