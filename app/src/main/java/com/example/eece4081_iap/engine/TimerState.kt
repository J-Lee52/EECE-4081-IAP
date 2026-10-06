package com.example.eece4081_iap.engine

import com.example.eece4081_iap.domain.SessionStatistics
import com.example.eece4081_iap.domain.ShootingSession

/**
 * Workflow state of the timer (NFR 03: exactly one of these at any time).
 * Owned by TimerEngine, not part of the saved session.
 */
enum class TimerState {
    Idle,
    Preparing,
    WaitingForStart,
    Recording,
    Completed,
}

/**
 * Everything the UI needs to draw the timer, captured at one moment.
 * [session] is null until the start beep begins, and again after a new
 * session is started from Completed.
 */
data class TimerSnapshot(
    val state: TimerState,
    val session: ShootingSession?,
    val statistics: SessionStatistics,
)
