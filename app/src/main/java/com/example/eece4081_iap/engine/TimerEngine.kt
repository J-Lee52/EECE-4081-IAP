package com.example.eece4081_iap.engine

import com.example.eece4081_iap.audio.AudioInput
import com.example.eece4081_iap.audio.BeepPlayer
import com.example.eece4081_iap.audio.ShotDetector
import com.example.eece4081_iap.domain.FixedDelay
import com.example.eece4081_iap.domain.SessionStatisticsCalculator
import com.example.eece4081_iap.domain.ShootingSession
import com.example.eece4081_iap.domain.TimerSettings
import java.util.UUID
import kotlin.time.Duration

/**
 * Owns the timer workflow: the five states and the transitions between them.
 *
 *   Idle/Completed --start()--> Preparing --> WaitingForStart
 *     --(delay elapsed, beep plays, beep finishes)--> Recording --stop()--> Completed
 *
 * It wires AudioInput to ShotDetector (ADR-001) and records detected shots in
 * a ShootingSession. It has no Android types: the microphone, beep, clock and
 * scheduler are injected, so the whole workflow is testable on the JVM.
 *
 * Methods are synchronized because audio buffers arrive on a background
 * thread. [listener] is called on whichever thread caused the change, while
 * the engine's lock is held, so keep it short (post to the UI thread).
 */
class TimerEngine(
    private val audioInput: AudioInput,
    private val beepPlayer: BeepPlayer,
    private val clock: Clock,
    private val scheduler: Scheduler,
    private val newSessionId: () -> String = { UUID.randomUUID().toString() },
    initialSettings: TimerSettings = TimerSettings.DEFAULT,
) {
    /** Called after every change to the state or the session. */
    @Volatile
    var listener: ((TimerSnapshot) -> Unit)? = null

    private var state = TimerState.Idle
    private var settings = initialSettings
    private var session: ShootingSession? = null
    private var detector: ShotDetector? = null
    private var beepStartedAt: Duration = Duration.ZERO

    @Synchronized
    fun snapshot(): TimerSnapshot = buildSnapshot()

    @Synchronized
    fun currentSettings(): TimerSettings = settings

    /**
     * Changes the settings for the next session. Allowed only in Idle or
     * Completed (US01); returns false and changes nothing otherwise.
     */
    @Synchronized
    fun updateSettings(newSettings: TimerSettings): Boolean {
        if (state != TimerState.Idle && state != TimerState.Completed) return false
        settings = newSettings
        return true
    }

    /**
     * Starts a session. Allowed only in Idle or Completed (US02). From
     * Completed the engine first resets internally to Idle, so no separate
     * reset action is needed. Returns false if a session is already active or
     * the microphone could not be opened (the engine is then back in Idle).
     */
    @Synchronized
    fun start(): Boolean {
        if (state != TimerState.Idle && state != TimerState.Completed) return false

        // Internal reset: a new session begins with no shots or splits.
        state = TimerState.Idle
        session = null
        detector = null

        setState(TimerState.Preparing)
        try {
            audioInput.start(this::onSamples)
        } catch (e: Exception) {
            setState(TimerState.Idle)
            return false
        }

        detector = ShotDetector(settings.detectionThreshold, settings.detectionCooldown)
        setState(TimerState.WaitingForStart)

        // Exhaustive over StartDelay: adding RandomDelay later forces this to be updated.
        val delay = when (val startDelay = settings.startDelay) {
            is FixedDelay -> startDelay.duration
        }
        scheduler.schedule(delay, this::onDelayElapsed)
        return true
    }

    /**
     * Stops the active session (US06). Allowed only while Recording; returns
     * false and changes nothing otherwise.
     */
    @Synchronized
    fun stop(): Boolean {
        if (state != TimerState.Recording) return false
        audioInput.stop()
        setState(TimerState.Completed)
        return true
    }

    @Synchronized
    private fun onDelayElapsed() {
        if (state != TimerState.WaitingForStart) return

        // The start beep establishes the timing origin (US03).
        beepStartedAt = clock.elapsed()
        session = ShootingSession(
            id = newSessionId(),
            startTime = clock.wallClockMillis(),
            settings = settings,
        )
        beepPlayer.play(this::onBeepFinished)
    }

    @Synchronized
    private fun onBeepFinished() {
        if (state != TimerState.WaitingForStart) return
        setState(TimerState.Recording)
    }

    @Synchronized
    private fun onSamples(samples: ShortArray) {
        // Not Recording: this drops audio during the delay and while the beep
        // plays, so the beep cannot be recorded as a shot, and drops late
        // buffers that arrive after stop().
        if (state != TimerState.Recording) return
        val current = session ?: return

        val timestamp = clock.elapsed() - beepStartedAt
        val event = detector?.process(samples, timestamp) ?: return

        val previous = current.shots.lastOrNull()
        if (previous != null && event.timestamp <= previous.timestamp) return

        session = current.recordShot(event.timestamp)
        notifyListener()
    }

    private fun setState(newState: TimerState) {
        state = newState
        notifyListener()
    }

    private fun notifyListener() {
        listener?.invoke(buildSnapshot())
    }

    private fun buildSnapshot() = TimerSnapshot(
        state = state,
        session = session,
        statistics = SessionStatisticsCalculator.calculate(session?.shots.orEmpty()),
    )
}
