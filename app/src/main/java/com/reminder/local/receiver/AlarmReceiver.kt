package com.reminder.local.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.alarm.AlarmScheduler
import com.reminder.local.domain.model.ReminderStatus
import com.reminder.local.domain.model.RepeatType
import com.reminder.local.domain.usecase.RepeatCalculator
import com.reminder.local.notification.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AlarmManager 到点后广播到这里。逻辑：
 * 1. 读出提醒，发通知。
 * 2. 如果是重复提醒：算下一次触发时间，写回数据库并重新注册闹钟；
 *    如果已经超过 repeatEndDate，则标记为 DONE，不再注册。
 * 3. 如果是一次性提醒：什么都不用改，状态仍然是 PENDING，等用户在通知栏或 App 里手动"标为完成"。
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId < 0) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = repository.getById(reminderId)
                if (reminder != null && reminder.status == ReminderStatus.PENDING) {
                    notificationHelper.showReminderNotification(reminder)

                    if (reminder.repeatType != RepeatType.NONE) {
                        val next = RepeatCalculator.computeNext(
                            reminder.triggerTime,
                            reminder.effectiveTime,
                            reminder.repeatType
                        )
                        val exceededEnd = reminder.repeatEndDate != null &&
                            next != null && next > reminder.repeatEndDate

                        if (next == null || exceededEnd) {
                            repository.update(reminder.copy(status = ReminderStatus.DONE))
                        } else {
                            val updated = reminder.copy(nextTriggerTime = next)
                            repository.update(updated)
                            alarmScheduler.scheduleExact(updated)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
    }
}
