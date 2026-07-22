package com.reminder.local.service

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmAlertContentFormatterTest {

    @Test
    fun usesReminderTitleAndNoteForLockscreenPreview() {
        val content = AlarmAlertContentFormatter.format(
            title = "还信用卡",
            note = "招商银行 23:00 前处理",
            kind = AlarmAlertKind.DUE
        )

        assertEquals("还信用卡", content.title)
        assertEquals("招商银行 23:00 前处理", content.previewText)
    }

    @Test
    fun prefixesAdvanceAlertsForLockscreenPreview() {
        val content = AlarmAlertContentFormatter.format(
            title = "还信用卡",
            note = "招商银行 23:00 前处理",
            kind = AlarmAlertKind.ADVANCE
        )

        assertEquals("提前提醒：还信用卡", content.title)
        assertEquals("招商银行 23:00 前处理", content.previewText)
    }

    @Test
    fun prefixesSnoozedAlertsForLockscreenPreview() {
        val content = AlarmAlertContentFormatter.format(
            title = "还信用卡",
            note = null,
            kind = AlarmAlertKind.SNOOZE
        )

        assertEquals("稍后提醒：还信用卡", content.title)
        assertEquals("提醒时间到了", content.previewText)
    }

    @Test
    fun fallsBackToReadableTextWhenTitleOrNoteIsBlank() {
        val content = AlarmAlertContentFormatter.format(
            title = " ",
            note = "",
            kind = AlarmAlertKind.DUE
        )

        assertEquals("提醒事项", content.title)
        assertEquals("提醒时间到了", content.previewText)
    }
}
