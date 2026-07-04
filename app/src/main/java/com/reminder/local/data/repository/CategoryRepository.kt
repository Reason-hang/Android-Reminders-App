package com.reminder.local.data.repository

import com.reminder.local.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun getById(id: Long): Category?
    suspend fun insert(category: Category): Long
    suspend fun update(category: Category)

    /** 删除分类，先把归属提醒改为未分类，再删除分类本身。内置分类禁止删除，由上层拦截。 */
    suspend fun delete(category: Category)
    suspend fun count(): Int
}
