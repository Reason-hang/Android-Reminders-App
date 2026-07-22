package com.reminder.local.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.reminder.local.data.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert
    suspend fun insert(entity: CategoryEntity): Long

    @Update
    suspend fun update(entity: CategoryEntity)

    @Delete
    suspend fun delete(entity: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    /** 删除自定义分类前，把归属于它的提醒统一改成"未分类"（categoryId = null）。 */
    @Query("UPDATE reminders SET categoryId = NULL WHERE categoryId = :categoryId")
    suspend fun reassignRemindersToUncategorized(categoryId: Long)

    @Transaction
    suspend fun deleteAndReassign(entity: CategoryEntity) {
        reassignRemindersToUncategorized(entity.id)
        delete(entity)
    }
}
