package com.reminder.local.domain.usecase

import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.ReminderStatus
import com.reminder.local.testing.InMemoryReminderRepository
import com.reminder.local.testing.RecordingAlarmScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecycleReminderUseCasesTest {

    @Test
    fun restorePendingReminderSchedulesItAndClearsRecycleState() = runBlocking {
        val deleted = deletedReminder()
        val repository = InMemoryReminderRepository(listOf(deleted))
        val scheduler = RecordingAlarmScheduler()

        val restored = RestoreReminderUseCase(repository, scheduler)(deleted.id)

        assertTrue(restored)
        val record = repository.requireReminder(deleted.id)
        assertEquals(ReminderStatus.PENDING, record.status)
        assertEquals(null, record.deletedAt)
        assertEquals(null, record.statusBeforeDelete)
        assertEquals(record, scheduler.scheduled[record.alarmId])
    }

    @Test
    fun restoreScheduleFailureCancelsPartiallyRegisteredAlarm() = runBlocking {
        val deleted = deletedReminder()
        val repository = InMemoryReminderRepository(listOf(deleted))
        val scheduler = RecordingAlarmScheduler().apply { partiallyScheduleThenFail = true }

        assertFalse(RestoreReminderUseCase(repository, scheduler)(deleted.id))
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(ReminderStatus.DELETED, repository.requireReminder(deleted.id).status)
    }

    @Test
    fun permanentDeleteRejectsActiveReminderAndRemovesOnlyRecycleRecord() = runBlocking {
        val active = Reminder(id = 1, title = "正常", triggerTime = System.currentTimeMillis() + 60_000, alarmId = 1)
        val deleted = deletedReminder(id = 2)
        val repository = InMemoryReminderRepository(listOf(active, deleted))
        val useCase = PermanentlyDeleteReminderUseCase(repository, RecordingAlarmScheduler())

        assertFalse(useCase(active.id))
        assertTrue(useCase(deleted.id))
        assertEquals(active, repository.requireReminder(active.id))
        assertFalse(runCatching { repository.requireReminder(deleted.id) }.isSuccess)
    }

    @Test
    fun reorderRejectsDuplicateIdsAndPersistsUniqueOrder() = runBlocking {
        val first = Reminder(id = 1, title = "一", triggerTime = 10, alarmId = 1)
        val second = Reminder(id = 2, title = "二", triggerTime = 20, alarmId = 2)
        val repository = InMemoryReminderRepository(listOf(first, second))
        val useCase = ReorderRemindersUseCase(repository)

        assertFalse(useCase(listOf(first.id, first.id)))
        assertTrue(useCase(listOf(second.id, first.id)))
        assertEquals(0L, repository.requireReminder(second.id).manualSortOrder)
        assertEquals(1L, repository.requireReminder(first.id).manualSortOrder)
    }

    private fun deletedReminder(id: Long = 1) = Reminder(
        id = id,
        title = "回收站提醒",
        triggerTime = System.currentTimeMillis() + 60_000,
        alarmId = id.toInt() + 100,
        status = ReminderStatus.DELETED,
        statusBeforeDelete = ReminderStatus.PENDING,
        deletedAt = System.currentTimeMillis()
    )
}
