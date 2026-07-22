package com.reminder.local.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.reminder.local.R
import com.reminder.local.ReminderEntryActivity
import com.reminder.local.domain.model.Reminder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知渠道说明：
 *
 * App 里“到点强提醒”和“提前强提醒”走的是同一条代码路径——
 * AlarmReceiver -> AlarmAlertService（前台服务）自己 new NotificationCompat.Builder(
 * CHANNEL_FULLSCREEN_ALERT) 来发通知、自己控制响铃/震动/全屏弹窗，
 * 详见 [com.reminder.local.service.AlarmAlertService]。
 *
 * 2026-07 复盘修复：本文件之前还留有 showReminderNotification() /
 * showFullScreenReminderNotification() / showAdvanceReminderNotification()
 * 三个方法，以及 CHANNEL_SOUND_VIBRATE / CHANNEL_SOUND_ONLY / CHANNEL_VIBRATE_ONLY /
 * CHANNEL_SILENT / CHANNEL_ADVANCE 五个渠道——经排查确认它们从未被任何调用方使用
 * （AlarmReceiver 里注入的 notificationHelper 字段也从未调用这几个方法），
 * 是早期方案留下的死代码。风险：CHANNEL_ADVANCE 在系统设置里显示名就是“提前提醒”，
 * 排查问题时极容易被误认成“控制提前提醒的渠道”而被用户误开关，
 * 实际上它从未影响过真正的强提醒。为避免继续误导，本轮已删除，只保留
 * 真正在用的 CHANNEL_FULLSCREEN_ALERT。
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_FULLSCREEN_ALERT = AlarmNotificationPolicy.STRONG_ALERT_CHANNEL_ID
        const val CHANNEL_ALARM_SERVICE = AlarmNotificationPolicy.FOREGROUND_SERVICE_CHANNEL_ID
        const val CHANNEL_NOISY_FALLBACK = AlarmNotificationPolicy.NOISY_FALLBACK_CHANNEL_ID
        private const val TAG = "NotificationHelper"
    }

    fun createNotificationChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val fullScreenAlert = NotificationChannel(
            CHANNEL_FULLSCREEN_ALERT,
            context.getString(R.string.notification_channel_fullscreen_alert),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            // 声音/震动由 AlarmAlertService 用 Ringtone/Vibrator 手动控制（需要循环播放，
            // NotificationChannel 自带的声音/震动不支持循环），所以渠道本身保持无声/无震动，
            // 避免和手动播放的铃声、震动叠加成“双重响铃”。
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        val alarmService = NotificationChannel(
            CHANNEL_ALARM_SERVICE,
            "提醒运行服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "仅在提醒正在响铃时维持后台运行"
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET
            setShowBadge(false)
        }

        val alarmAudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        fun fallbackChannel(id: String, name: String, sound: Boolean, vibrate: Boolean) =
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "强提醒服务被系统阻止时的备用通知"
                enableVibration(vibrate)
                if (vibrate) vibrationPattern = longArrayOf(0, 800, 800, 800)
                setSound(
                    if (sound) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) else null,
                    if (sound) alarmAudioAttributes else null
                )
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
        val fallbackChannels = listOf(
            fallbackChannel(
                AlarmNotificationPolicy.NOISY_FALLBACK_CHANNEL_ID,
                "强提醒备用（响铃和震动）",
                sound = true,
                vibrate = true
            ),
            fallbackChannel(
                AlarmNotificationPolicy.SOUND_FALLBACK_CHANNEL_ID,
                "强提醒备用（仅响铃）",
                sound = true,
                vibrate = false
            ),
            fallbackChannel(
                AlarmNotificationPolicy.VIBRATION_FALLBACK_CHANNEL_ID,
                "强提醒备用（仅震动）",
                sound = false,
                vibrate = true
            ),
            fallbackChannel(
                AlarmNotificationPolicy.SILENT_FALLBACK_CHANNEL_ID,
                "强提醒备用（静音）",
                sound = false,
                vibrate = false
            )
        )

        manager.createNotificationChannels(listOf(fullScreenAlert, alarmService) + fallbackChannels)
        (listOf(CHANNEL_FULLSCREEN_ALERT, CHANNEL_ALARM_SERVICE) + fallbackChannels.map { it.id })
            .forEach { id ->
                manager.getNotificationChannel(id)?.let { channel ->
                    Log.i(
                        TAG,
                        "channel=$id importance=${channel.importance} " +
                            "lockscreen=${channel.lockscreenVisibility} sound=${channel.sound}"
                    )
                }
            }
    }

    fun cancelNotification(reminder: Reminder) {
        NotificationManagerCompat.from(context).cancel(reminder.alarmId)
    }

    @SuppressLint("MissingPermission")
    fun showRetainedAlertNotification(
        reminderId: Long,
        alarmId: Int,
        title: String,
        previewText: String
    ) {
        val contentIntent = Intent(context, ReminderEntryActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("reminder://$reminderId/view")
            putExtra(ReminderEntryActivity.EXTRA_OPEN_REMINDER_ID, reminderId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            alarmId xor 0x08000000,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_FULLSCREEN_ALERT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(previewText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(previewText))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(false)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "通知权限未授权，无法保留提醒记录 alarmId=$alarmId")
            return
        }
        NotificationManagerCompat.from(context).notify(alarmId, notification)
    }
}
