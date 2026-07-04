package com.reminder.local.domain.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import com.reminder.local.MainActivity
import com.reminder.local.domain.model.Reminder
import com.reminder.local.receiver.AlarmReceiver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用 AlarmManager.setAlarmClock() 实现精确到点提醒。
 *
 * 选择 setAlarmClock() 而不是 setExactAndAllowWhileIdle() 的原因：
 * - 系统会把它当作"闹钟"对待，Doze/省电模式下也会准时唤醒设备触发，触发窗口不会被系统压缩；
 * - 状态栏会显示时钟图标，用户能直观看到"下一个提醒"，符合"提醒事项"App 的产品心智；
 * - 需要 SCHEDULE_EXACT_ALARM（或 USE_EXACT_ALARM）权限，权限检查见 canScheduleExactAlarms()。
 */
@Singleton
class AlarmSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AlarmScheduler {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun canScheduleExactAlarms(): Boolean = alarmManager.canScheduleExactAlarms()

    override fun scheduleExact(reminder: Reminder) {
        if (!canScheduleExactAlarms()) {
            throw IllegalStateException("精确闹钟权限未开启")
        }
        val triggerAt = reminder.effectiveTime
        val operation = buildOperationPendingIntent(reminder)
        val showIntent = buildShowPendingIntent(reminder)
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showIntent),
            operation
        )
    }

    override fun cancel(reminder: Reminder) {
        val operation = buildOperationPendingIntent(reminder)
        alarmManager.cancel(operation)
    }

    override fun scheduleSnooze(reminder: Reminder, delayMillis: Long) {
        if (!canScheduleExactAlarms()) {
            throw IllegalStateException("精确闹钟权限未开启")
        }
        val triggerAt = System.currentTimeMillis() + delayMillis
        val operation = buildOperationPendingIntent(reminder)
        val showIntent = buildShowPendingIntent(reminder)
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showIntent),
            operation
        )
    }

    private fun buildOperationPendingIntent(reminder: Reminder): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildShowPendingIntent(reminder: Reminder): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_REMINDER_ID, reminder.id)
        }
        return PendingIntent.getActivity(
            context,
            reminder.alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        /** 生成一个稳定的 Int alarmId，避免数据库自增 Long 主键直接转 Int 溢出冲突。 */
        fun generateAlarmId(): Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
    }
}
