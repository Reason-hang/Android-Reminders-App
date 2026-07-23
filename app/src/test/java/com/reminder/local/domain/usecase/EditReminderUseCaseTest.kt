package com.reminder.local.domain.usecase

import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.RepeatType
import com.reminder.local.testing.InMemoryReminderRepository
import com.reminder.local.testing.RecordingAlarmScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditReminderUseCaseTest {

    @Test
    fun invalidRepeatEndDateDoesNotCancelExistingAlarm() = runBlocking {
        val old = futureReminder()
        val repository = InMemoryReminderRepository(listOf(old))
        val scheduler = RecordingAlarmScheduler(listOf(old))

        val result = EditReminderUseCase(repository, scheduler)(
            old.copy(
                triggerTime = old.triggerTime + 60_000L,
                repeatType = RepeatType.DAILY,
                repeatEndDate = old.triggerTime
            )
        )

        assertTrue(result is EditResult.Failure)
        assertEquals(old, scheduler.scheduled[old.alarmId])
        assertEquals(old, repository.requireReminder(old.id))
    }

    @Test
    fun databaseFailureRestoresExistingAlarm() = runBlocking {
        val old = futureReminder()
        val repository = InMemoryReminderRepository(listOf(old)).apply { failUpdate = true }
        val scheduler = RecordingAlarmScheduler(listOf(old))

        val result = EditReminderUseCase(repository, scheduler)(
            old.copy(triggerTime = old.triggerTime + 60_000L)
        )

        assertTrue(result is EditResult.Failure)
        assertEquals(old, scheduler.scheduled[old.alarmId])
    }

    @Test
    fun occurrenceConflictDoesNotOverwriteOrReplaceCurrentAlarm() = runBlocking {
        val old = futureReminder()
        val repository = InMemoryReminderRepository(listOf(old)).apply {
            forceOccurrenceConflict = true
        }
        val scheduler = RecordingAlarmScheduler(listOf(old))

        val result = EditReminderUseCase(repository, scheduler)(
            old.copy(triggerTime = old.triggerTime + 60_000L)
        )

        assertTrue(result is EditResult.Failure)
        assertEquals(old, repository.requireReminder(old.id))
        assertEquals(old, scheduler.scheduled[old.alarmId])
    }

    @Test
    fun noteLongerThanTwoHundredCharactersDoesNotReplaceExistingAlarm() = runBlocking {
        val old = futureReminder()
        val repository = InMemoryReminderRepository(listOf(old))
        val scheduler = RecordingAlarmScheduler(listOf(old))

        val result = EditReminderUseCase(repository, scheduler)(
            old.copy(note = "a".repeat(201))
        )

        assertEquals(EditResult.Failure("备注最多 200 个字符"), result)
        assertEquals(old, repository.requireReminder(old.id))
        assertEquals(old, scheduler.scheduled[old.alarmId])
    }

    private fun futureReminder(): Reminder = Reminder(
        id = 1L,
        title = "编辑测试",
        triggerTime = System.currentTimeMillis() + 3_600_000L,
        nextTriggerTime = System.currentTimeMillis() + 3_600_000L,
        alarmId = 101
    )
}
