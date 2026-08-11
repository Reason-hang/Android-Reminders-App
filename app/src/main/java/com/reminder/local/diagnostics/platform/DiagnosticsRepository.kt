package com.reminder.local.diagnostics.platform

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.reminder.local.diagnostics.core.AlertTraceParser
import com.reminder.local.diagnostics.core.DiagnosticTraceSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: DiagnosticStore,
    private val preferences: DiagnosticPreferences
) {
    val enhancedUntil: Flow<Long> = preferences.enhancedUntil

    fun summaries(): List<DiagnosticTraceSummary> = AlertTraceParser.summarize(store.readRecent())

    suspend fun setEnhanced(enabled: Boolean) {
        if (enabled) preferences.enableFor24Hours() else preferences.disable()
    }

    fun clear() = store.clear()

    fun exportIntent(): Intent? = runCatching {
        val sources = store.eventFiles()
        if (sources.isEmpty()) return null
        val exportDir = File(context.cacheDir, "diagnostics-export").apply { mkdirs() }
        val target = File(exportDir, "reminder-diagnostics-${System.currentTimeMillis()}.zip")
        ZipOutputStream(target.outputStream()).use { zip ->
            sources.forEach { source ->
                zip.putNextEntry(ZipEntry(source.name))
                source.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(EXPORT_README.toByteArray())
            zip.closeEntry()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.diagnostics", target)
        Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }.getOrNull()

    private companion object {
        const val EXPORT_README = "ReminderApp diagnostics. Events contain no reminder title or note.\n"
    }
}
