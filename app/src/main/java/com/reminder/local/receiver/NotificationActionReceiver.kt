package com.reminder.local.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.alarm.AlarmScheduler
import com.reminder.local.domain.model.RepeatActionScope
import com.reminder.local.domain.usecase.CompleteReminderUseCase
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
    @Inject lateinit var completeReminderUseCase: CompleteReminderUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, Int.MIN_VALUE)
        val occurrenceTime = intent.getLongExtra(EXTRA_OCCURRENCE_TIME, -1L)
        val kind = com.reminder.local.service.AlarmAlertKind.fromWireValue(
            intent.getStringExtra(EXTRA_ALARM_KIND)
        )
        if (reminderId < 0) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = repository.getById(reminderId) ?: return@launch
                when (intent.action) {
                    ACTION_MARK_DONE -> {
                        val success = completeReminderUseCase.markDone(
                            reminder,
                            RepeatActionScope.ONCE,
                            occurrenceTime.takeIf { it > 0L }
                        )
                        if (success) {
                            stopTargetAlert(context, reminder, alarmId, kind, occurrenceTime)
                        } else {
                            Log.e(TAG, "通知栏完成操作失败 reminderId=${reminder.id}")
                        }
                    }
                    ACTION_SNOOZE -> {
                        val scheduled = runCatching {
                            alarmScheduler.scheduleSnooze(reminder, SNOOZE_DELAY_MILLIS)
                        }.onFailure {
                            Log.e(TAG, "稍后提醒调度失败 reminderId=${reminder.id}", it)
                        }.isSuccess
                        if (scheduled) {
                            stopTargetAlert(context, reminder, alarmId, kind, occurrenceTime)
                        }
                    }
                }
            } catch (error: Throwable) {
                Log.e(TAG, "处理通知操作失败 reminderId=$reminderId action=${intent.action}", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun stopTargetAlert(
        context: Context,
        reminder: com.reminder.local.domain.model.Reminder,
        alarmId: Int,
        kind: com.reminder.local.service.AlarmAlertKind,
        occurrenceTime: Long
    ) {
        if (alarmId == Int.MIN_VALUE || occurrenceTime <= 0L) return
        runCatching {
            context.startService(
                AlarmAlertService.stopIntent(
                    context = context,
                    reminderId = reminder.id,
                    alarmId = alarmId,
                    title = reminder.title,
                    note = reminder.note,
                    kind = kind,
                    occurrenceTime = occurrenceTime,
                    retainNotification = false
                )
            )
        }.onFailure { Log.e(TAG, "停止目标强提醒失败 reminderId=${reminder.id}", it) }
    }

    companion object {
        private const val TAG = "NotificationActionReceiver"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ALARM_KIND = "extra_alarm_kind"
        const val EXTRA_OCCURRENCE_TIME = "extra_occurrence_time"
        const val ACTION_MARK_DONE = "com.reminder.local.action.MARK_DONE"
        const val ACTION_SNOOZE = "com.reminder.local.action.SNOOZE"
        const val SNOOZE_DELAY_MILLIS = 10 * 60 * 1000L // 10 分钟
    }
}
