package com.reminder.local.service

/**
 * PendingIntent extras are not part of Android's identity comparison.
 * These stable URIs keep different reminders and occurrences independent.
 */
object AlarmIntentIdentity {
    fun trigger(reminderId: Long, kind: String): String =
        "reminder://alarm/trigger/$reminderId/$kind"

    fun show(reminderId: Long, kind: String): String =
        "reminder://alarm/show/$reminderId/$kind"

    fun alert(reminderId: Long, kind: AlarmAlertKind): String =
        "reminder://alarm/alert/$reminderId/${kind.name}"

    fun action(
        reminderId: Long,
        alarmId: Int,
        kind: AlarmAlertKind,
        occurrenceTime: Long,
        action: String
    ): String =
        "reminder://alarm/action/$reminderId/$alarmId/${kind.name}/$occurrenceTime/$action"
}
