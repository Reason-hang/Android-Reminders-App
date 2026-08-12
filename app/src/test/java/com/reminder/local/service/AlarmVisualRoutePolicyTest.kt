package com.reminder.local.service

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmVisualRoutePolicyTest {

    @Test
    fun unlockedInteractiveDeviceUsesApplicationOverlayWhenGranted() {
        assertEquals(
            AlarmVisualRoute.APPLICATION_OVERLAY,
            AlarmVisualRoutePolicy.decide(
                interactive = true,
                keyguardLocked = false,
                overlayAllowed = true
            )
        )
    }

    @Test
    fun lockedDeviceKeepsSystemFullScreenIntentRoute() {
        assertEquals(
            AlarmVisualRoute.SYSTEM_FULL_SCREEN,
            AlarmVisualRoutePolicy.decide(
                interactive = true,
                keyguardLocked = true,
                overlayAllowed = true
            )
        )
    }

    @Test
    fun unlockedDeviceWithoutOverlayPermissionFallsBackToSystemNotification() {
        assertEquals(
            AlarmVisualRoute.SYSTEM_NOTIFICATION_FALLBACK,
            AlarmVisualRoutePolicy.decide(
                interactive = true,
                keyguardLocked = false,
                overlayAllowed = false
            )
        )
    }
}
