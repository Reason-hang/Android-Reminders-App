package com.reminder.local.receiver

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.reminder.local.AlarmActivity
import com.reminder.local.R
import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.alarm.AlarmScheduler
import com.reminder.local.domain.alarm.AlarmSchedulerImpl
import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.ReminderStatus
import com.reminder.local.domain.model.RepeatType
import com.reminder.local.domain.usecase.RepeatCalculator
import com.reminder.local.notification.NotificationHelper
import com.reminder.local.notification.AlarmNotificationPolicy
import com.reminder.local.service.AlarmAlertKind
import com.reminder.local.service.AlarmAlertService
import com.reminder.local.service.AlarmIntentIdentity
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
            } catch (error: Throwable) {
                Log.e(TAG, "处理系统闹钟失败 reminderId=$reminderId kind=$alarmKind", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * AlarmAlertService 前台服务无法启动时的兜底方案：直接发一条高优先级通知，
     * 保留全屏 Intent（锁屏时仍有机会自动弹出）和公开可见性（锁屏能看到标题/备注），
     * 渠道按单条提醒配置补偿声音/震动，但不具备前台服务的循环播放能力。
     * 根因（为什么前台服务启动失败）仍需要看 logcat 里上一行的异常堆栈来定位。
     */
    @SuppressLint("MissingPermission")
    private fun postFallbackNotification(context: Context, reminder: Reminder, alarmKind: String) {
        if (!AlarmTriggerPolicy.shouldUseNoisyFallback(alarmKind)) return
        val kind = if (alarmKind == KIND_ADVANCE) AlarmAlertKind.ADVANCE else AlarmAlertKind.DUE
        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse(AlarmIntentIdentity.alert(reminder.id, kind))
            putExtra(AlarmActivity.EXTRA_REMINDER_ID, reminder.id)
            putExtra(AlarmActivity.EXTRA_ALARM_ID, reminder.alarmId)
            putExtra(AlarmActivity.EXTRA_TITLE, reminder.title)
            putExtra(AlarmActivity.EXTRA_NOTE, reminder.note)
            putExtra(AlarmActivity.EXTRA_ALARM_TIME, reminder.effectiveTime)
            putExtra(AlarmActivity.EXTRA_ALARM_KIND, kind.name)
        }
        val requestCode = if (alarmKind == KIND_ADVANCE) {
            AlarmSchedulerImpl.advanceAlarmRequestCode(reminder.alarmId)
        } else {
            reminder.alarmId
        }
        val contentPendingIntent = activityPendingIntent(context, requestCode, activityIntent)
        val titlePrefix = if (alarmKind == KIND_ADVANCE) "提前提醒：" else ""
        val title = "$titlePrefix${reminder.title}"
        val previewText = reminder.note?.ifBlank { null } ?: "提醒时间到了"
        val publicPreview = NotificationCompat.Builder(
            context,
            AlarmNotificationPolicy.fallbackChannelId(
                sound = reminder.notifySound,
                vibrate = reminder.notifyVibrate
            )
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(previewText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(previewText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPendingIntent)
            .build()
        val notification = NotificationCompat.Builder(
            context,
            AlarmNotificationPolicy.fallbackChannelId(
                sound = reminder.notifySound,
                vibrate = reminder.notifyVibrate
            )
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(previewText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(previewText))
            .setTicker("$title：$previewText")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setAutoCancel(false)
            .setOngoing(false)
            .setPublicVersion(publicPreview)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(contentPendingIntent, true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(reminder.alarmId, notification)
        }.onFailure {
            Log.e(TAG, "备用强提醒通知发布失败 reminderId=${reminder.id} kind=$alarmKind", it)
        }
        runCatching {
            contentPendingIntent.send(
                context,
                0,
                null,
                null,
                null,
                null,
                senderBackgroundActivityLaunchOptions()
            )
        }.onFailure {
            Log.e(TAG, "备用全屏提醒页启动失败 reminderId=${reminder.id} kind=$alarmKind", it)
        }
        Log.i(
            TAG,
            "已执行备用强提醒 reminderId=${reminder.id} kind=$alarmKind " +
                "sound=${reminder.notifySound} vibrate=${reminder.notifyVibrate}"
        )
    }

    private fun activityPendingIntent(
        context: Context,
        requestCode: Int,
        intent: Intent
    ): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val options = creatorBackgroundActivityLaunchOptions()
        return if (options == null) {
            PendingIntent.getActivity(context, requestCode, intent, flags)
        } else {
            runCatching {
                PendingIntent.getActivity(context, requestCode, intent, flags, options)
            }.getOrElse {
                PendingIntent.getActivity(context, requestCode, intent, flags)
            }
        }
    }

    private fun creatorBackgroundActivityLaunchOptions(): Bundle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                pendingIntentCreatorBackgroundActivityStartMode = backgroundActivityStartMode()
            }.toBundle()
        } else {
            null
        }

    private fun senderBackgroundActivityLaunchOptions(): Bundle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(backgroundActivityStartMode())
            }.toBundle()
        } else {
            null
        }

    private fun backgroundActivityStartMode(): Int =
        if (Build.VERSION.SDK_INT >= 36) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        } else {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }

    companion object {
        private const val TAG = "AlarmReceiver"

        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_ALARM_KIND = "extra_alarm_kind"
        const val KIND_DUE = "due"
        const val KIND_ADVANCE = "advance"
    }
}
