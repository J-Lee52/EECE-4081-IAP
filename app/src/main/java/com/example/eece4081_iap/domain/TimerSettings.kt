package com.example.eece4081_iap.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * How long the timer waits between Start and the start beep.
 *
 * Sealed so the two modes in REQUIREMENTS.md (Fixed and Random Range) cannot
 * be mixed. Only [FixedDelay] exists for the walking skeleton; a RandomDelay
 * with minDelay/maxDelay is added later.
 */
sealed interface StartDelay

data class FixedDelay(val duration: Duration) : StartDelay {
    init {
        require(!duration.isNegative()) { "duration must not be negative, was $duration" }
    }
}

/**
 * Session configuration chosen while the timer is Idle or Completed.
 *
 * [detectionThreshold] is a normalized peak amplitude in the range (0.0, 1.0].
 * [detectionCooldown] is the time after an accepted shot during which further
 * microphone events are ignored.
 */
data class TimerSettings(
    val detectionThreshold: Double,
    val detectionCooldown: Duration,
    val startDelay: StartDelay,
) {
    init {
        require(detectionThreshold > 0.0 && detectionThreshold <= 1.0) {
            "detectionThreshold must be in (0.0, 1.0], was $detectionThreshold"
        }
        require(!detectionCooldown.isNegative()) {
            "detectionCooldown must not be negative, was $detectionCooldown"
        }
    }

    companion object {
        /** Placeholder defaults; tune against real microphone input. */
        val DEFAULT = TimerSettings(
            detectionThreshold = 0.5,
            detectionCooldown = 200.milliseconds,
            startDelay = FixedDelay(3.seconds),
        )
    }
}
