package com.reminder.local.domain.usecase

import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.RepeatActionScope
import com.reminder.local.domain.model.RepeatType
import com.reminder.local.testing.InMemoryReminderRepository
import com.reminder.local.testing.RecordingAlarmScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteReminderUseCaseTest {

    @Test
    fun onceRescheduleFailureKeepsExistingReminderAndAlarm() = runBlocking {
        val reminder = repeatingReminder()
        val repository = InMemoryReminderRepository(listOf(reminder))
        val scheduler = RecordingAlarmScheduler(listOf(reminder)).apply { failSchedule = true }

        DeleteReminderUseCase(repository, scheduler)(reminder, RepeatActionScope.ONCE)

        assertEquals(reminder, repository.requireReminder(reminder.id))
        assertEquals(reminder, scheduler.scheduled[reminder.alarmId])
    }

    @Test
    fun allDatabaseFailureDoesNotCancelExistingAlarm() = runBlocking {
        val reminder = repeatingReminder()
        val repository = InMemoryReminderRepository(listOf(reminder)).apply { failDelete = true }
        val scheduler = RecordingAlarmScheduler(listOf(reminder))

        runCatching {
            DeleteReminderUseCase(repository, scheduler)(reminder, RepeatActionScope.ALL)
        }

        assertEquals(reminder, scheduler.scheduled[reminder.alarmId])
    }

    @Test
    fun occurrenceConflictDoesNotAdvanceOrReplaceAlarm() = runBlocking {
        val reminder = repeatingReminder()
        val repository = InMemoryReminderRepository(listOf(reminder)).apply {
            forceOccurrenceConflict = true
        }
        val scheduler = RecordingAlarmScheduler(listOf(reminder))

        val success = DeleteReminderUseCase(repository, scheduler)(
            reminder,
            RepeatActionScope.ONCE
        )

        assertEquals(false, success)
        assertEquals(reminder, repository.requireReminder(reminder.id))
        assertEquals(reminder, scheduler.scheduled[reminder.alarmId])
    }

    private fun repeatingReminder(): Reminder {
        val trigger = System.currentTimeMillis() + 3_600_000L
        return Reminder(
            id = 1L,
            title = "删除测试",
            triggerTime = trigger,
            nextTriggerTime = trigger,
            repeatType = RepeatType.DAILY,
            alarmId = 303
        )
    }
}
