package com.example.eece4081_iap.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TimerSettingsTest {

    @Test
    fun defaultSettings_useAFixedStartDelay() {
        assertTrue(TimerSettings.DEFAULT.startDelay is FixedDelay)
    }

    @Test
    fun validSettings_areAccepted() {
        val settings = TimerSettings(
            detectionThreshold = 0.8,
            detectionCooldown = 150.milliseconds,
            startDelay = FixedDelay(2.seconds),
        )

        assertEquals(0.8, settings.detectionThreshold, 0.0)
        assertEquals(150.milliseconds, settings.detectionCooldown)
        assertEquals(FixedDelay(2.seconds), settings.startDelay)
    }

    @Test
    fun thresholdOfExactlyOne_isAccepted() {
        TimerSettings(1.0, 0.milliseconds, FixedDelay(0.seconds))
    }

    @Test(expected = IllegalArgumentException::class)
    fun thresholdOfZero_isRejected() {
        TimerSettings(0.0, 200.milliseconds, FixedDelay(1.seconds))
    }

    @Test(expected = IllegalArgumentException::class)
    fun thresholdAboveOne_isRejected() {
        TimerSettings(1.01, 200.milliseconds, FixedDelay(1.seconds))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeCooldown_isRejected() {
        TimerSettings(0.5, (-1).milliseconds, FixedDelay(1.seconds))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeFixedDelay_isRejected() {
        FixedDelay((-1).seconds)
    }
}
