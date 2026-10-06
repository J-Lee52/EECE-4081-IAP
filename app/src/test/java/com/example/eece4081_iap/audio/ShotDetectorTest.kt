package com.example.eece4081_iap.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ShotDetectorTest {

    // Threshold 0.5 means a peak sample of 16384 (0.5 x 32768).
    private fun newDetector() = ShotDetector(threshold = 0.5, cooldown = 200.milliseconds)

    /** A buffer whose largest absolute sample is [peak]. */
    private fun frame(peak: Int) = shortArrayOf(100, peak.toShort(), -50)

    @Test
    fun quietBuffer_isNotAShot() {
        assertNull(newDetector().process(frame(1000), 1.seconds))
    }

    @Test
    fun bufferJustBelowThreshold_isNotAShot() {
        assertNull(newDetector().process(frame(16383), 1.seconds))
    }

    @Test
    fun bufferExactlyAtThreshold_isAShot() {
        val event = newDetector().process(frame(16384), 1.seconds)

        assertNotNull(event)
        assertEquals(1.seconds, event!!.timestamp)
    }

    @Test
    fun loudBuffer_isAShotAtTheGivenTime() {
        val event = newDetector().process(frame(30000), 2500.milliseconds)

        assertEquals(ShotEvent(2500.milliseconds), event)
    }

    @Test
    fun negativePeak_countsTowardTheThreshold() {
        val event = newDetector().process(shortArrayOf(0, (-20000).toShort(), 0), 1.seconds)

        assertNotNull(event)
    }

    @Test
    fun emptyBuffer_isNotAShot() {
        assertNull(newDetector().process(shortArrayOf(), 1.seconds))
    }

    @Test
    fun loudBufferInsideCooldown_isIgnored() {
        val detector = newDetector()
        detector.process(frame(30000), 1000.milliseconds)

        assertNull(detector.process(frame(30000), 1100.milliseconds))
    }

    @Test
    fun loudBufferAfterCooldown_isAShot() {
        val detector = newDetector()
        detector.process(frame(30000), 1000.milliseconds)

        val second = detector.process(frame(30000), 1250.milliseconds)

        assertEquals(ShotEvent(1250.milliseconds), second)
    }

    @Test
    fun bufferExactlyOneCooldownLater_isAShot() {
        val detector = newDetector()
        detector.process(frame(30000), 1000.milliseconds)

        assertNotNull(detector.process(frame(30000), 1200.milliseconds))
    }

    @Test
    fun ignoredBufferDoesNotRestartTheCooldown() {
        val detector = newDetector()
        detector.process(frame(30000), 1000.milliseconds)
        detector.process(frame(30000), 1150.milliseconds) // ignored

        // 1250 is 250 ms after the accepted shot, so it counts even though
        // it is only 100 ms after the ignored buffer.
        assertNotNull(detector.process(frame(30000), 1250.milliseconds))
    }

    @Test
    fun quietBufferDoesNotStartACooldown() {
        val detector = newDetector()
        detector.process(frame(1000), 1000.milliseconds)

        assertNotNull(detector.process(frame(30000), 1050.milliseconds))
    }

    @Test
    fun peakAmplitude_isNormalizedToFullScale() {
        assertEquals(0.5, ShotDetector.peakAmplitude(shortArrayOf(0, 16384, 0)), 0.0)
        assertEquals(1.0, ShotDetector.peakAmplitude(shortArrayOf(Short.MIN_VALUE)), 0.0)
        assertEquals(0.0, ShotDetector.peakAmplitude(shortArrayOf()), 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun thresholdOfZero_isRejected() {
        ShotDetector(threshold = 0.0, cooldown = 200.milliseconds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun thresholdAboveOne_isRejected() {
        ShotDetector(threshold = 1.5, cooldown = 200.milliseconds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeCooldown_isRejected() {
        ShotDetector(threshold = 0.5, cooldown = (-1).milliseconds)
    }
}
