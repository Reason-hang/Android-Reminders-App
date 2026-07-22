package com.reminder.local.data.repository

import com.reminder.local.data.db.dao.ReminderDao
import com.reminder.local.domain.mapper.toDomain
import com.reminder.local.domain.mapper.toEntity
import com.reminder.local.domain.model.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ReminderRepositoryImpl @Inject constructor(
    private val dao: ReminderDao
) : ReminderRepository {

    override fun observeAll(): Flow<List<Reminder>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): Reminder? = dao.getById(id)?.toDomain()

    override suspend fun isAlarmIdInUse(alarmId: Int): Boolean = dao.isAlarmIdInUse(alarmId)

    override suspend fun insert(reminder: Reminder): Long = dao.insert(reminder.toEntity())

    override suspend fun update(reminder: Reminder) = dao.update(reminder.toEntity())

    override suspend fun updateIfOccurrenceCurrent(
        reminder: Reminder,
        expectedOccurrenceTime: Long
    ): Boolean = dao.updateIfOccurrenceCurrent(reminder.toEntity(), expectedOccurrenceTime)

    override suspend fun delete(reminder: Reminder) = dao.delete(reminder.toEntity())

    override suspend fun getAllPending(): List<Reminder> = dao.getAllPending().map { it.toDomain() }

    override suspend fun markNonRepeatingExpired(now: Long) = dao.markNonRepeatingExpired(now)

    override fun observeCountByCategory(categoryId: Long): Flow<Int> =
        dao.observeCountByCategory(categoryId)
}
