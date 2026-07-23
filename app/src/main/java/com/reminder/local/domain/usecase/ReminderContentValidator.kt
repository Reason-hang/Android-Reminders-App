package com.reminder.local.domain.usecase

object ReminderContentValidator {
    const val TITLE_MAX_LENGTH = 50
    const val NOTE_MAX_LENGTH = 200

    fun validate(title: String, note: String?): String? = when {
        title.length > TITLE_MAX_LENGTH -> "标题最多 $TITLE_MAX_LENGTH 个字符"
        note != null && note.length > NOTE_MAX_LENGTH -> "备注最多 $NOTE_MAX_LENGTH 个字符"
        else -> null
    }
}
