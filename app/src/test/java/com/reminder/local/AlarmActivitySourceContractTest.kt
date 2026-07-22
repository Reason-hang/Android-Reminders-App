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
}
