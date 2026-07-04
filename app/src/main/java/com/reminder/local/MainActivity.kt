package com.reminder.local

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.reminder.local.ui.navigation.AppNavGraph
import com.reminder.local.ui.theme.ReminderAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingReminderIdState = mutableStateOf<Long?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op：被拒绝也不阻塞使用 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingReminderIdState.value = extractReminderId(intent)
        requestNotificationPermissionIfNeeded()

        setContent {
            ReminderAppTheme {
                AppNavGraph(startReminderId = pendingReminderIdState.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingReminderIdState.value = extractReminderId(intent)
    }

    private fun extractReminderId(intent: Intent?): Long? {
        val id = intent?.getLongExtra(EXTRA_OPEN_REMINDER_ID, -1L) ?: -1L
        return if (id >= 0) id else null
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_OPEN_REMINDER_ID = "extra_open_reminder_id"
    }
}
