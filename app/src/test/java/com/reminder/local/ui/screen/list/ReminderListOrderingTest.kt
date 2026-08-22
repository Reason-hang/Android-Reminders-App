package com.reminder.local.ui.screen.list

import com.reminder.local.domain.model.Reminder
import com.reminder.local.domain.model.ReminderListSortMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderListOrderingTest {

    @Test
    fun timeModeUsesEffectiveTimeThenStableManualAndCreationTieBreakers() {
        val sameTime = listOf(
            reminder(id = 3, effectiveTime = 1_000L, manualSortOrder = 2, createdAt = 10),
            reminder(id = 1, effectiveTime = 1_000L, manualSortOrder = 1, createdAt = 20),
            reminder(id = 2, effectiveTime = 2_000L, manualSortOrder = 0, createdAt = 1)
        )

        assertEquals(listOf(1L, 3L, 2L), sortPendingReminders(sameTime, ReminderListSortMode.TIME).map { it.id })
    }

    @Test
    fun manualModeKeepsPersistedOrderAndUsesCreationAsTieBreaker() {
        val reminders = listOf(
            reminder(id = 2, effectiveTime = 1_000L, manualSortOrder = 0, createdAt = 20),
            reminder(id = 1, effectiveTime = 9_000L, manualSortOrder = 0, createdAt = 10),
            reminder(id = 3, effectiveTime = 2_000L, manualSortOrder = 2, createdAt = 1)
        )

        assertEquals(listOf(1L, 2L, 3L), sortPendingReminders(reminders, ReminderListSortMode.MANUAL).map { it.id })
    }

    @Test
    fun createdModeShowsNewestReminderFirst() {
        val reminders = listOf(
            reminder(id = 1, effectiveTime = 1_000L, manualSortOrder = 0, createdAt = 10),
            reminder(id = 3, effectiveTime = 2_000L, manualSortOrder = 0, createdAt = 30),
            reminder(id = 2, effectiveTime = 3_000L, manualSortOrder = 0, createdAt = 20)
        )

        assertEquals(listOf(3L, 2L, 1L), sortPendingReminders(reminders, ReminderListSortMode.CREATED).map { it.id })
    }

    private fun reminder(
        id: Long,
        effectiveTime: Long,
        manualSortOrder: Long,
        createdAt: Long
    ) = Reminder(
        id = id,
        title = "提醒$id",
        triggerTime = effectiveTime,
        createdAt = createdAt,
        updatedAt = createdAt,
        manualSortOrder = manualSortOrder
    )
}
