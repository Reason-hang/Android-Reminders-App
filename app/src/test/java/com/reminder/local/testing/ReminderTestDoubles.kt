package com.reminder.local.testing

import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.domain.alarm.AlarmScheduler
import com.reminder.local.domain.model.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class InMemoryReminderRepository(
    reminders: List<Reminder> = emptyList()
) : ReminderRepository {
    private val records = reminders.associateBy { it.id }.toMutableMap()

    var failInsert = false
    var failUpdate = false
    var failDelete = false
    var forceOccurrenceConflict = false

    fun requireReminder(id: Long): Reminder = requireNotNull(records[id])

    override fun observeAll(): Flow<List<Reminder>> = flowOf(records.values.toList())

    override suspend fun getById(id: Long): Reminder? = records[id]

    override suspend fun isAlarmIdInUse(alarmId: Int): Boolean =
        records.values.any { it.alarmId == alarmId }

    override suspend fun insert(reminder: Reminder): Long {
        if (failInsert) error("insert failed")
        val id = reminder.id.takeIf { it > 0L } ?: ((records.keys.maxOrNull() ?: 0L) + 1L)
        records[id] = reminder.copy(id = id)
        return id
    }

    override suspend fun update(reminder: Reminder) {
        if (failUpdate) error("update failed")
        records[reminder.id] = reminder
    }

    override suspend fun updateIfOccurrenceCurrent(
        reminder: Reminder,
        expectedOccurrenceTime: Long
    ): Boolean {
        if (forceOccurrenceConflict) return false
        val current = records[reminder.id] ?: return false
        if (current.effectiveTime != expectedOccurrenceTime) return false
        update(reminder)
        return true
    }

    override suspend fun delete(reminder: Reminder) {
        if (failDelete) error("delete failed")
        records.remove(reminder.id)
    }

    override suspend fun getAllPending(): List<Reminder> = records.values.toList()

    override suspend fun markNonRepeatingExpired(now: Long) = Unit

    override fun observeCountByCategory(categoryId: Long): Flow<Int> = flowOf(0)
}

class RecordingAlarmScheduler(
    initiallyScheduled: List<Reminder> = emptyList()
) : AlarmScheduler {
    val scheduled = initiallyScheduled.associateBy { it.alarmId }.toMutableMap()
    var failSchedule = false
    val failAlarmIds = mutableSetOf<Int>()
    var canSchedule = true

    override fun canScheduleExactAlarms(): Boolean = canSchedule

    override fun scheduleExact(reminder: Reminder) {
        if (failSchedule || reminder.alarmId in failAlarmIds) error("schedule failed")
        scheduled[reminder.alarmId] = reminder
    }

    override fun cancel(reminder: Reminder) {
        scheduled.remove(reminder.alarmId)
    }

    override fun scheduleSnooze(reminder: Reminder, delayMillis: Long) = Unit
}
