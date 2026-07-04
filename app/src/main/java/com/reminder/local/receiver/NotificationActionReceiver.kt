package com.reminder.local.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.alarm.AlarmScheduler
import com.reminder.local.domain.usecase.CompleteReminderUseCase
import com.reminder.local.notification.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 通知栏 Action 按钮专用 Receiver：即使 App 进程已经被系统杀掉，
 * 点击"标为完成"/"稍后提醒"也能正常工作，不需要先把 App 拉起来。
 */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: ReminderRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var completeReminderUseCase: CompleteReminderUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId < 0) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = repository.getById(reminderId) ?: return@launch
                when (intent.action) {
                    ACTION_MARK_DONE -> {
                        completeReminderUseCase.markDone(reminder)
                        notificationHelper.cancelNotification(reminder)
                    }
                    ACTION_SNOOZE -> {
                        notificationHelper.cancelNotification(reminder)
                        runCatching {
                            alarmScheduler.scheduleSnooze(reminder, SNOOZE_DELAY_MILLIS)
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
        const val ACTION_MARK_DONE = "com.reminder.local.action.MARK_DONE"
        const val ACTION_SNOOZE = "com.reminder.local.action.SNOOZE"
        const val SNOOZE_DELAY_MILLIS = 10 * 60 * 1000L // 10 分钟
    }
}
