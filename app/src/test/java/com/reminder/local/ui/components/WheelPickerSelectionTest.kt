package com.reminder.local.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class WheelPickerSelectionTest {

    @Test
    fun choosesItemNearestViewportCenter() {
        val items = listOf(
            WheelItemPosition(index = 8, offset = -20, size = 56),
            WheelItemPosition(index = 9, offset = 36, size = 56),
            WheelItemPosition(index = 10, offset = 92, size = 56),
            WheelItemPosition(index = 11, offset = 148, size = 56),
            WheelItemPosition(index = 12, offset = 204, size = 56)
        )

        assertEquals(
            10,
            WheelPickerSelection.nearestIndex(
                viewportStartOffset = 0,
                viewportEndOffset = 280,
                items = items
            )
        )
    }

    @Test
    fun doesNotTreatPartiallyVisibleFirstItemAsSelected() {
        val items = listOf(
            WheelItemPosition(index = 20, offset = -40, size = 56),
            WheelItemPosition(index = 21, offset = 16, size = 56),
            WheelItemPosition(index = 22, offset = 72, size = 56),
            WheelItemPosition(index = 23, offset = 128, size = 56),
            WheelItemPosition(index = 24, offset = 184, size = 56),
            WheelItemPosition(index = 25, offset = 240, size = 56)
        )

        assertEquals(
            23,
            WheelPickerSelection.nearestIndex(
                viewportStartOffset = 0,
                viewportEndOffset = 280,
                items = items
            )
        )
    }
}
