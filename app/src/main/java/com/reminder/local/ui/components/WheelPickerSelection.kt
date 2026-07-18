package com.reminder.local.ui.components

data class WheelItemPosition(
    val index: Int,
    val offset: Int,
    val size: Int
)

object WheelPickerSelection {
    fun nearestIndex(
        viewportStartOffset: Int,
        viewportEndOffset: Int,
        items: List<WheelItemPosition>
    ): Int? {
        if (items.isEmpty()) return null
        val viewportCenter = (viewportStartOffset + viewportEndOffset) / 2
        return items.minByOrNull { item ->
            kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
        }?.index
    }
}
