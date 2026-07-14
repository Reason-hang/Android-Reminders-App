package com.reminder.local.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.alarm.AlarmScheduler
import com.reminder.local.domain.model.RepeatActionScope
import com.reminder.local.domain.usecase.CompleteReminderUseCase
import com.reminder.local.notification.NotificationHelper
import com.reminder.local.service.AlarmAlertService
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
                        context.stopService(Intent(context, AlarmAlertService::class.java))
                        // 2026-07 第二轮复查修复：和 AlarmActivity 里同一个问题——不传 scope
                        // 会默认 RepeatActionScope.ALL，导致通知栏"标为完成"会把重复提醒
                        // 整个停掉，而不是仅完成这一次。改成 ONCE，和全屏页保持一致；
                        // 需要彻底停止重复提醒，请到列表页操作（会弹窗确认）。
                        completeReminderUseCase.markDone(reminder, RepeatActionScope.ONCE)
                        notificationHelper.cancelNotification(reminder)
                    }
                    ACTION_SNOOZE -> {
                        context.stopService(Intent(context, AlarmAlertService::class.java))
                        notificationHelper.cancelNotification(reminder)
                        runCatching {
                            alarmScheduler.scheduleSnooze(reminder, SNOOZE_DELAY_MILLIS)
                        }.onFailure {
                            Log.e(TAG, "稍后提醒调度失败 reminderId=${reminder.id}", it)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NotificationActionReceiver"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val ACTION_MARK_DONE = "com.reminder.local.action.MARK_DONE"
        const val ACTION_SNOOZE = "com.reminder.local.action.SNOOZE"
        const val SNOOZE_DELAY_MILLIS = 10 * 60 * 1000L // 10 分钟
    }
}
