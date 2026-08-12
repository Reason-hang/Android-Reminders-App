package com.reminder.local.ui.components

data class WheelItemPosition(
    val index: Int,
    val offset: Int,
    val size: Int
)

object WheelPickerSelection {
    const val CYCLIC_ITEM_COUNT = Int.MAX_VALUE

    fun cyclicInitialIndex(selectedIndex: Int, valueCount: Int): Int {
        require(valueCount > 0)
        require(selectedIndex in 0 until valueCount)
        val middle = CYCLIC_ITEM_COUNT / 2
        return middle - (middle % valueCount) + selectedIndex
    }

    fun cyclicValueIndex(virtualIndex: Int, valueCount: Int): Int {
        require(valueCount > 0)
        return Math.floorMod(virtualIndex, valueCount)
    }

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
