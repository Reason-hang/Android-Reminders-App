package com.reminder.local.data.db

import java.io.File
import com.reminder.local.domain.model.AdvanceReminderType
import com.reminder.local.domain.model.AdvanceReminderUnit
import com.reminder.local.domain.model.ReminderStatus
import com.reminder.local.domain.model.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun invalidStatusFallsBackToPending() {
        assertEquals(ReminderStatus.PENDING, safelyRead { converters.stringToStatus("BROKEN_STATUS") })
    }

    @Test
    fun invalidRepeatTypeFallsBackToNone() {
        assertEquals(RepeatType.NONE, safelyRead { converters.stringToRepeatType("BROKEN_REPEAT") })
    }

    @Test
    fun invalidAdvanceReminderTypeFallsBackToNone() {
        assertEquals(
            AdvanceReminderType.NONE,
            safelyRead { converters.stringToAdvanceReminderType("BROKEN_ADVANCE_TYPE") }
        )
    }

    @Test
    fun invalidAdvanceReminderUnitFallsBackToHours() {
        assertEquals(
            AdvanceReminderUnit.HOURS,
            safelyRead { converters.stringToAdvanceReminderUnit("BROKEN_ADVANCE_UNIT") }
        )
    }

    @Test
    fun validStatusesArePreserved() {
        assertEquals(
            ReminderStatus.entries.toList(),
            ReminderStatus.entries.map { converters.stringToStatus(it.name) }
        )
    }

    @Test
    fun validRepeatTypesArePreserved() {
        assertEquals(
            RepeatType.entries.toList(),
            RepeatType.entries.map { converters.stringToRepeatType(it.name) }
        )
    }

    @Test
    fun validAdvanceReminderTypesArePreserved() {
        assertEquals(
            AdvanceReminderType.entries.toList(),
            AdvanceReminderType.entries.map { converters.stringToAdvanceReminderType(it.name) }
        )
    }

    @Test
    fun validAdvanceReminderUnitsArePreserved() {
        assertEquals(
            AdvanceReminderUnit.entries.toList(),
            AdvanceReminderUnit.entries.map { converters.stringToAdvanceReminderUnit(it.name) }
        )
    }

    @Test
    fun convertersOnlyRecoverFromInvalidEnumNames() {
        val source = File("src/main/java/com/reminder/local/data/db/Converters.kt").readText()

        assertFalse(source.contains("runCatching"))
        assertEquals(
            4,
            Regex("catch \\(_:\\s*IllegalArgumentException\\)").findAll(source).count()
        )
    }

    private fun <T> safelyRead(read: () -> T): T? = runCatching(read).getOrNull()
}
