package com.example.eece4081_iap.domain

import kotlin.time.Duration

/**
 * One timing session: the settings it ran with and the shots recorded in it.
 *
 * Immutable. [recordShot] returns a new session rather than modifying this one.
 * Timer workflow state (Idle, Recording, ...) is not part of the session; it
 * belongs to TimerEngine.
 *
 * [startTimeEpochMillis] is the wall-clock time of the start beep, stored as a
 * Long because java.time needs API 26+ and the app's minSdk is 24. Shot
 * timestamps are elapsed durations measured from this moment.
 */
data class ShootingSession(
    val id: String,
    val startTimeEpochMillis: Long,
    val settings: TimerSettings,
    val shots: List<Shot> = emptyList(),
) {
    init {
        require(shots.withIndex().all { (index, shot) -> shot.shotNumber == index + 1 }) {
            "shots must be numbered sequentially starting at 1"
        }
    }

    /**
     * Records a shot at [timestamp] (elapsed since the start beep).
     * The shot number is assigned sequentially and the split time is the
     * difference from the previous shot; the first shot has no split.
     */
    fun recordShot(timestamp: Duration): ShootingSession {
        val previous = shots.lastOrNull()
        require(previous == null || timestamp > previous.timestamp) {
            "shot timestamp $timestamp must be after the previous shot at ${previous?.timestamp}"
        }
        val shot = Shot(
            shotNumber = shots.size + 1,
            timestamp = timestamp,
            splitTime = previous?.let { timestamp - it.timestamp },
        )
        return copy(shots = shots + shot)
    }
}
