package com.reminder.local.diagnostics.core

enum class DiagnosticEvidence { CONFIRMED, LIKELY, INSUFFICIENT }

data class DiagnosticTraceSummary(
    val traceId: String,
    val startedAtMillis: Long,
    val lastEventAtMillis: Long,
    val evidence: DiagnosticEvidence,
    val conclusion: String,
    val nextAction: String,
    val events: List<DiagnosticEvent>
)

/** 纯函数：只根据本应用已记录的事实输出结论，不把 SystemUI 行为伪装成可确认事实。 */
object AlertTraceParser {
    fun summarize(events: List<DiagnosticEvent>): List<DiagnosticTraceSummary> = events
        .filter { !it.traceId.isNullOrBlank() }
        .groupBy { it.traceId.orEmpty() }
        .map { (traceId, traceEvents) -> summarizeOne(traceId, traceEvents.sortedBy { it.recordedAtMillis }) }
        .sortedByDescending { it.startedAtMillis }

    fun summarizeOne(traceId: String, events: List<DiagnosticEvent>): DiagnosticTraceSummary {
        val names = events.map { it.name }.toSet()
        val has = { name: String -> name in names }
        val launchSources = events
            .filter {
                it.name == DiagnosticEventName.ACTIVITY_CREATED ||
                    it.name == DiagnosticEventName.ACTIVITY_NEW_INTENT
            }
            .mapNotNull { it.details["launchSource"] }
            .toSet()
        val activityReachedForeground =
            has(DiagnosticEventName.ACTIVITY_RESUMED) || has(DiagnosticEventName.ACTIVITY_FOCUSED)
        val openedByFullScreenNotification =
            "notification_full_screen" in launchSources && activityReachedForeground
        val openedByNotificationTap =
            "alert_notification" in launchSources && activityReachedForeground
        val started = events.firstOrNull()?.recordedAtMillis ?: 0L
        val last = events.lastOrNull()?.recordedAtMillis ?: started
        val (evidence, conclusion, nextAction) = when {
            has(DiagnosticEventName.FULL_SCREEN_REQUEST_FAILED) -> Triple(
                DiagnosticEvidence.CONFIRMED,
                "全屏页启动请求在应用侧失败。",
                "检查导出包中的失败阶段和系统权限快照。"
            )
            has(DiagnosticEventName.ALERT_PREEMPTED) -> Triple(
                DiagnosticEvidence.CONFIRMED,
                "该强提醒已被后续提醒接管，并保留为静音回看通知。",
                "这是并发提醒切换，不是十分钟自动结束；查看事件详情中的接管实例。"
            )
            openedByFullScreenNotification -> Triple(
                DiagnosticEvidence.CONFIRMED,
                "系统全屏通知已把提醒页带入应用前台生命周期。",
                "若仍只看到横幅，请结合录屏和系统通知设置排查 SystemUI 呈现。"
            )
            openedByNotificationTap -> Triple(
                DiagnosticEvidence.CONFIRMED,
                "用户点击强提醒通知后，提醒页已进入应用前台生命周期。",
                "该证据不能证明系统曾自动展示全屏页。"
            )
            activityReachedForeground -> Triple(
                DiagnosticEvidence.INSUFFICIENT,
                "提醒页已进入应用前台生命周期，但旧记录未标明启动来源。",
                "升级后重新复现；新版会区分系统全屏、通知点击和闹钟图标入口。"
            )
            has(DiagnosticEventName.FULL_SCREEN_REQUESTED) && has(DiagnosticEventName.ALERT_NOTIFICATION_POSTED) -> Triple(
                DiagnosticEvidence.LIKELY,
                "应用已发布强提醒通知并请求全屏页，但没有收到页面恢复证据。",
                "优先核对全屏通知权限、锁屏显示、悬浮窗和厂商后台策略；系统界面是否实际展示无法由应用单独确认。"
            )
            has(DiagnosticEventName.ALARM_SCHEDULED) && !has(DiagnosticEventName.RECEIVER_ENTERED) -> Triple(
                DiagnosticEvidence.INSUFFICIENT,
                "提醒已登记，尚未收到到点接收器事件。",
                "这不是已触发后的链路中断；若当前时间已超过 triggerAt，核对精确闹钟权限和接收器事件。"
            )
            has(DiagnosticEventName.FOREGROUND_SERVICE_STARTED) -> Triple(
                DiagnosticEvidence.INSUFFICIENT,
                "强提醒服务已启动，但通知或全屏链路证据不完整。",
                "查看后续事件是否缺失；若重复出现，导出诊断包。"
            )
            else -> Triple(
                DiagnosticEvidence.INSUFFICIENT,
                "提醒链路在到达强提醒服务前中断或记录不完整。",
                "核对精确闹钟权限、触发时间和接收器事件。"
            )
        }
        return DiagnosticTraceSummary(traceId, started, last, evidence, conclusion, nextAction, events)
    }
}
