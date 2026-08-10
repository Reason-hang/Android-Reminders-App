package com.reminder.local.service

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmAlertServiceSourceContractTest {

    @Test
    fun serviceOwnsInstanceScopedAutoStopLifecycle() {
        val source = File("src/main/java/com/reminder/local/service/AlarmAlertService.kt").readText()

        assertTrue(source.contains("scheduleAutoStop(incomingInstance)"))
        assertTrue(source.contains("timeoutTargetsCurrent"))
        assertTrue(source.contains("sendAutoDismissBroadcast(scheduledInstance)"))
        assertTrue(source.contains("showRetainedAlertNotification"))
    }
}
