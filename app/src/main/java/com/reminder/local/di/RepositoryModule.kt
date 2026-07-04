package com.reminder.local.di

import com.reminder.local.data.repository.CategoryRepository
import com.reminder.local.data.repository.CategoryRepositoryImpl
import com.reminder.local.data.repository.ReminderRepository
import com.reminder.local.data.repository.ReminderRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindReminderRepository(impl: ReminderRepositoryImpl): ReminderRepository

    @Binds
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository
}
