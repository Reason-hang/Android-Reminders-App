package com.reminder.local.ui.screen.list

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderListPermissionRefreshSourceContractTest {

    @Test
    fun listScreenRefreshesExactAlarmPermissionWhenItResumes() {
        val source = File("src/main/java/com/reminder/local/ui/screen/list/ReminderListScreen.kt").readText()

        assertTrue(source.contains("Lifecycle.Event.ON_RESUME"))
        assertTrue(source.contains("viewModel.refreshExactAlarmPermission()"))
    }
}
