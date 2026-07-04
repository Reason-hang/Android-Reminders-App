package com.reminder.local.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.reminder.local.data.db.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Insert
    suspend fun insert(entity: ReminderEntity): Long

    @Update
    suspend fun update(entity: ReminderEntity)

    @Delete
    suspend fun delete(entity: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    /** 列表页监听：全部提醒，具体的分组/排序逻辑放在上层（domain/ui），DAO 只做基础查询。 */
    @Query("SELECT * FROM reminders ORDER BY triggerTime ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE status = 'PENDING'")
    suspend fun getAllPending(): List<ReminderEntity>

    /**
     * App 冷启动/开机时批量把"已过期但仍是 PENDING 且非重复"的提醒标记为 EXPIRED。
     * 重复提醒不会走这个逻辑（它们的过期判断在 RescheduleAllAlarmsUseCase 里处理）。
     */
    @Query(
        "UPDATE reminders SET status = 'EXPIRED', updatedAt = :now " +
            "WHERE status = 'PENDING' AND repeatType = 'NONE' AND triggerTime < :now"
    )
    suspend fun markNonRepeatingExpired(now: Long)

    @Query("SELECT COUNT(*) FROM reminders WHERE categoryId = :categoryId")
    fun observeCountByCategory(categoryId: Long): Flow<Int>
}
