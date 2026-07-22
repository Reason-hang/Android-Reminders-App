package com.reminder.local.receiver

object AlarmTriggerPolicy {
    fun shouldStartStrongAlert(kind: String): Boolean =
        kind == AlarmReceiver.KIND_DUE ||
            kind == AlarmReceiver.KIND_ADVANCE ||
            kind == AlarmReceiver.KIND_SNOOZE

    fun shouldProgressRepeatingReminder(kind: String): Boolean =
        kind == AlarmReceiver.KIND_DUE

    fun shouldUseNoisyFallback(kind: String): Boolean = shouldStartStrongAlert(kind)
}
