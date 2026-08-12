package com.reminder.local.diagnostics.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertTraceParserTest {
    @Test
    fun `only a full screen notification source confirms automatic full screen lifecycle`() {
        val summary = AlertTraceParser.summarizeOne("trace", listOf(
            event(DiagnosticEventName.RECEIVER_ENTERED),
            event(DiagnosticEventName.ALERT_NOTIFICATION_POSTED),
            event(DiagnosticEventName.FULL_SCREEN_REQUESTED),
            event(
                DiagnosticEventName.ACTIVITY_CREATED,
                details = mapOf("launchSource" to "notification_full_screen")
            ),
            event(DiagnosticEventName.ACTIVITY_RESUMED)
        ))

        assertEquals(DiagnosticEvidence.CONFIRMED, summary.evidence)
        assertTrue(summary.conclusion.contains("全屏通知"))
    }

    @Test
    fun `notification click lifecycle never proves automatic full screen`() {
        val summary = AlertTraceParser.summarizeOne("trace", listOf(
            event(
                DiagnosticEventName.ACTIVITY_CREATED,
                details = mapOf("launchSource" to "alert_notification")
            ),
            event(DiagnosticEventName.ACTIVITY_RESUMED)
        ))

        assertEquals(DiagnosticEvidence.CONFIRMED, summary.evidence)
        assertTrue(summary.conclusion.contains("用户点击"))
        assertTrue(summary.nextAction.contains("不能证明"))
    }

    @Test
    fun `old lifecycle record without source is not treated as full screen proof`() {
        val summary = AlertTraceParser.summarizeOne("trace", listOf(
            event(DiagnosticEventName.ACTIVITY_CREATED),
            event(DiagnosticEventName.ACTIVITY_RESUMED)
        ))

        assertEquals(DiagnosticEvidence.INSUFFICIENT, summary.evidence)
        assertTrue(summary.conclusion.contains("未标明启动来源"))
    }

    @Test
    fun `full screen request without lifecycle is only likely and never confirmed`() {
        val summary = AlertTraceParser.summarizeOne("trace", listOf(
            event(DiagnosticEventName.ALERT_NOTIFICATION_POSTED),
            event(DiagnosticEventName.FULL_SCREEN_REQUESTED)
        ))

        assertEquals(DiagnosticEvidence.LIKELY, summary.evidence)
        assertTrue(summary.nextAction.contains("全屏通知权限"))
    }

    @Test
    fun `scheduled alarm without receiver is not reported as a broken alert chain`() {
        val summary = AlertTraceParser.summarizeOne("trace", listOf(
            event(DiagnosticEventName.ALARM_SCHEDULED)
        ))

        assertEquals(DiagnosticEvidence.INSUFFICIENT, summary.evidence)
        assertTrue(summary.conclusion.contains("已登记"))
        assertTrue(!summary.conclusion.contains("中断"))
    }

    @Test
    fun `preempted alert is not misdiagnosed as a timeout`() {
        val summary = AlertTraceParser.summarizeOne("trace", listOf(
            event(DiagnosticEventName.ALERT_NOTIFICATION_POSTED),
            event(DiagnosticEventName.ALERT_PREEMPTED)
        ))

        assertEquals(DiagnosticEvidence.CONFIRMED, summary.evidence)
        assertTrue(summary.conclusion.contains("后续提醒接管"))
        assertTrue(summary.nextAction.contains("不是十分钟自动结束"))
    }

    @Test
    fun `failed launch is confirmed application side failure`() {
        val summary = AlertTraceParser.summarizeOne("trace", listOf(event(DiagnosticEventName.FULL_SCREEN_REQUEST_FAILED)))

        assertEquals(DiagnosticEvidence.CONFIRMED, summary.evidence)
    }

    @Test
    fun `overlay window attachment is confirmed unlocked strong alert evidence`() {
        val summary = AlertTraceParser.summarizeOne(
            "trace",
            listOf(
                event(DiagnosticEventName.OVERLAY_REQUESTED),
                event(DiagnosticEventName.OVERLAY_SHOWN)
            )
        )

        assertEquals(DiagnosticEvidence.CONFIRMED, summary.evidence)
        assertTrue(summary.conclusion.contains("悬浮页"))
    }

    private fun event(name: String, details: Map<String, String> = emptyMap()) = DiagnosticEvent(
        id = name,
        recordedAtMillis = 1L,
        level = DiagnosticLevel.INFO,
        stage = DiagnosticStage.FULL_SCREEN,
        name = name,
        traceId = "trace",
        details = details
    )
}
