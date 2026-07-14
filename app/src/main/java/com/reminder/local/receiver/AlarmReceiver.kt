package com.reminder.local.receiver

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.reminder.local.AlarmActivity
import com.reminder.local.R
import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.alarm.AlarmScheduler
import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.ReminderStatus
import com.reminder.local.domain.model.RepeatType
import com.reminder.local.domain.usecase.RepeatCalculator
import com.reminder.local.notification.NotificationHelper
import com.reminder.local.service.AlarmAlertKind
import com.reminder.local.service.AlarmAlertService
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

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val alarmKind = intent.getStringExtra(EXTRA_ALARM_KIND) ?: KIND_DUE
        Log.d(TAG, "onReceive reminderId=$reminderId kind=$alarmKind")
        if (reminderId < 0) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = repository.getById(reminderId)
                Log.d(
                    TAG,
                    "reminder=${reminder?.id} status=${reminder?.status} " +
                        "kind=$alarmKind (为空说明数据库里已经查不到这条提醒，不会有任何提醒动作)"
                )
                if (reminder != null && reminder.status == ReminderStatus.PENDING) {
                    if (AlarmTriggerPolicy.shouldStartStrongAlert(alarmKind)) {
                        val started = runCatching {
                            ContextCompat.startForegroundService(
                                context,
                                AlarmAlertService.startIntent(
                                    context = context,
                                    reminderId = reminder.id,
                                    alarmId = reminder.alarmId,
                                    title = reminder.title,
                                    note = reminder.note,
                                    alarmTime = reminder.effectiveTime,
                                    sound = reminder.notifySound,
                                    vibrate = reminder.notifyVibrate,
                                    kind = if (alarmKind == KIND_ADVANCE) {
                                        AlarmAlertKind.ADVANCE
                                    } else {
                                        AlarmAlertKind.DUE
                                    }
                                )
                            )
                        }
                        started.onFailure { error ->
                            // 2026-07 复盘修复：startForegroundService 在少数机型/系统状态下可能被拒绝
                            // （例如 ForegroundServiceStartNotAllowedException）。之前这里没有 try/catch，
                            // 一旦被拒绝，异常会被 goAsync 的协程吞掉，用户什么提醒都收不到，
                            // 且没有任何日志能定位。现在捕获异常、记录日志，并降级发一条兜底通知，
                            // 保证用户至少能在通知栏/锁屏看到内容、点开能进入提醒详情，
                            // 而不是彻底没有任何反应。
                            Log.e(TAG, "startForegroundService 失败，降级为普通通知 reminderId=${reminder.id}", error)
                            postFallbackNotification(context, reminder, alarmKind)
                        }
                    }

                    if (
                        AlarmTriggerPolicy.shouldProgressRepeatingReminder(alarmKind) &&
                        reminder.repeatType != RepeatType.NONE
                    ) {
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

    /**
     * AlarmAlertService 前台服务无法启动时的兜底方案：直接发一条高优先级通知，
     * 保留全屏 Intent（锁屏时仍有机会自动弹出）和公开可见性（锁屏能看到标题/备注），
     * 但没有循环响铃/持续震动——这是"至少让用户看到提醒"和"完全没反应"之间的折中。
     * 根因（为什么前台服务启动失败）仍需要看 logcat 里上一行的异常堆栈来定位。
     */
    @SuppressLint("MissingPermission")
    private fun postFallbackNotification(context: Context, reminder: Reminder, alarmKind: String) {
        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmActivity.EXTRA_REMINDER_ID, reminder.id)
            putExtra(AlarmActivity.EXTRA_ALARM_TIME, reminder.effectiveTime)
            putExtra(
                AlarmActivity.EXTRA_ALARM_KIND,
                if (alarmKind == KIND_ADVANCE) AlarmAlertKind.ADVANCE.name else AlarmAlertKind.DUE.name
            )
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            reminder.alarmId,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val titlePrefix = if (alarmKind == KIND_ADVANCE) "提前提醒：" else ""
        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_FULLSCREEN_ALERT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$titlePrefix${reminder.title}")
            .setContentText(reminder.note?.ifBlank { null } ?: "点击查看详情")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(contentPendingIntent, true)
            .build()
        NotificationManagerCompat.from(context).notify(reminder.alarmId, notification)
    }

    companion object {
        private const val TAG = "AlarmReceiver"

        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_ALARM_KIND = "extra_alarm_kind"
        const val KIND_DUE = "due"
        const val KIND_ADVANCE = "advance"
    }
}
