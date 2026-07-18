package com.reminder.local.service

enum class AlarmAlertAction {
    CLOSE,
    SNOOZE,
    MARK_DONE
}

object AlarmAlertInteractionPolicy {
    fun shouldKeepNotification(action: AlarmAlertAction): Boolean =
        action == AlarmAlertAction.CLOSE
}
