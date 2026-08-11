package com.reminder.local.diagnostics.core

/** 不包含提醒正文的、可持久化的诊断事件。 */
data class DiagnosticEvent(
    val id: String,
    val recordedAtMillis: Long,
    val level: DiagnosticLevel,
    val stage: DiagnosticStage,
    val name: String,
    val outcome: String = "ok",
    val traceId: String? = null,
    val snapshotId: String? = null,
    val details: Map<String, String> = emptyMap()
)

enum class DiagnosticLevel { INFO, WARN, ERROR }

enum class DiagnosticStage {
    SCHEDULER,
    RECEIVER,
    FOREGROUND_SERVICE,
    NOTIFICATION,
    FULL_SCREEN,
    ACTIVITY,
    PLAYBACK,
    TIMER,
    USER_ACTION,
    SYSTEM,
    DIAGNOSTICS
}

object DiagnosticEventName {
    const val ALARM_SCHEDULED = "alarm_scheduled"
    const val ALARM_SCHEDULE_FAILED = "alarm_schedule_failed"
    const val RECEIVER_ENTERED = "receiver_entered"
    const val RECEIVER_STALE = "receiver_stale"
    const val FOREGROUND_SERVICE_REQUESTED = "foreground_service_requested"
    const val FOREGROUND_SERVICE_STARTED = "foreground_service_started"
    const val FOREGROUND_SERVICE_FAILED = "foreground_service_failed"
    const val ALERT_SESSION_STARTED = "alert_session_started"
    const val ALERT_PREEMPTED = "alert_preempted"
    const val ALERT_NOTIFICATION_REQUESTED = "alert_notification_requested"
    const val ALERT_NOTIFICATION_POSTED = "alert_notification_posted"
    const val FULL_SCREEN_REQUESTED = "full_screen_requested"
    const val FULL_SCREEN_REQUEST_FAILED = "full_screen_request_failed"
    const val ACTIVITY_CREATED = "activity_created"
    const val ACTIVITY_NEW_INTENT = "activity_new_intent"
    const val ACTIVITY_RESUMED = "activity_resumed"
    const val ACTIVITY_FOCUSED = "activity_focused"
    const val PLAYBACK_STARTED = "playback_started"
    const val TIMER_SCHEDULED = "timer_scheduled"
    const val TIMER_EXPIRED = "timer_expired"
    const val TIMER_STALE_IGNORED = "timer_stale_ignored"
    const val ALERT_CLOSED = "alert_closed"
}

/**
 * 仅使用稳定标识，故意不纳入标题、备注等用户内容；同一提醒实例在各链路具有同一 traceId。
 */
object DiagnosticTraceId {
    fun alert(reminderId: Long, alarmId: Int, kind: String, occurrenceTime: Long): String =
        "a:$reminderId:$alarmId:${kind.lowercase()}:$occurrenceTime"
}

object DiagnosticRedactor {
    fun safeDetails(raw: Map<String, Any?>): Map<String, String> = raw.mapNotNull { (key, value) ->
        when {
            key.contains("title", ignoreCase = true) ||
                key.contains("note", ignoreCase = true) ||
                key.contains("message", ignoreCase = true) ||
                key.contains("stack", ignoreCase = true) ||
                key.contains("uri", ignoreCase = true) -> null
            value == null -> null
            value is String && value.length > 120 -> key to "[redacted-long-value]"
            else -> key to value.toString().take(120)
        }
    }.toMap()
}
