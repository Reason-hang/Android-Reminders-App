package com.reminder.local.service

object AlarmAlertConcurrencyPolicy {
    fun shouldRetainCurrent(currentAlarmId: Int?, incomingAlarmId: Int): Boolean =
        currentAlarmId != null && currentAlarmId != incomingAlarmId

    fun shouldRestartPlayback(currentAlarmId: Int?): Boolean = currentAlarmId != null

    fun actionTargetsCurrent(
        current: AlarmAlertInstanceKey?,
        action: AlarmAlertInstanceKey
    ): Boolean = current == null || current == action
}

data class AlarmAlertInstanceKey(
    val alarmId: Int,
    val kind: AlarmAlertKind,
    val occurrenceTime: Long
)
