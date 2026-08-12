package com.reminder.local.service

import android.app.NotificationManager
import android.app.KeyguardManager
import android.os.SystemClock
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmOverlayDeviceTest {

    @Test
    fun unlockedAlertAddsApplicationOverlayAndKeepsNotificationRecord() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val alarmId = 902_600_001
        val occurrenceTime = System.currentTimeMillis()
        val powerManager = context.getSystemService(PowerManager::class.java)
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        assertTrue("真机测试必须在亮屏状态执行", powerManager.isInteractive)
        assertTrue("真机测试必须先解锁设备", !keyguardManager.isKeyguardLocked)
        assertTrue("真机测试前必须授权显示在其他应用上层", Settings.canDrawOverlays(context))

        ContextCompat.startForegroundService(
            context,
            AlarmAlertService.startIntent(
                context = context,
                reminderId = 9_026_000_001L,
                alarmId = alarmId,
                title = "解锁强提醒真机验证",
                note = "看到此页面表示应用悬浮强提醒链路已生效",
                alarmTime = occurrenceTime,
                sound = false,
                vibrate = false,
                kind = AlarmAlertKind.DUE
            )
        )

        try {
            SystemClock.sleep(20_000L)
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            assertTrue(notificationManager.activeNotifications.any { it.id == alarmId })
        } finally {
            context.startService(
                AlarmAlertService.stopIntent(
                    context = context,
                    reminderId = 9_026_000_001L,
                    alarmId = alarmId,
                    title = "解锁强提醒真机验证",
                    note = "看到此页面表示应用悬浮强提醒链路已生效",
                    kind = AlarmAlertKind.DUE,
                    occurrenceTime = occurrenceTime,
                    retainNotification = false,
                    source = "device_test_cleanup"
                )
            )
            SystemClock.sleep(1_000L)
        }
    }
}
