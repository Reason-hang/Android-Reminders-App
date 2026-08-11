package com.reminder.local

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.alarm.AlarmScheduler
import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.RepeatActionScope
import com.reminder.local.domain.usecase.CompleteReminderUseCase
import com.reminder.local.receiver.NotificationActionReceiver
import com.reminder.local.service.AlarmAlertKind
import com.reminder.local.service.AlarmAlertInstanceKey
import com.reminder.local.service.AlarmAlertLaunchPolicy
import com.reminder.local.service.AlarmAlertService
import com.reminder.local.diagnostics.core.DiagnosticEventName
import com.reminder.local.diagnostics.core.DiagnosticStage
import com.reminder.local.diagnostics.core.DiagnosticTraceId
import com.reminder.local.diagnostics.platform.DiagnosticLogger
import com.reminder.local.ui.theme.ReminderAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject lateinit var reminderRepository: ReminderRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var completeReminderUseCase: CompleteReminderUseCase
    @Inject lateinit var diagnosticLogger: DiagnosticLogger

    private val reminderState = mutableStateOf<Reminder?>(null)
    private val alarmTimeState = mutableStateOf<Long?>(null)
    private val alarmKindState = mutableStateOf(AlarmAlertKind.DUE)
    private val actionErrorState = mutableStateOf<String?>(null)
    private var loadReminderJob: Job? = null
    private var autoDismissFallbackJob: Job? = null
    private var autoDismissReceiverRegistered = false
    private val autoDismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AlarmAlertService.ACTION_AUTO_DISMISS) return
            val dismissedInstance = alertInstanceFrom(intent) ?: return
            if (dismissedInstance == currentAlertInstance()) {
                Log.i(
                    TAG,
                    "收到自动结束通知 alarmId=${dismissedInstance.alarmId} " +
                        "kind=${dismissedInstance.kind}"
                )
                finishAfterAutoDismiss()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAlarmWindow()
        registerAutoDismissReceiver()

        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        Log.d(TAG, "onCreate reminderId=$reminderId")
        alarmTimeState.value = intent.getLongExtra(EXTRA_ALARM_TIME, -1L).takeIf { it > 0L }
        alarmKindState.value = AlarmAlertKind.fromWireValue(intent.getStringExtra(EXTRA_ALARM_KIND))
        recordActivityEvent(DiagnosticEventName.ACTIVITY_CREATED, captureSnapshot = true)
        actionErrorState.value = null
        loadReminder(reminderId)
        scheduleAutoDismissFallback()

        setContent {
            ReminderAppTheme {
                AlarmScreen(
                    reminder = reminderState.value,
                    alarmTime = alarmTimeState.value,
                    alarmKind = alarmKindState.value,
                    actionError = actionErrorState.value,
                    onClose = { closeAlertOnly() },
                    onSnooze = { snoozeAndClose() },
                    onDone = { markDoneAndClose() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        Log.d(TAG, "onNewIntent reminderId=$reminderId")
        alarmTimeState.value = intent.getLongExtra(EXTRA_ALARM_TIME, -1L).takeIf { it > 0L }
        alarmKindState.value = AlarmAlertKind.fromWireValue(intent.getStringExtra(EXTRA_ALARM_KIND))
        recordActivityEvent(DiagnosticEventName.ACTIVITY_NEW_INTENT, captureSnapshot = true)
        actionErrorState.value = null
        loadReminder(reminderId)
        scheduleAutoDismissFallback()
    }

    override fun onDestroy() {
        autoDismissFallbackJob?.cancel()
        if (autoDismissReceiverRegistered) {
            unregisterReceiver(autoDismissReceiver)
            autoDismissReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        recordActivityEvent(DiagnosticEventName.ACTIVITY_RESUMED, captureSnapshot = true)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) recordActivityEvent(DiagnosticEventName.ACTIVITY_FOCUSED)
    }

    private fun loadReminder(reminderId: Long) {
        loadReminderJob?.cancel()
        reminderState.value = null
        loadReminderJob = lifecycleScope.launch {
            reminderState.value = reminderRepository.getById(reminderId)
        }
    }

    private fun configureAlarmWindow() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
    }

    private fun registerAutoDismissReceiver() {
        ContextCompat.registerReceiver(
            this,
            autoDismissReceiver,
            IntentFilter(AlarmAlertService.ACTION_AUTO_DISMISS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        autoDismissReceiverRegistered = true
    }

    private fun scheduleAutoDismissFallback() {
        autoDismissFallbackJob?.cancel()
        val scheduledInstance = currentAlertInstance() ?: return
        autoDismissFallbackJob = lifecycleScope.launch {
            delay(AlarmAlertLaunchPolicy.ALERT_AUTO_STOP_TIMEOUT_MILLIS)
            if (scheduledInstance == currentAlertInstance()) {
                Log.w(
                    TAG,
                    "服务未通知页面结束，执行全屏页超时兜底 alarmId=${scheduledInstance.alarmId} " +
                        "kind=${scheduledInstance.kind}"
                )
                finishAfterAutoDismiss()
            }
        }
    }

    private fun currentAlertInstance(): AlarmAlertInstanceKey? = alertInstanceFrom(intent)

    private fun alertInstanceFrom(source: Intent): AlarmAlertInstanceKey? {
        val alarmId = source.getIntExtra(EXTRA_ALARM_ID, Int.MIN_VALUE)
        val occurrenceTime = source.getLongExtra(EXTRA_ALARM_TIME, -1L)
        if (alarmId == Int.MIN_VALUE || occurrenceTime <= 0L) return null
        return AlarmAlertInstanceKey(
            alarmId = alarmId,
            kind = AlarmAlertKind.fromWireValue(source.getStringExtra(EXTRA_ALARM_KIND)),
            occurrenceTime = occurrenceTime
        )
    }

    private fun finishAfterAutoDismiss() {
        autoDismissFallbackJob?.cancel()
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        setTurnScreenOn(false)
        finish()
    }

    private fun recordActivityEvent(name: String, captureSnapshot: Boolean = false) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, Int.MIN_VALUE)
        val occurrenceTime = intent.getLongExtra(EXTRA_ALARM_TIME, -1L)
        if (reminderId < 0L || alarmId == Int.MIN_VALUE || occurrenceTime <= 0L) return
        diagnosticLogger.record(
            DiagnosticStage.ACTIVITY,
            name,
            traceId = DiagnosticTraceId.alert(
                reminderId,
                alarmId,
                AlarmAlertKind.fromWireValue(intent.getStringExtra(EXTRA_ALARM_KIND)).name,
                occurrenceTime
            ),
            details = mapOf(
                "launchSource" to intent.getStringExtra(EXTRA_LAUNCH_SOURCE).orEmpty().ifBlank {
                    LAUNCH_SOURCE_UNKNOWN
                }
            ),
            captureSnapshot = captureSnapshot
        )
    }

    private fun closeAlertOnly() {
        val reminder = reminderState.value
        val reminderId = reminder?.id ?: intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val alarmId = reminder?.alarmId ?: intent.getIntExtra(EXTRA_ALARM_ID, Int.MIN_VALUE)
        val occurrenceTime = alarmTimeState.value
        if (reminderId >= 0L && alarmId != Int.MIN_VALUE && occurrenceTime != null) {
            startService(
                AlarmAlertService.stopIntent(
                    context = this,
                    reminderId = reminderId,
                    alarmId = alarmId,
                    title = reminder?.title ?: intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    note = reminder?.note ?: intent.getStringExtra(EXTRA_NOTE),
                    kind = alarmKindState.value,
                    occurrenceTime = occurrenceTime,
                    source = AlarmAlertService.STOP_SOURCE_ALARM_PAGE_CLOSE
                )
            )
        } else {
            Log.w(TAG, "关闭提醒缺少实例标识，拒绝无条件停止服务")
        }
        finish()
    }

    private fun snoozeAndClose() {
        val reminder = reminderState.value ?: return
        lifecycleScope.launch {
            val scheduled = runCatching {
                alarmScheduler.scheduleSnooze(
                    reminder,
                    NotificationActionReceiver.SNOOZE_DELAY_MILLIS
                )
            }.onFailure {
                // 2026-07 第二轮复查修复：之前这里失败会被完全吞掉，用户点了"稍后提醒"、
                // 页面正常关闭，但闹钟其实没有注册成功，10 分钟后不会再提醒，且没有任何记录。
                Log.e(TAG, "稍后提醒调度失败 reminderId=${reminder.id}", it)
            }.isSuccess
            if (scheduled) {
                stopTargetAlert(
                    reminder = reminder,
                    retainNotification = false,
                    source = AlarmAlertService.STOP_SOURCE_ALARM_PAGE_SNOOZE
                )
                finish()
            } else {
                actionErrorState.value = "稍后提醒设置失败，请重试"
            }
        }
    }

    private fun markDoneAndClose() {
        val reminder = reminderState.value ?: return
        lifecycleScope.launch {
            val success = completeReminderUseCase.markDone(
                reminder,
                RepeatActionScope.ONCE,
                alarmTimeState.value
            )
            if (success) {
                stopTargetAlert(
                    reminder = reminder,
                    retainNotification = false,
                    source = AlarmAlertService.STOP_SOURCE_ALARM_PAGE_DONE
                )
                finish()
            } else {
                actionErrorState.value = "标为完成失败，请重试"
            }
        }
    }

    private fun stopTargetAlert(
        reminder: Reminder,
        retainNotification: Boolean,
        source: String
    ) {
        val occurrenceTime = alarmTimeState.value ?: return
        startService(
            AlarmAlertService.stopIntent(
                context = this,
                reminderId = reminder.id,
                alarmId = reminder.alarmId,
                title = reminder.title,
                note = reminder.note,
                kind = alarmKindState.value,
                occurrenceTime = occurrenceTime,
                retainNotification = retainNotification,
                source = source
            )
        )
    }

    companion object {
        private const val TAG = "AlarmActivity"
        const val EXTRA_REMINDER_ID = "extra_alarm_reminder_id"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_NOTE = "extra_note"
        const val EXTRA_ALARM_TIME = "extra_alarm_time"
        const val EXTRA_ALARM_KIND = "extra_alarm_kind"
        const val EXTRA_LAUNCH_SOURCE = "extra_launch_source"
        const val LAUNCH_SOURCE_NOTIFICATION_FULL_SCREEN = "notification_full_screen"
        const val LAUNCH_SOURCE_ALERT_NOTIFICATION = "alert_notification"
        const val LAUNCH_SOURCE_ALARM_CLOCK = "alarm_clock"
        const val LAUNCH_SOURCE_UNKNOWN = "unknown"
    }
}

@Composable
private fun AlarmScreen(
    reminder: Reminder?,
    alarmTime: Long?,
    alarmKind: AlarmAlertKind,
    actionError: String?,
    onClose: () -> Unit,
    onSnooze: () -> Unit,
    onDone: () -> Unit
) {
    BackHandler(enabled = true) {}

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = when (alarmKind) {
                    AlarmAlertKind.ADVANCE -> "提前提醒"
                    AlarmAlertKind.SNOOZE -> "稍后提醒"
                    AlarmAlertKind.DUE -> "提醒事项"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = reminder?.title ?: "正在加载提醒",
                fontSize = 32.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            reminder?.note?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            reminder?.let {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = formatAlarmTime(alarmTime ?: it.effectiveTime),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(44.dp))
            actionError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(12.dp))
            }
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("关闭")
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth(),
                enabled = reminder != null
            ) {
                Text("稍后提醒 10 分钟")
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                enabled = reminder != null
            ) {
                Text("标为完成")
            }
        }
    }
}

private fun formatAlarmTime(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm"))
