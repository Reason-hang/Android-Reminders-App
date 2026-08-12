package com.reminder.local.service

enum class AlarmVisualRoute {
    SYSTEM_FULL_SCREEN,
    APPLICATION_OVERLAY,
    SYSTEM_NOTIFICATION_FALLBACK
}

/** 锁屏由系统全屏通知负责；解锁态由应用悬浮层接管，避免被 SystemUI 降级为横幅。 */
object AlarmVisualRoutePolicy {
    fun decide(
        interactive: Boolean,
        keyguardLocked: Boolean,
        overlayAllowed: Boolean
    ): AlarmVisualRoute = when {
        !interactive || keyguardLocked -> AlarmVisualRoute.SYSTEM_FULL_SCREEN
        overlayAllowed -> AlarmVisualRoute.APPLICATION_OVERLAY
        else -> AlarmVisualRoute.SYSTEM_NOTIFICATION_FALLBACK
    }
}
