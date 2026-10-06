package com.example.eece4081_iap.domain

import kotlin.time.Duration

/**
 * One detected shot within a [ShootingSession].
 *
 * [timestamp] is the elapsed time since the session's start beep.
 * [splitTime] is the time since the previous shot, or null for the first shot.
 */
data class Shot(
    val shotNumber: Int,
    val timestamp: Duration,
    val splitTime: Duration?,
) {
    init {
        require(shotNumber >= 1) { "shotNumber must be at least 1, was $shotNumber" }
        require(!timestamp.isNegative()) { "timestamp must not be negative, was $timestamp" }
        require(splitTime == null || !splitTime.isNegative()) {
            "splitTime must not be negative, was $splitTime"
        }
    }
}
