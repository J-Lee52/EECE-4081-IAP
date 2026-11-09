package com.example.eece4081_iap.engine

import com.example.eece4081_iap.audio.AudioInput
import com.example.eece4081_iap.audio.AudioInputError
import com.example.eece4081_iap.audio.BeepError
import com.example.eece4081_iap.audio.BeepPlayer
import kotlin.time.Duration

/** Test double: the test pushes sample buffers in with [emit]. */
class FakeAudioInput : AudioInput {
    var started = false
        private set
    var startCount = 0
        private set
    var stopCount = 0
        private set

    /** If set, [start] throws it, like a missing microphone permission. */
    var failOnStart: RuntimeException? = null

    private var listener: ((ShortArray) -> Unit)? = null
    private var errorListener: ((AudioInputError) -> Unit)? = null

    override fun start(onSamples: (ShortArray) -> Unit, onError: (AudioInputError) -> Unit) {
        failOnStart?.let { throw it }
        started = true
        startCount++
        listener = onSamples
        errorListener = onError
    }

    override fun stop() {
        started = false
        stopCount++
        // The listener is kept on purpose so [emit] can simulate a late
        // buffer arriving after stop().
    }

    fun emit(samples: ShortArray) {
        listener?.invoke(samples)
    }

    /** Simulates the capture failing after it started, through the latest start()'s callback. */
    fun fail(error: AudioInputError) {
        errorListener?.invoke(error)
    }

    /** Keeps the error callback of the current capture, to report it late (after a new start). */
    fun captureErrorCallback(): (AudioInputError) -> Unit = checkNotNull(errorListener)

    /** Keeps the samples callback of the current capture, to deliver a late buffer (after a new start). */
    fun captureSamplesCallback(): (ShortArray) -> Unit = checkNotNull(listener)
}

/** Test double: the beep "plays" until the test calls [finish]. */
class FakeBeepPlayer : BeepPlayer {
    var playCount = 0
        private set

    private var onFinished: (() -> Unit)? = null
    private var onUnavailable: ((BeepError) -> Unit)? = null

    override fun play(onFinished: () -> Unit, onUnavailable: (BeepError) -> Unit) {
        playCount++
        this.onFinished = onFinished
        this.onUnavailable = onUnavailable
    }

    fun finish() {
        onFinished?.invoke()
    }

    /** Simulates the device having no way to play the beep. */
    fun reportUnavailable(error: BeepError) {
        onUnavailable?.invoke(error)
    }
}

/** Test double: time only moves when the test says so. */
class FakeClock(
    var elapsedNow: Duration = Duration.ZERO,
    var wallMillis: Long = 1_000L,
) : Clock {
    override fun elapsed(): Duration = elapsedNow

    override fun wallClockMillis(): Long = wallMillis

    fun advance(by: Duration) {
        elapsedNow += by
    }
}

/** Test double: scheduled actions wait until the test runs them. */
class FakeScheduler : Scheduler {
    data class Scheduled(val delay: Duration, val action: () -> Unit)

    val scheduled = mutableListOf<Scheduled>()

    override fun schedule(delay: Duration, action: () -> Unit) {
        scheduled.add(Scheduled(delay, action))
    }

    /** Runs the oldest scheduled action, as if its delay had elapsed. */
    fun runNext() {
        scheduled.removeAt(0).action()
    }
}
