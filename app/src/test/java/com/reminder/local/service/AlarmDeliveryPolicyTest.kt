package com.reminder.local.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmDeliveryPolicyTest {

    @Test
    fun successfulForegroundAndPlaybackUseNormalStrongChannel() {
        val decision = AlarmDeliveryPolicy.decide(
            foregroundStarted = true,
            soundRequested = true,
            vibrateRequested = true,
            playback = AlarmPlaybackResult(soundStarted = true, vibrationStarted = true)
        )

        assertFalse(decision.useFallbackChannel)
        assertFalse(decision.fallbackSound)
        assertFalse(decision.fallbackVibration)
    }

    @Test
    fun foregroundFailureUsesConfiguredSoundAndVibrationFallback() {
        val decision = AlarmDeliveryPolicy.decide(
            foregroundStarted = false,
            soundRequested = true,
            vibrateRequested = true,
            playback = AlarmPlaybackResult(soundStarted = false, vibrationStarted = false)
        )

        assertTrue(decision.useFallbackChannel)
        assertTrue(decision.fallbackSound)
        assertTrue(decision.fallbackVibration)
    }

    @Test
    fun partialPlaybackFailureFallsBackOnlyForMissingModality() {
        val decision = AlarmDeliveryPolicy.decide(
            foregroundStarted = true,
            soundRequested = true,
            vibrateRequested = true,
            playback = AlarmPlaybackResult(soundStarted = true, vibrationStarted = false)
        )

        assertTrue(decision.useFallbackChannel)
        assertFalse(decision.fallbackSound)
        assertTrue(decision.fallbackVibration)
    }
}
