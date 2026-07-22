package com.reminder.local.domain.usecase

import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.RepeatActionScope
import com.reminder.local.domain.model.RepeatType
import com.reminder.local.testing.InMemoryReminderRepository
import com.reminder.local.testing.RecordingAlarmScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CompleteReminderUseCaseTest {

    @Test
    fun onceRescheduleFailureKeepsExistingReminderAndAlarm() = runBlocking {
        val reminder = repeatingReminder()
        val repository = InMemoryReminderRepository(listOf(reminder))
        val scheduler = RecordingAlarmScheduler(listOf(reminder)).apply { failSchedule = true }

        CompleteReminderUseCase(repository, scheduler)
            .markDone(reminder, RepeatActionScope.ONCE)

        assertEquals(reminder, repository.requireReminder(reminder.id))
        assertEquals(reminder, scheduler.scheduled[reminder.alarmId])
    }

    @Test
    fun allDatabaseFailureDoesNotCancelExistingAlarm() = runBlocking {
        val reminder = repeatingReminder()
        val repository = InMemoryReminderRepository(listOf(reminder)).apply { failUpdate = true }
        val scheduler = RecordingAlarmScheduler(listOf(reminder))

        runCatching {
            CompleteReminderUseCase(repository, scheduler)
                .markDone(reminder, RepeatActionScope.ALL)
        }

        assertEquals(reminder, scheduler.scheduled[reminder.alarmId])
    }

    @Test
    fun completingOccurrenceAlreadyAdvancedByReceiverDoesNotSkipNextPeriod() = runBlocking {
        val occurrence = System.currentTimeMillis() - 60_000L
        val next = occurrence + 24 * 60 * 60 * 1000L
        val reminder = Reminder(
            id = 2L,
            title = "已推进完成测试",
            triggerTime = occurrence,
            nextTriggerTime = next,
            repeatType = RepeatType.DAILY,
            alarmId = 204
        )
        val repository = InMemoryReminderRepository(listOf(reminder))
        val scheduler = RecordingAlarmScheduler(listOf(reminder))

        val success = CompleteReminderUseCase(repository, scheduler).markDone(
            reminder,
            RepeatActionScope.ONCE,
            occurrenceTime = occurrence
        )

        assertEquals(true, success)
        assertEquals(next, repository.requireReminder(reminder.id).nextTriggerTime)
        assertEquals(next, scheduler.scheduled[reminder.alarmId]?.nextTriggerTime)
    }

    @Test
    fun occurrenceConflictDoesNotAdvanceOrReplaceAlarm() = runBlocking {
        val reminder = repeatingReminder()
        val repository = InMemoryReminderRepository(listOf(reminder)).apply {
            forceOccurrenceConflict = true
        }
        val scheduler = RecordingAlarmScheduler(listOf(reminder))

        val success = CompleteReminderUseCase(repository, scheduler)
            .markDone(reminder, RepeatActionScope.ONCE)

        assertEquals(false, success)
        assertEquals(reminder, repository.requireReminder(reminder.id))
        assertEquals(reminder, scheduler.scheduled[reminder.alarmId])
    }

    @Test
    fun staleOneTimeNotificationDoesNotCompleteRescheduledReminder() = runBlocking {
        val oldOccurrence = System.currentTimeMillis() - 60_000L
        val rescheduled = Reminder(
            id = 3L,
            title = "改期后的一次性提醒",
            triggerTime = oldOccurrence + 3_600_000L,
            alarmId = 205
        )
        val repository = InMemoryReminderRepository(listOf(rescheduled))
        val scheduler = RecordingAlarmScheduler(listOf(rescheduled))

        val success = CompleteReminderUseCase(repository, scheduler).markDone(
            rescheduled,
            RepeatActionScope.ONCE,
            occurrenceTime = oldOccurrence
        )

        assertEquals(true, success)
        assertEquals(rescheduled, repository.requireReminder(rescheduled.id))
        assertEquals(rescheduled, scheduler.scheduled[rescheduled.alarmId])
    }

    private fun repeatingReminder(): Reminder {
        val trigger = System.currentTimeMillis() + 3_600_000L
        return Reminder(
            id = 1L,
            title = "完成测试",
            triggerTime = trigger,
            nextTriggerTime = trigger,
            repeatType = RepeatType.DAILY,
            alarmId = 202
        )
    }
}
