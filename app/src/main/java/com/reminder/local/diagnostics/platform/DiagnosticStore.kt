package com.reminder.local.diagnostics.platform

import android.content.Context
import android.util.JsonReader
import android.util.JsonWriter
import com.reminder.local.diagnostics.core.DiagnosticEvent
import com.reminder.local.diagnostics.core.DiagnosticLevel
import com.reminder.local.diagnostics.core.DiagnosticStage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/** 应用私有、追加式 JSONL；诊断 I/O 永远在独立单线程执行，失败被吞掉。 */
@Singleton
class DiagnosticStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val directory = File(context.filesDir, "diagnostics")
    private val eventsFile = File(directory, "events.jsonl")
    private val previousEventsFile = File(directory, "events.previous.jsonl")
    private val writer = Executors.newSingleThreadExecutor()

    fun append(event: DiagnosticEvent) {
        writer.execute {
            runCatching {
                directory.mkdirs()
                rotateIfNeeded()
                FileWriter(eventsFile, true).use { fileWriter ->
                    JsonWriter(fileWriter).also { json ->
                        writeEvent(json, event)
                        json.flush()
                    }
                    fileWriter.append('\n')
                }
            }
        }
    }

    fun readRecent(limit: Int = 300): List<DiagnosticEvent> = runCatching {
        eventFiles().flatMap { it.readLines().mapNotNull(::parseEvent) }.takeLast(limit)
    }.getOrDefault(emptyList())

    fun eventFiles(): List<File> = listOf(previousEventsFile, eventsFile).filter { it.exists() }

    fun clear() {
        writer.execute { runCatching { eventFiles().forEach(File::delete) } }
    }

    private fun rotateIfNeeded() {
        if (eventsFile.length() < MAX_BYTES) return
        previousEventsFile.delete()
        eventsFile.renameTo(previousEventsFile)
    }

    private fun writeEvent(json: JsonWriter, event: DiagnosticEvent) {
        json.beginObject()
        json.name("id").value(event.id)
        json.name("recordedAtMillis").value(event.recordedAtMillis)
        json.name("level").value(event.level.name)
        json.name("stage").value(event.stage.name)
        json.name("name").value(event.name)
        json.name("outcome").value(event.outcome)
        event.traceId?.let { json.name("traceId").value(it) }
        event.snapshotId?.let { json.name("snapshotId").value(it) }
        json.name("details").beginObject()
        event.details.forEach { (key, value) -> json.name(key).value(value) }
        json.endObject()
        json.endObject()
    }

    private fun parseEvent(line: String): DiagnosticEvent? = runCatching {
        var id = ""
        var recordedAt = 0L
        var level = DiagnosticLevel.INFO
        var stage = DiagnosticStage.DIAGNOSTICS
        var name = ""
        var outcome = "ok"
        var traceId: String? = null
        var snapshotId: String? = null
        val details = linkedMapOf<String, String>()
        JsonReader(line.reader()).use { reader ->
            reader.beginObject()
            while (reader.hasNext()) when (reader.nextName()) {
                "id" -> id = reader.nextString()
                "recordedAtMillis" -> recordedAt = reader.nextLong()
                "level" -> level = DiagnosticLevel.valueOf(reader.nextString())
                "stage" -> stage = DiagnosticStage.valueOf(reader.nextString())
                "name" -> name = reader.nextString()
                "outcome" -> outcome = reader.nextString()
                "traceId" -> traceId = reader.nextString()
                "snapshotId" -> snapshotId = reader.nextString()
                "details" -> {
                    reader.beginObject()
                    while (reader.hasNext()) details[reader.nextName()] = reader.nextString()
                    reader.endObject()
                }
                else -> reader.skipValue()
            }
            reader.endObject()
        }
        DiagnosticEvent(id, recordedAt, level, stage, name, outcome, traceId, snapshotId, details)
    }.getOrNull()

    private companion object {
        const val MAX_BYTES = 2L * 1024L * 1024L
    }
}
