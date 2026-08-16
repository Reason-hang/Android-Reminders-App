package com.reminder.local.ui.screen.edit

import com.reminder.local.domain.model.AdvanceReminderType
import com.reminder.local.domain.model.AdvanceReminderUnit
import com.reminder.local.domain.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditReminderHistoryTest {

    @Test
    fun undoAndRedoRestoreThePreviousFormSnapshots() {
        val history = EditReminderHistory()
        val initial = snapshot("初始")
        val changed = snapshot("修改后")

        history.reset(initial)
        history.record(changed)

        assertEquals(initial, history.undo())
        assertTrue(history.canRedo)
        assertEquals(changed, history.redo())
        assertFalse(history.canRedo)
    }

    @Test
    fun newEditAfterUndoClearsRedoHistory() {
        val history = EditReminderHistory()
        history.reset(snapshot("初始"))
        history.record(snapshot("第一次"))
        history.undo()
        history.record(snapshot("新的分支"))

        assertFalse(history.canRedo)
        assertNull(history.redo())
    }

    @Test
    fun identicalSnapshotDoesNotCreateAnUndoEntry() {
        val history = EditReminderHistory()
        val initial = snapshot("初始")
        history.reset(initial)
        history.record(initial)

        assertFalse(history.canUndo)
        assertNull(history.undo())
    }

    @Test
    fun consecutiveTextEditsInOneFieldUndoAsOneChange() {
        val history = EditReminderHistory()
        val initial = snapshot("初始")
        history.reset(initial)
        history.record(snapshot("初"), EditReminderChangeKind.TITLE_TEXT)
        history.record(snapshot("初始"), EditReminderChangeKind.TITLE_TEXT)

        assertEquals(initial, history.undo())
        assertNull(history.undo())
    }

    @Test
    fun historyIsBounded() {
        val history = EditReminderHistory(maxEntries = 2)
        history.reset(snapshot("0"))
        history.record(snapshot("1"))
        history.record(snapshot("2"))
        history.record(snapshot("3"))

        assertEquals("2", history.undo()?.title)
        assertEquals("1", history.undo()?.title)
        assertNull(history.undo())
    }

    private fun snapshot(title: String) = EditReminderSnapshot(
        title = title,
        note = "备注",
        triggerTime = 100L,
        categoryId = null,
        repeatType = RepeatType.NONE,
        repeatEndDate = null,
        advanceReminderType = AdvanceReminderType.NONE,
        customAdvanceValue = 1,
        customAdvanceUnit = AdvanceReminderUnit.HOURS,
        notifyVibrate = true,
        notifySound = true
    )
}
