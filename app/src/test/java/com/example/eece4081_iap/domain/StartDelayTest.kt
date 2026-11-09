package com.example.eece4081_iap.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class StartDelayTest {

    /** A "random" source that always returns the lowest or highest value it is allowed to. */
    private class Extreme(private val highest: Boolean) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextLong(from: Long, until: Long): Long = if (highest) until - 1 else from
    }

    private val twoToFour = RandomDelay(minDelay = 2.seconds, maxDelay = 4.seconds)

    @Test
    fun fixedDelay_alwaysWaitsItsDuration() {
        assertEquals(3.seconds, FixedDelay(3.seconds).chooseDuration(Random(1)))
    }

    @Test
    fun randomDelay_canChooseExactlyTheMinimumAndExactlyTheMaximum() {
        assertEquals(2.seconds, twoToFour.chooseDuration(Extreme(highest = false)))
        assertEquals(4.seconds, twoToFour.chooseDuration(Extreme(highest = true)))
    }

    @Test
    fun randomDelay_alwaysChoosesWithinItsRangeAndVaries() {
        val random = Random(1)
        val chosen = List(1000) { twoToFour.chooseDuration(random) }

        assertTrue(chosen.all { it in 2.seconds..4.seconds })
        assertTrue("an unpredictable delay must vary", chosen.toSet().size > 900)
    }

    @Test
    fun randomDelay_withEqualMinimumAndMaximum_alwaysWaitsThatDelay() {
        assertEquals(3.seconds, RandomDelay(3.seconds, 3.seconds).chooseDuration(Random(1)))
    }

    @Test
    fun randomDelay_whereTheMinimumExceedsTheMaximum_isRejected() {
        assertThrows(IllegalArgumentException::class.java) { RandomDelay(minDelay = 5.seconds, maxDelay = 4.seconds) }
    }

    @Test
    fun randomDelay_withANegativeMinimum_isRejected() {
        assertThrows(IllegalArgumentException::class.java) { RandomDelay(minDelay = (-1).seconds, maxDelay = 4.seconds) }
    }

    @Test
    fun timerSettings_acceptARandomDelay() {
        val settings = TimerSettings(0.5, 200.milliseconds, twoToFour)

        assertEquals(twoToFour, settings.startDelay)
    }
}
