package com.example.eece4081_iap.domain

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * How long the timer waits between Start and the start beep.
 *
 * Sealed so the two modes in REQUIREMENTS.md, Fixed ([FixedDelay]) and
 * Random Range ([RandomDelay]), cannot be mixed (US01).
 */
sealed interface StartDelay {

    /**
     * The delay to wait for one session (US03). [random] is used only by
     * [RandomDelay]; it is passed in so tests can control the choice.
     */
    fun chooseDuration(random: Random): Duration
}

/** Waits the same [duration] every session. */
data class FixedDelay(val duration: Duration) : StartDelay {
    init {
        require(!duration.isNegative()) { "duration must not be negative, was $duration" }
    }

    override fun chooseDuration(random: Random): Duration = duration
}

/**
 * Waits a different, unpredictable time each session, chosen evenly within
 * [minDelay]..[maxDelay] (both included), so the shooter cannot anticipate
 * the beep (US03). The minimum may not exceed the maximum (US01); they may be
 * equal, which behaves like a fixed delay.
 */
data class RandomDelay(val minDelay: Duration, val maxDelay: Duration) : StartDelay {
    init {
        require(!minDelay.isNegative()) { "minDelay must not be negative, was $minDelay" }
        require(minDelay <= maxDelay) { "minDelay ($minDelay) must not exceed maxDelay ($maxDelay)" }
    }

    override fun chooseDuration(random: Random): Duration {
        val min = minDelay.inWholeNanoseconds
        val max = maxDelay.inWholeNanoseconds
        if (min == max) return minDelay
        return random.nextLong(min, max + 1).nanoseconds
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
        /**
         * Defaults until the person changes them on the Settings screen.
         *
         * The 80 ms cooldown matches the echo filter of other phone shot
         * timers and allows fast double-taps (splits under 0.2 s). Clap echoes
         * captured on a test phone arrived 2–44 ms after the clap, so 80 ms
         * still rejects them. The threshold is still a placeholder; tune it
         * against real gunshots.
         */
        val DEFAULT = TimerSettings(
            detectionThreshold = 0.5,
            detectionCooldown = 80.milliseconds,
            startDelay = FixedDelay(3.seconds),
        )
    }
}
