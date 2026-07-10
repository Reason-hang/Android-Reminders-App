package com.reminder.local.domain.alarm

import android.app.AlarmManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import dagger.hilt.android.qualifiers.ApplicationContext
import com.reminder.local.AlarmActivity
import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.usecase.AdvanceReminderCalculator
import com.reminder.local.receiver.AlarmReceiver
import com.reminder.local.service.AlarmAlertKind
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
        scheduleOne(
            reminder = reminder,
            triggerAt = reminder.effectiveTime,
            kind = AlarmReceiver.KIND_DUE,
            requestCode = reminder.alarmId
        )

        val advanceAt = AdvanceReminderCalculator.computeAdvanceTrigger(
            reminder.effectiveTime,
            reminder.advanceReminderType,
            reminder.customAdvanceValue,
            reminder.customAdvanceUnit
        )
        if (advanceAt != null && advanceAt > System.currentTimeMillis()) {
            scheduleOne(
                reminder = reminder,
                triggerAt = advanceAt,
                kind = AlarmReceiver.KIND_ADVANCE,
                requestCode = advanceAlarmRequestCode(reminder.alarmId)
            )
        }
    }

    private fun scheduleOne(reminder: Reminder, triggerAt: Long, kind: String, requestCode: Int) {
        val operation = buildOperationPendingIntent(reminder, kind, requestCode)
        val showIntent = buildShowPendingIntent(reminder)
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showIntent),
            operation
        )
    }

    override fun cancel(reminder: Reminder) {
        alarmManager.cancel(buildOperationPendingIntent(reminder, AlarmReceiver.KIND_DUE, reminder.alarmId))
        alarmManager.cancel(
            buildOperationPendingIntent(
                reminder,
                AlarmReceiver.KIND_ADVANCE,
                advanceAlarmRequestCode(reminder.alarmId)
            )
        )
    }

    override fun scheduleSnooze(reminder: Reminder, delayMillis: Long) {
        if (!canScheduleExactAlarms()) {
            throw IllegalStateException("精确闹钟权限未开启")
        }
        val triggerAt = System.currentTimeMillis() + delayMillis
        val operation = buildOperationPendingIntent(reminder, AlarmReceiver.KIND_DUE, snoozeAlarmRequestCode(reminder.alarmId))
        val showIntent = buildShowPendingIntent(reminder)
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showIntent),
            operation
        )
    }

    private fun buildOperationPendingIntent(reminder: Reminder, kind: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_KIND, kind)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildShowPendingIntent(reminder: Reminder): PendingIntent {
        val intent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmActivity.EXTRA_REMINDER_ID, reminder.id)
            putExtra(AlarmActivity.EXTRA_ALARM_TIME, reminder.effectiveTime)
            putExtra(AlarmActivity.EXTRA_ALARM_KIND, AlarmAlertKind.DUE.name)
        }
        val options = creatorBackgroundActivityLaunchOptions()
        return if (options != null) {
            runCatching {
                PendingIntent.getActivity(
                    context,
                    reminder.alarmId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    options
                )
            }.getOrElse {
                buildActivityPendingIntentWithoutOptions(reminder.alarmId, intent)
            }
        } else {
            buildActivityPendingIntentWithoutOptions(reminder.alarmId, intent)
        }
    }

    private fun buildActivityPendingIntentWithoutOptions(requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun creatorBackgroundActivityLaunchOptions(): Bundle? =
        if (Build.VERSION.SDK_INT >= 34) {
            runCatching {
                ActivityOptions.makeBasic().apply {
                    pendingIntentCreatorBackgroundActivityStartMode =
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }.toBundle()
            }.getOrNull()
        } else {
            null
        }

    companion object {
        /** 生成一个稳定的 Int alarmId，避免数据库自增 Long 主键直接转 Int 溢出冲突。 */
        fun generateAlarmId(): Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        fun advanceAlarmRequestCode(alarmId: Int): Int = alarmId xor 0x40000000

        fun snoozeAlarmRequestCode(alarmId: Int): Int = alarmId xor 0x20000000
    }
}
