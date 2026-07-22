package com.reminder.local.domain.usecase

import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.ReminderStatus
import com.reminder.local.domain.model.RepeatType
import com.reminder.local.testing.InMemoryReminderRepository
import com.reminder.local.testing.RecordingAlarmScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RescheduleAllAlarmsUseCaseTest {

    @Test
    fun oneScheduleFailureDoesNotBlockLaterReminders() = runBlocking {
        val first = futureReminder(id = 1L, alarmId = 101)
        val second = futureReminder(id = 2L, alarmId = 202)
        val repository = InMemoryReminderRepository(listOf(first, second))
        val scheduler = RecordingAlarmScheduler().apply { failAlarmIds += first.alarmId }

        RescheduleAllAlarmsUseCase(repository, scheduler)()

        assertFalse(scheduler.scheduled.containsKey(first.alarmId))
        assertEquals(second, scheduler.scheduled[second.alarmId])
        assertEquals(first, repository.requireReminder(first.id))
    }

    @Test
    fun missedRepeatingReminderCatchesUpToFutureOccurrence() = runBlocking {
        val trigger = System.currentTimeMillis() - 3L * DAY
        val reminder = Reminder(
            id = 3L,
            title = "追赶重复提醒",
            triggerTime = trigger,
            nextTriggerTime = trigger,
            repeatType = RepeatType.DAILY,
            alarmId = 303
        )
        val repository = InMemoryReminderRepository(listOf(reminder))
        val scheduler = RecordingAlarmScheduler()

        RescheduleAllAlarmsUseCase(repository, scheduler)()

        val restored = repository.requireReminder(reminder.id)
        assertTrue(restored.effectiveTime > System.currentTimeMillis())
        assertEquals(restored, scheduler.scheduled[reminder.alarmId])
    }

    @Test
    fun catchUpScheduleFailureRollsDatabaseBackToPreviousOccurrence() = runBlocking {
        val trigger = System.currentTimeMillis() - 2L * DAY
        val reminder = Reminder(
            id = 6L,
            title = "追赶失败回滚",
            triggerTime = trigger,
            nextTriggerTime = trigger,
            repeatType = RepeatType.DAILY,
            alarmId = 606
        )
        val repository = InMemoryReminderRepository(listOf(reminder))
        val scheduler = RecordingAlarmScheduler().apply { failSchedule = true }

        RescheduleAllAlarmsUseCase(repository, scheduler)()

        assertEquals(reminder, repository.requireReminder(reminder.id))
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun missingExactAlarmPermissionDoesNotMutatePendingReminder() = runBlocking {
        val reminder = futureReminder(id = 4L, alarmId = 404)
        val repository = InMemoryReminderRepository(listOf(reminder))
        val scheduler = RecordingAlarmScheduler().apply { canSchedule = false }

        RescheduleAllAlarmsUseCase(repository, scheduler)()

        assertEquals(reminder, repository.requireReminder(reminder.id))
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun repeatPastEndDateBecomesDoneWithoutNewAlarm() = runBlocking {
        val past = System.currentTimeMillis() - DAY
        val reminder = Reminder(
            id = 5L,
            title = "已到截止日期",
            triggerTime = past - DAY,
            nextTriggerTime = past,
            repeatType = RepeatType.DAILY,
            repeatEndDate = past - 1L,
            alarmId = 505
        )
        val repository = InMemoryReminderRepository(listOf(reminder))
        val scheduler = RecordingAlarmScheduler(listOf(reminder))

        RescheduleAllAlarmsUseCase(repository, scheduler)()

        assertEquals(ReminderStatus.DONE, repository.requireReminder(reminder.id).status)
        assertFalse(scheduler.scheduled.containsKey(reminder.alarmId))
    }

    private fun futureReminder(id: Long, alarmId: Int): Reminder {
        val trigger = System.currentTimeMillis() + DAY
        return Reminder(
            id = id,
            title = "开机恢复测试$id",
            triggerTime = trigger,
            nextTriggerTime = trigger,
            alarmId = alarmId
        )
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
