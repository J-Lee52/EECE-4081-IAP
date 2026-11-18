package com.example.eece4081_iap.ui

import com.example.eece4081_iap.domain.SessionStatisticsCalculator
import com.example.eece4081_iap.domain.ShootingSession
import com.example.eece4081_iap.domain.TimerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun shotCount_isSingularForOneShot() {
        assertEquals("1 shot", ResultsText.shotCount(1))
        assertEquals("0 shots", ResultsText.shotCount(0))
        assertEquals("5 shots", ResultsText.shotCount(5))
    }

    @Test
    fun shotRow_showsNumberTimeAndSplit_withNoSplitForTheFirstShot() {
        val shots = ShootingSession("s", 0L, TimerSettings.DEFAULT)
            .recordShot(1250.milliseconds)
            .recordShot(1650.milliseconds)
            .shots

        assertEquals("#1   1.25 s   split —", ResultsText.shotRow(shots[0]))
        assertEquals("#2   1.65 s   split 0.40 s", ResultsText.shotRow(shots[1]))
    }

    @Test
    fun historyRow_saysOneShotOrNoTotalForSmallSessions() {
        val oneShot = ShootingSession("a", 0L, TimerSettings.DEFAULT).recordShot(2650.milliseconds)
        val noShots = ShootingSession("b", 0L, TimerSettings.DEFAULT)

        assertTrue(ResultsText.historyRow(oneShot).endsWith(" — 1 shot, total 2.65 s"))
        assertTrue(ResultsText.historyRow(noShots).endsWith(" — 0 shots, total time unavailable"))
    }

    @Test
    fun historyTotal_isTheLastShotsTime_orSaysUnavailableForASessionWithNoShots() {
        assertEquals("total 2.35 s", ResultsText.historyTotal(2350.milliseconds))
        assertEquals("total time unavailable", ResultsText.historyTotal(null))
    }
}
