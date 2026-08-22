package com.reminder.local

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmActivitySourceContractTest {

    @Test
    fun alarmActivityImportsIntentForNewIntentHandling() {
        val source = File("src/main/java/com/reminder/local/AlarmActivity.kt").readText()

        assertTrue(source.contains("import android.content.Intent"))
    }

    @Test
    fun autoDismissClearsKeepScreenOnAndFinishesMatchingActivity() {
        val source = File("src/main/java/com/reminder/local/AlarmActivity.kt").readText()

        assertTrue(source.contains("AlarmAlertService.ACTION_AUTO_DISMISS"))
        assertTrue(source.contains("WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON"))
        assertTrue(source.contains("finishAfterAutoDismiss()"))
    }

    @Test
    fun alertPageUsesCompactTimeLabelAndActionOrder() {
        val source = File("src/main/java/com/reminder/local/AlarmActivity.kt").readText()

        assertTrue(source.contains("text = \"提醒时间 \${formatAlarmTime"))
        assertTrue(source.contains("Text(\"完成\")"))
        assertTrue(source.contains("Text(\"稍后提醒 10 分钟\")"))
        assertTrue(source.contains("Text(\"关闭提醒\")"))
        assertTrue(!source.contains("请确认后续操作"))
        assertTrue(!source.contains("提醒服务负责声音、震动、超时和屏幕状态"))
    }
}
