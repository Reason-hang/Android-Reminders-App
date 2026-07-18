package com.reminder.local.service

object AlarmAlertConcurrencyPolicy {
    fun shouldRetainCurrent(currentAlarmId: Int?, incomingAlarmId: Int): Boolean =
        currentAlarmId != null && currentAlarmId != incomingAlarmId

    fun actionTargetsCurrent(currentAlarmId: Int?, actionAlarmId: Int): Boolean =
        currentAlarmId == null || currentAlarmId == actionAlarmId
}
