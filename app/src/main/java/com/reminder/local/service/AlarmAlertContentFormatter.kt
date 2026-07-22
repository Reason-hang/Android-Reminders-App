package com.reminder.local.service

data class AlarmAlertContent(
    val title: String,
    val previewText: String
)

object AlarmAlertContentFormatter {
    fun format(
        title: String?,
        note: String?,
        kind: AlarmAlertKind = AlarmAlertKind.DUE
    ): AlarmAlertContent {
        val baseTitle = title?.trim().orEmpty().ifBlank { "提醒事项" }
        val displayTitle = when (kind) {
            AlarmAlertKind.DUE -> baseTitle
            AlarmAlertKind.ADVANCE -> "提前提醒：$baseTitle"
            AlarmAlertKind.SNOOZE -> "稍后提醒：$baseTitle"
        }

        return AlarmAlertContent(
            title = displayTitle,
            previewText = note?.trim().orEmpty().ifBlank { "提醒时间到了" }
        )
    }
}

enum class AlarmAlertKind {
    DUE,
    ADVANCE,
    SNOOZE;

    companion object {
        fun fromWireValue(value: String?): AlarmAlertKind =
            entries.firstOrNull { it.name == value } ?: DUE
    }
}
