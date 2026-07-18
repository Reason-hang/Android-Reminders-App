package com.reminder.local.service

data class AlarmPlaybackResult(
    val soundStarted: Boolean,
    val vibrationStarted: Boolean
)

data class AlarmDeliveryDecision(
    val useFallbackChannel: Boolean,
    val fallbackSound: Boolean,
    val fallbackVibration: Boolean
)

/**
 * Keeps the strong-alert fallback decision independent from Android framework calls.
 */
object AlarmDeliveryPolicy {

    fun decide(
        foregroundStarted: Boolean,
        soundRequested: Boolean,
        vibrateRequested: Boolean,
        playback: AlarmPlaybackResult
    ): AlarmDeliveryDecision {
        val missingSound = soundRequested && (!foregroundStarted || !playback.soundStarted)
        val missingVibration = vibrateRequested &&
            (!foregroundStarted || !playback.vibrationStarted)

        return AlarmDeliveryDecision(
            useFallbackChannel = !foregroundStarted || missingSound || missingVibration,
            fallbackSound = missingSound,
            fallbackVibration = missingVibration
        )
    }
}
