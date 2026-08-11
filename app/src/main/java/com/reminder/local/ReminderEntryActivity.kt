package com.reminder.local

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.reminder.local.ui.navigation.AppNavGraph
import com.reminder.local.ui.theme.ReminderAppTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 兼容 v1.8 及之前已经发出的保留提醒通知。
 *
 * 历史版本把该通知直接路由到编辑页；新版只进入列表，避免一次通知点击被误解为编辑操作。
 */
@AndroidEntryPoint
class ReminderEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReminderAppTheme {
                AppNavGraph()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
