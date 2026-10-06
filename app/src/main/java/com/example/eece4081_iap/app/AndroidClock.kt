package com.example.eece4081_iap.app

import android.os.SystemClock
import com.example.eece4081_iap.engine.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * The real clock. [elapsed] is monotonic (it never jumps if the user changes
 * the date or time), so it is used for shot timing. [wallClockMillis] is only
 * the date and time a session started.
 */
class AndroidClock : Clock {
    override fun elapsed(): Duration = SystemClock.elapsedRealtimeNanos().nanoseconds

    override fun wallClockMillis(): Long = System.currentTimeMillis()
}
