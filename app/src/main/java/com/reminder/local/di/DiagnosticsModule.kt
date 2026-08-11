package com.reminder.local.di

import com.reminder.local.diagnostics.platform.AppDiagnosticLogger
import com.reminder.local.diagnostics.platform.DiagnosticLogger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DiagnosticsModule {
    @Binds
    abstract fun bindDiagnosticLogger(impl: AppDiagnosticLogger): DiagnosticLogger
}
