package com.reminder.local.data.repository

import com.reminder.local.domain.model.Reminder
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun observeAll(): Flow<List<Reminder>>
    suspend fun getById(id: Long): Reminder?
    suspend fun isAlarmIdInUse(alarmId: Int): Boolean
    suspend fun insert(reminder: Reminder): Long
    suspend fun update(reminder: Reminder)
    suspend fun delete(reminder: Reminder)
    suspend fun getAllPending(): List<Reminder>
    suspend fun markNonRepeatingExpired(now: Long = System.currentTimeMillis())
    fun observeCountByCategory(categoryId: Long): Flow<Int>
}
