package com.example.eece4081_iap.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

class ShootingSessionTest {

    private fun newSession() = ShootingSession(
        id = "test-session",
        startTimeEpochMillis = 0L,
        settings = TimerSettings.DEFAULT,
    )

    @Test
    fun newSession_hasNoShots() {
        assertTrue(newSession().shots.isEmpty())
    }

    @Test
    fun firstShot_hasNumberOneAndNoSplit() {
        val session = newSession().recordShot(1200.milliseconds)

        val shot = session.shots.single()
        assertEquals(1, shot.shotNumber)
        assertEquals(1200.milliseconds, shot.timestamp)
        assertNull(shot.splitTime)
    }

    @Test
    fun laterShots_getSequentialNumbersAndSplitFromPreviousShot() {
        val session = newSession()
            .recordShot(1000.milliseconds)
            .recordShot(1600.milliseconds)
            .recordShot(2500.milliseconds)

        assertEquals(listOf(1, 2, 3), session.shots.map { it.shotNumber })
        assertNull(session.shots[0].splitTime)
        assertEquals(600.milliseconds, session.shots[1].splitTime)
        assertEquals(900.milliseconds, session.shots[2].splitTime)
    }

    @Test
    fun recordShot_returnsNewSessionAndLeavesOriginalUnchanged() {
        val original = newSession()

        val updated = original.recordShot(1000.milliseconds)

        assertTrue(original.shots.isEmpty())
        assertEquals(1, updated.shots.size)
    }

    @Test
    fun recordShot_keepsIdStartTimeAndSettings() {
        val updated = newSession().recordShot(1000.milliseconds)

        assertEquals("test-session", updated.id)
        assertEquals(0L, updated.startTimeEpochMillis)
        assertEquals(TimerSettings.DEFAULT, updated.settings)
    }

    @Test(expected = IllegalArgumentException::class)
    fun recordShot_rejectsTimestampEqualToPreviousShot() {
        newSession().recordShot(1000.milliseconds).recordShot(1000.milliseconds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun recordShot_rejectsTimestampBeforePreviousShot() {
        newSession().recordShot(2000.milliseconds).recordShot(1500.milliseconds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun session_rejectsShotsThatAreNotNumberedSequentially() {
        ShootingSession(
            id = "bad",
            startTimeEpochMillis = 0L,
            settings = TimerSettings.DEFAULT,
            shots = listOf(Shot(2, 1.seconds, null)),
        )
    }

    // NFR 01: calculated splits differ from the mathematically expected split
    // by no more than 0.01 seconds.
    @Test
    fun splitTimes_matchExpectedValuesWithinNfr01Tolerance() {
        val timestampsSeconds = listOf(1.234, 1.987, 2.5, 3.1415, 4.0)
        var session = newSession()
        timestampsSeconds.forEach { session = session.recordShot(it.seconds) }

        timestampsSeconds.zipWithNext { previous, current -> current - previous }
            .forEachIndexed { index, expectedSplitSeconds ->
                val actual = session.shots[index + 1].splitTime!!.toDouble(DurationUnit.SECONDS)
                assertTrue(
                    "split ${index + 1}: expected $expectedSplitSeconds but was $actual",
                    abs(actual - expectedSplitSeconds) <= 0.01,
                )
            }
    }
}
