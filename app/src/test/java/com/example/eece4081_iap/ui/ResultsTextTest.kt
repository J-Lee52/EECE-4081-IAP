package com.example.eece4081_iap.ui

import com.example.eece4081_iap.domain.SessionStatisticsCalculator
import com.example.eece4081_iap.domain.ShootingSession
import com.example.eece4081_iap.domain.TimerSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class ResultsTextTest {

    private fun statsFor(vararg shotMillis: Long) = SessionStatisticsCalculator.calculate(
        shotMillis.fold(ShootingSession("s", 0L, TimerSettings.DEFAULT)) { session, ms ->
            session.recordShot(ms.milliseconds)
        }.shots,
    )

    @Test
    fun noShots_saysTotalTimeAndSplitStatisticsAreUnavailable() {
        val stats = statsFor()

        assertEquals("Shots: 0    Total time unavailable", ResultsText.summary(stats))
        assertEquals("Split statistics unavailable (needs at least 2 shots)", ResultsText.splitStatistics(stats))
    }

    @Test
    fun oneShot_showsTheTotalButSaysSplitStatisticsAreUnavailable() {
        val stats = statsFor(1250)

        assertEquals("Shots: 1    Total: 1.25 s", ResultsText.summary(stats))
        assertEquals(ResultsText.SPLITS_UNAVAILABLE, ResultsText.splitStatistics(stats))
    }

    @Test
    fun twoOrMoreShots_showAllThreeSplitStatistics() {
        val stats = statsFor(1250, 1650, 2350)

        assertEquals("Shots: 3    Total: 2.35 s", ResultsText.summary(stats))
        assertEquals(
            "Fastest split: 0.40 s\nAverage split: 0.55 s\nSlowest split: 0.70 s",
            ResultsText.splitStatistics(stats),
        )
    }

    @Test
    fun historyTotal_isTheLastShotsTime_orSaysUnavailableForASessionWithNoShots() {
        assertEquals("total 2.35 s", ResultsText.historyTotal(2350.milliseconds))
        assertEquals("total time unavailable", ResultsText.historyTotal(null))
    }
}
