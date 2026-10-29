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
 * Runs the timer workflow: start() and stop(), the start delay, the beep, and
 * shot detection. Which state may follow which is defined once, in
 * [TimerState.canMoveTo]; every change goes through [moveTo], which applies it.
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
        if (!state.isAtRest) return false
        settings = newSettings
        return true
    }

    /**
     * Starts a session. Allowed only in Idle or Completed (US02); from
     * Completed a new session simply replaces the finished one, so no
     * separate reset action is needed. Returns false if a session is already
     * active or the microphone could not be opened (the engine is then back
     * in Idle).
     */
    @Synchronized
    fun start(): Boolean {
        if (!state.canMoveTo(TimerState.Preparing)) return false

        // A new session begins with no shots or splits.
        session = null
        detector = null

        requireMoveTo(TimerState.Preparing)
        try {
            audioInput.start(this::onSamples)
        } catch (e: Exception) {
            requireMoveTo(TimerState.Idle)
            return false
        }

        detector = ShotDetector(settings.detectionThreshold, settings.detectionCooldown)
        requireMoveTo(TimerState.WaitingForStart)

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
        if (!state.canMoveTo(TimerState.Completed)) return false
        audioInput.stop()
        requireMoveTo(TimerState.Completed)
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
        // An event callback: if it arrives late (the state has moved on), the
        // table rejects the move and the callback is ignored, as intended.
        moveTo(TimerState.Recording)
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

    /**
     * The only place [state] changes. Applies the move if [TimerState.canMoveTo]
     * allows it and tells the listener; otherwise changes nothing and returns false.
     * Use it directly only for event callbacks, where a rejected (outdated) event
     * is expected and should be ignored.
     */
    private fun moveTo(next: TimerState): Boolean {
        if (!state.canMoveTo(next)) return false
        state = next
        notifyListener()
        return true
    }

    /**
     * For a transition the caller has already established is allowed. A
     * rejection here would mean the table and the engine disagree, which is a
     * bug, so it fails loudly instead of being silently ignored.
     */
    private fun requireMoveTo(next: TimerState) {
        val from = state
        check(moveTo(next)) { "Transition $from -> $next is not allowed by TimerState.canMoveTo" }
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
