package com.example.eece4081_iap.engine

import com.example.eece4081_iap.domain.SessionStatistics
import com.example.eece4081_iap.domain.ShootingSession

/**
 * Workflow state of the timer (NFR 03: exactly one of these at any time).
 * Owned by TimerEngine, not part of the saved session.
 *
 * This is also the one place that says which state may follow which:
 *
 *   Idle ──────────┐
 *                  ├──> Preparing ──> WaitingForStart ──> Recording ──> Completed
 *   Completed ─────┘        │
 *                           └──> Idle   (the microphone could not be opened)
 *
 * TimerEngine asks [canMoveTo] before every change instead of keeping its
 * own copy of these rules.
 */
enum class TimerState {
    Idle,
    Preparing,
    WaitingForStart,
    Recording,
    Completed;

    /**
     * True when no session is in progress, so a new session may be started
     * (US02) and the settings may be changed (US01).
     */
    val isAtRest: Boolean
        get() = this == Idle || this == Completed

    /** True if the workflow may move directly from this state to [next]. */
    fun canMoveTo(next: TimerState): Boolean = when (this) {
        Idle, Completed -> next == Preparing
        Preparing -> next == WaitingForStart || next == Idle
        WaitingForStart -> next == Recording
        Recording -> next == Completed
    }
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
