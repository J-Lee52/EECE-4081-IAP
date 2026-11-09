package com.example.eece4081_iap.ui

import com.example.eece4081_iap.domain.SessionStatistics
import kotlin.time.Duration

/**
 * The wording the timer screen uses for session results, live and completed
 * (US05, US06). Kept out of the composable so the rules can be unit tested.
 *
 * When a value cannot be calculated the text says so in words, as the
 * requirements ask, rather than showing a blank or a dash:
 * - no shots yet: the total time is unavailable;
 * - fewer than two shots: there is no split, so split statistics are unavailable.
 */
object ResultsText {

    const val TOTAL_UNAVAILABLE = "Total time unavailable"
    const val SPLITS_UNAVAILABLE = "Split statistics unavailable (needs at least 2 shots)"

    /** For example "Shots: 3    Total: 2.35 s", or "Shots: 0    Total time unavailable". */
    fun summary(stats: SessionStatistics): String =
        "Shots: ${stats.shotCount}    " + (stats.totalTime?.let { "Total: ${it.toSecondsText()}" } ?: TOTAL_UNAVAILABLE)

    /** The three split statistics on three lines, or [SPLITS_UNAVAILABLE]. */
    fun splitStatistics(stats: SessionStatistics): String {
        if (!stats.splitStatisticsAvailable) return SPLITS_UNAVAILABLE
        return "Fastest split: ${stats.fastestSplit.seconds()}\n" +
            "Average split: ${stats.averageSplit.seconds()}\n" +
            "Slowest split: ${stats.slowestSplit.seconds()}"
    }

    /** A saved session's total for the history list: its last shot's time, or unavailable. */
    fun historyTotal(lastShotTime: Duration?): String =
        lastShotTime?.let { "total ${it.toSecondsText()}" } ?: "total time unavailable"

    // All three split statistics exist together (SessionStatisticsCalculator).
    private fun Duration?.seconds(): String = checkNotNull(this).toSecondsText()
}
