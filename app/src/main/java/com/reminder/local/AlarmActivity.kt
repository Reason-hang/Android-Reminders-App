package com.reminder.local

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.alarm.AlarmScheduler
import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.usecase.CompleteReminderUseCase
import com.reminder.local.notification.NotificationHelper
import com.reminder.local.receiver.NotificationActionReceiver
import com.reminder.local.service.AlarmAlertKind
import com.reminder.local.service.AlarmAlertService
import com.reminder.local.ui.theme.ReminderAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject lateinit var reminderRepository: ReminderRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var completeReminderUseCase: CompleteReminderUseCase

    private val reminderState = mutableStateOf<Reminder?>(null)
    private val alarmTimeState = mutableStateOf<Long?>(null)
    private val alarmKindState = mutableStateOf(AlarmAlertKind.DUE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAlarmWindow()

        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        alarmTimeState.value = intent.getLongExtra(EXTRA_ALARM_TIME, -1L).takeIf { it > 0L }
        alarmKindState.value = AlarmAlertKind.fromWireValue(intent.getStringExtra(EXTRA_ALARM_KIND))
        lifecycleScope.launch {
            val reminder = reminderRepository.getById(reminderId)
            reminderState.value = reminder
        }

        setContent {
            ReminderAppTheme {
                AlarmScreen(
                    reminder = reminderState.value,
                    alarmTime = alarmTimeState.value,
                    alarmKind = alarmKindState.value,
                    onClose = { closeAlertOnly() },
                    onSnooze = { snoozeAndClose() },
                    onDone = { markDoneAndClose() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        stopAlert()
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        alarmTimeState.value = intent.getLongExtra(EXTRA_ALARM_TIME, -1L).takeIf { it > 0L }
        alarmKindState.value = AlarmAlertKind.fromWireValue(intent.getStringExtra(EXTRA_ALARM_KIND))
        lifecycleScope.launch {
            val reminder = reminderRepository.getById(reminderId)
            reminderState.value = reminder
        }
    }

    private fun configureAlarmWindow() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)
    }

    private fun stopAlert() {
        stopService(Intent(this, AlarmAlertService::class.java))
    }

    private fun closeAlertOnly() {
        val reminder = reminderState.value
        stopAlert()
        if (reminder != null) notificationHelper.cancelNotification(reminder)
        finish()
    }

    private fun snoozeAndClose() {
        val reminder = reminderState.value ?: return
        lifecycleScope.launch {
            stopAlert()
            notificationHelper.cancelNotification(reminder)
            runCatching {
                alarmScheduler.scheduleSnooze(
                    reminder,
                    NotificationActionReceiver.SNOOZE_DELAY_MILLIS
                )
            }
            finish()
        }
    }

    private fun markDoneAndClose() {
        val reminder = reminderState.value ?: return
        lifecycleScope.launch {
            stopAlert()
            completeReminderUseCase.markDone(reminder)
            notificationHelper.cancelNotification(reminder)
            finish()
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "extra_alarm_reminder_id"
        const val EXTRA_ALARM_TIME = "extra_alarm_time"
        const val EXTRA_ALARM_KIND = "extra_alarm_kind"
    }
}

@Composable
private fun AlarmScreen(
    reminder: Reminder?,
    alarmTime: Long?,
    alarmKind: AlarmAlertKind,
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
                text = if (alarmKind == AlarmAlertKind.ADVANCE) "提前提醒" else "提醒事项",
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
