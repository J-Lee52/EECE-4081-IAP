package com.example.eece4081_iap.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class SessionStatisticsCalculatorTest {

    private fun shot(number: Int, timestampMs: Long, splitMs: Long?) = Shot(
        shotNumber = number,
        timestamp = timestampMs.milliseconds,
        splitTime = splitMs?.milliseconds,
    )

    @Test
    fun noShots_hasZeroCountAndNoStatistics() {
        val stats = SessionStatisticsCalculator.calculate(emptyList())

        assertEquals(0, stats.shotCount)
        assertNull(stats.totalTime)
        assertNull(stats.fastestSplit)
        assertNull(stats.averageSplit)
        assertNull(stats.slowestSplit)
        assertFalse(stats.splitStatisticsAvailable)
    }

    @Test
    fun singleShot_hasTotalTimeButNoSplitStatistics() {
        val stats = SessionStatisticsCalculator.calculate(listOf(shot(1, 1000, null)))

        assertEquals(1, stats.shotCount)
        assertEquals(1000.milliseconds, stats.totalTime)
        assertNull(stats.fastestSplit)
        assertNull(stats.averageSplit)
        assertNull(stats.slowestSplit)
        assertFalse(stats.splitStatisticsAvailable)
    }

    @Test
    fun threeShots_computesCountTotalAndSplitStatistics() {
        val shots = listOf(
            shot(1, 1000, null),
            shot(2, 1500, 500),
            shot(3, 2750, 1250),
        )

        val stats = SessionStatisticsCalculator.calculate(shots)

        assertEquals(3, stats.shotCount)
        assertEquals(2750.milliseconds, stats.totalTime)
        assertEquals(500.milliseconds, stats.fastestSplit)
        assertEquals(875.milliseconds, stats.averageSplit)
        assertEquals(1250.milliseconds, stats.slowestSplit)
        assertTrue(stats.splitStatisticsAvailable)
    }

    @Test
    fun twoShots_fastestAverageAndSlowestAreTheSameSplit() {
        val stats = SessionStatisticsCalculator.calculate(
            listOf(shot(1, 800, null), shot(2, 1300, 500)),
        )

        assertEquals(500.milliseconds, stats.fastestSplit)
        assertEquals(500.milliseconds, stats.averageSplit)
        assertEquals(500.milliseconds, stats.slowestSplit)
    }
}
