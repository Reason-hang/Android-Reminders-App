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
}
