package com.reminder.local.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.reminder.local.R
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
        const val CHANNEL_FULLSCREEN_ALERT = "reminder_fullscreen_alert_v2"
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

        manager.createNotificationChannels(listOf(fullScreenAlert))
    }

    fun cancelNotification(reminder: Reminder) {
        NotificationManagerCompat.from(context).cancel(reminder.alarmId)
        NotificationManagerCompat.from(context).cancel(reminder.alarmId + 2)
    }
}
