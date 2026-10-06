package com.example.eece4081_iap.engine

import kotlin.time.Duration

/** Time source, injected so tests can control time. */
interface Clock {

    /**
     * Monotonic elapsed time from an arbitrary fixed origin. Used to measure
     * shot times; it never goes backwards.
     */
    fun elapsed(): Duration

    /** Wall-clock time in epoch milliseconds. Used only to label sessions. */
    fun wallClockMillis(): Long
}

/** Runs an action after a delay, injected so tests do not have to wait. */
interface Scheduler {

    /** Runs [action] once, after [delay]. */
    fun schedule(delay: Duration, action: () -> Unit)
}
