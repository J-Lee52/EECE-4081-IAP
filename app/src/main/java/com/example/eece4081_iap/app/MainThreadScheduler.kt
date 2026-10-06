package com.example.eece4081_iap.app

import android.os.Handler
import android.os.Looper
import com.example.eece4081_iap.engine.Scheduler
import kotlin.time.Duration

/** The real scheduler: runs the action on the main thread after [delay]. */
class MainThreadScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : Scheduler {
    override fun schedule(delay: Duration, action: () -> Unit) {
        handler.postDelayed(action, delay.inWholeMilliseconds)
    }
}
