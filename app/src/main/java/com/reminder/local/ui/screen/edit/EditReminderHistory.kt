package com.reminder.local.ui.screen.edit

/**
 * 编辑页一次会话内的可编辑字段快照。
 * 保存后由 ViewModel 重置历史，不把历史写入提醒数据或数据库。
 */
internal data class EditReminderSnapshot(
    val title: String,
    val note: String,
    val triggerTime: Long,
    val categoryId: Long?,
    val repeatType: com.reminder.local.domain.model.RepeatType,
    val repeatEndDate: Long?,
    val advanceReminderType: com.reminder.local.domain.model.AdvanceReminderType,
    val customAdvanceValue: Int,
    val customAdvanceUnit: com.reminder.local.domain.model.AdvanceReminderUnit,
    val notifyVibrate: Boolean,
    val notifySound: Boolean
)

internal enum class EditReminderChangeKind {
    FORM,
    TITLE_TEXT,
    NOTE_TEXT
}

/** 有界、单向的撤销/重做历史；新编辑会清空重做栈。 */
internal class EditReminderHistory(
    private val maxEntries: Int = 50
) {
    private val undoStack = ArrayDeque<EditReminderSnapshot>()
    private val redoStack = ArrayDeque<EditReminderSnapshot>()
    private var current: EditReminderSnapshot? = null
    private var lastChangeKind: EditReminderChangeKind? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    fun reset(snapshot: EditReminderSnapshot) {
        current = snapshot
        undoStack.clear()
        redoStack.clear()
        lastChangeKind = null
    }

    fun record(snapshot: EditReminderSnapshot, kind: EditReminderChangeKind = EditReminderChangeKind.FORM) {
        if (current == snapshot) return
        if (kind == EditReminderChangeKind.FORM || kind != lastChangeKind) {
            current?.let { undoStack.addLast(it) }
            while (undoStack.size > maxEntries) undoStack.removeFirst()
        }
        current = snapshot
        redoStack.clear()
        lastChangeKind = kind
    }

    fun undo(): EditReminderSnapshot? {
        if (undoStack.isEmpty()) return null
        current?.let { redoStack.addLast(it) }
        current = undoStack.removeLast()
        lastChangeKind = null
        return current
    }

    fun redo(): EditReminderSnapshot? {
        if (redoStack.isEmpty()) return null
        current?.let { undoStack.addLast(it) }
        current = redoStack.removeLast()
        lastChangeKind = null
        return current
    }
}
