package com.example.eece4081_iap.domain

import kotlin.time.Duration

/**
 * Values derived from a session's shots. Never stored as authoritative state.
 * Fields are null when they cannot be computed (no shots, or fewer than two
 * shots for the split statistics).
 */
data class SessionStatistics(
    val shotCount: Int,
    val totalTime: Duration?,
    val fastestSplit: Duration?,
    val averageSplit: Duration?,
    val slowestSplit: Duration?,
) {
    /** Derived, not stored: split statistics exist only if a split exists. */
    val splitStatisticsAvailable: Boolean
        get() = fastestSplit != null
}

/** Stateless domain service that computes [SessionStatistics] from shots. */
object SessionStatisticsCalculator {

    fun calculate(shots: List<Shot>): SessionStatistics {
        if (shots.isEmpty()) {
            return SessionStatistics(
                shotCount = 0,
                totalTime = null,
                fastestSplit = null,
                averageSplit = null,
                slowestSplit = null,
            )
        }

        val splits = shots.mapNotNull { it.splitTime }
        val averageSplit = if (splits.isEmpty()) {
            null
        } else {
            splits.fold(Duration.ZERO) { sum, split -> sum + split } / splits.size
        }

        return SessionStatistics(
            shotCount = shots.size,
            totalTime = shots.maxOf { it.timestamp },
            fastestSplit = splits.minOrNull(),
            averageSplit = averageSplit,
            slowestSplit = splits.maxOrNull(),
        )
    }
}
