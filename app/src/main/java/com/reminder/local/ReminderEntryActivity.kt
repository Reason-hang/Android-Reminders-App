package com.reminder.local

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.reminder.local.ui.navigation.AppNavGraph
import com.reminder.local.ui.theme.ReminderAppTheme
import dagger.hilt.android.AndroidEntryPoint

/** 仅供本 App 自己的通知 PendingIntent 打开指定提醒，禁止外部应用直接调用。 */
@AndroidEntryPoint
class ReminderEntryActivity : ComponentActivity() {

    private val reminderIdState = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reminderIdState.value = reminderIdFrom(intent)
        setContent {
            ReminderAppTheme {
                AppNavGraph(startReminderId = reminderIdState.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reminderIdState.value = reminderIdFrom(intent)
    }

    private fun reminderIdFrom(intent: Intent): Long? =
        intent.getLongExtra(EXTRA_OPEN_REMINDER_ID, -1L).takeIf { it >= 0L }

    companion object {
        const val EXTRA_OPEN_REMINDER_ID = "extra_open_reminder_id"
    }
}
