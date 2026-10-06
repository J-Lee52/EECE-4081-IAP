package com.example.eece4081_iap.audio

import kotlin.math.abs
import kotlin.time.Duration

/** A detected shot. [timestamp] is the time value that was passed to [ShotDetector.process]. */
data class ShotEvent(val timestamp: Duration)

/**
 * Decides whether buffers of microphone samples contain a shot (ADR-001).
 *
 * A buffer counts as a shot when its peak amplitude, normalized to 0.0..1.0,
 * meets [threshold] and at least [cooldown] has passed since the previous
 * accepted shot. Pure Kotlin: no Android types, so it can be tested with
 * hand-built sample buffers.
 *
 * One instance is used per session, because the settings cannot change while
 * a session is active.
 */
class ShotDetector(
    private val threshold: Double,
    private val cooldown: Duration,
) {
    init {
        require(threshold > 0.0 && threshold <= 1.0) {
            "threshold must be in (0.0, 1.0], was $threshold"
        }
        require(!cooldown.isNegative()) { "cooldown must not be negative, was $cooldown" }
    }

    private var lastShotAt: Duration? = null

    /**
     * Examines one buffer captured at time [at] (any monotonic time base,
     * as long as it is the same for every call). Returns a [ShotEvent] if the
     * buffer is a shot, or null if it is too quiet or inside the cooldown.
     */
    fun process(samples: ShortArray, at: Duration): ShotEvent? {
        if (peakAmplitude(samples) < threshold) return null

        val previous = lastShotAt
        if (previous != null && at - previous < cooldown) return null

        lastShotAt = at
        return ShotEvent(at)
    }

    companion object {
        private const val FULL_SCALE = 32768.0

        /** Largest absolute sample value in [samples], divided by full scale (0.0..1.0). */
        fun peakAmplitude(samples: ShortArray): Double {
            var peak = 0
            for (sample in samples) {
                val magnitude = abs(sample.toInt())
                if (magnitude > peak) peak = magnitude
            }
            return peak / FULL_SCALE
        }
    }
}
