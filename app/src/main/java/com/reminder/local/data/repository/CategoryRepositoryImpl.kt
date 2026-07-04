package com.reminder.local.data.repository

import com.reminder.local.data.db.dao.CategoryDao
import com.reminder.local.domain.mapper.toDomain
import com.reminder.local.domain.mapper.toEntity
import com.reminder.local.domain.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): Category? = dao.getById(id)?.toDomain()

    override suspend fun insert(category: Category): Long = dao.insert(category.toEntity())

    override suspend fun update(category: Category) = dao.update(category.toEntity())

    override suspend fun delete(category: Category) {
        dao.reassignRemindersToUncategorized(category.id)
        dao.delete(category.toEntity())
    }

    override suspend fun count(): Int = dao.count()
}
