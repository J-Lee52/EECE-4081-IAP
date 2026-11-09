package com.example.eece4081_iap.engine

import com.example.eece4081_iap.audio.AudioInput
import com.example.eece4081_iap.audio.AudioInputError
import com.example.eece4081_iap.audio.BeepError
import com.example.eece4081_iap.audio.BeepPlayer
import com.example.eece4081_iap.audio.ShotDetector
import com.example.eece4081_iap.domain.FixedDelay
import com.example.eece4081_iap.domain.RandomDelay
import com.example.eece4081_iap.domain.SessionStatisticsCalculator
import com.example.eece4081_iap.domain.ShootingSession
import com.example.eece4081_iap.domain.StartDelay
import com.example.eece4081_iap.domain.TimerSettings
import com.example.eece4081_iap.logging.EventLog
import com.example.eece4081_iap.logging.LogLevel
import com.example.eece4081_iap.logging.LogRecord
import com.example.eece4081_iap.logging.bestEffort
import java.util.UUID
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the timer workflow: start() and stop(), the start delay, the beep, and
 * shot detection. Which state may follow which is defined once, in
 * [TimerState.canMoveTo]; every change goes through [moveTo], which applies it.
 *
 * It wires AudioInput to ShotDetector (ADR-001) and records detected shots in
 * a ShootingSession. It has no Android types: the microphone, beep, clock and
 * scheduler are injected, so the whole workflow is testable on the JVM.
 *
 * Each Start creates a run id ([newRunId]) that is put on every record sent
 * to [log] until the next Start, so one run can be followed through the log.
 * While Recording, a once-a-second "audio.level" record reports the loudest
 * sound heard against the detection threshold. Logging is best effort: a
 * failing [log] can never interrupt timing or a state change.
 *
 * Every callback from the hardware edge (the start delay, the beep, audio
 * samples and audio errors) is tied to the run that started it. A callback
 * from an earlier run is ignored, so it cannot affect a later run.
 *
 * If the start beep cannot be played, or the microphone fails, the run ends
 * and [TimerSnapshot.problem] says why, so the screen can tell the person.
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
    private val newRunId: () -> String = { UUID.randomUUID().toString().take(8) },
    log: EventLog = EventLog.NONE,
    private val random: Random = Random.Default,
) {
    /** Called after every change to the state or the session. */
    @Volatile
    var listener: ((TimerSnapshot) -> Unit)? = null

    private val log: EventLog = log.bestEffort()

    private var state = TimerState.Idle
    private var settings = initialSettings
    private var session: ShootingSession? = null
    private var detector: ShotDetector? = null
    private var beepStartedAt: Duration = Duration.ZERO
    private var runId: String? = null
    private var problem: RunProblem? = null

    // Audio heard while Recording, for the log only.
    private var heardBuffers = 0
    private var heardMaxPeak = 0.0
    private var heardIgnoredCooldown = 0
    private var windowStart: Duration? = null
    private var windowBuffers = 0
    private var windowMaxPeak = 0.0
    private var windowIgnoredCooldown = 0

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
        if (!state.canMoveTo(TimerState.Preparing)) {
            record(LogLevel.WARN, "start.rejected", "state" to state)
            return false
        }

        // A new session begins with no shots or splits.
        session = null
        detector = null
        problem = null
        runId = newRunId()
        resetAudioCounters()
        // The delay for this run is chosen once, now (US03). For Random
        // Range the log shows both the range and the delay actually chosen.
        val startDelay = settings.startDelay
        val delay = startDelay.chooseDuration(random)
        record(
            LogLevel.INFO, "run.started",
            "threshold" to settings.detectionThreshold,
            "cooldownMs" to settings.detectionCooldown.inWholeMilliseconds,
            *startDelayFields(startDelay),
            "startDelayMs" to delay.inWholeMilliseconds,
        )

        requireMoveTo(TimerState.Preparing)
        // Everything this run sets in motion is tagged with its id, so a
        // callback that arrives after a newer run has started is ignored, and
        // an error is still logged against the run it belongs to.
        val owner = runId
        try {
            audioInput.start(
                onSamples = { samples -> onSamples(owner, samples) },
                onError = { error -> onAudioError(owner, error) },
            )
        } catch (e: Exception) {
            record(
                LogLevel.ERROR, "microphone.open_failed",
                "error" to e.javaClass.simpleName,
                diagnostics = mapOf("message" to e.message),
            )
            requireMoveTo(TimerState.Idle)
            return false
        }
        record(LogLevel.INFO, "microphone.opened")

        detector = ShotDetector(settings.detectionThreshold, settings.detectionCooldown)
        requireMoveTo(TimerState.WaitingForStart)

        scheduler.schedule(delay) { onDelayElapsed(owner) }
        return true
    }

    /** The start-delay mode and, for Random Range, its range, for "run.started". */
    private fun startDelayFields(startDelay: StartDelay): Array<Pair<String, Any?>> = when (startDelay) {
        is FixedDelay -> arrayOf("startDelayMode" to "fixed")
        is RandomDelay -> arrayOf(
            "startDelayMode" to "random",
            "minDelayMs" to startDelay.minDelay.inWholeMilliseconds,
            "maxDelayMs" to startDelay.maxDelay.inWholeMilliseconds,
        )
    }

    /**
     * Ends the active run at any stage and releases the microphone, for when
     * the screen goes away (for example, the app is closed). Unlike [stop],
     * which is the user's Stop button and only works while Recording:
     *
     * - WaitingForStart (the start delay, or the beep is playing): the run is
     *   abandoned and the timer returns to Idle. A delay or beep callback that
     *   arrives afterwards is ignored.
     * - Recording: the run is completed as by [stop], keeping its shots.
     *
     * No problem is reported, because nothing went wrong. Returns false and
     * changes nothing in Idle or Completed. (Preparing exists only inside
     * [start], which holds the lock, so it cannot be seen here.)
     */
    @Synchronized
    fun cancel(): Boolean {
        when (state) {
            TimerState.WaitingForStart -> abandonRun(problem = null)
            TimerState.Recording -> finishRecording(reason = "cancelled")
            else -> {
                record(LogLevel.DEBUG, "cancel.rejected", "state" to state)
                return false
            }
        }
        return true
    }

    /**
     * Stops the active session (US06). Allowed only while Recording; returns
     * false and changes nothing otherwise.
     */
    @Synchronized
    fun stop(): Boolean {
        if (!state.canMoveTo(TimerState.Completed)) {
            record(LogLevel.DEBUG, "stop.rejected", "state" to state)
            return false
        }
        finishRecording(reason = "user")
        return true
    }

    /** Ends Recording: releases the microphone, logs the run summary, moves to Completed. */
    private fun finishRecording(reason: String) {
        audioInput.stop()
        flushAudioWindow()
        record(
            LogLevel.INFO, "run.stopped",
            "reason" to reason,
            "shots" to session?.shots.orEmpty().size,
            "recordedMs" to (clock.elapsed() - beepStartedAt).inWholeMilliseconds,
            "buffersHeard" to heardBuffers,
            "maxPeak" to rounded(heardMaxPeak),
            "threshold" to settings.detectionThreshold,
            "ignoredCooldown" to heardIgnoredCooldown,
        )
        requireMoveTo(TimerState.Completed)
    }

    /**
     * Ends a run that has not reached Recording: releases the microphone,
     * discards the unstarted session and returns to Idle. [problem] says
     * which hardware failure caused it (shown to the person), or null for a
     * requested [cancel].
     */
    private fun abandonRun(problem: RunProblem?) {
        audioInput.stop()
        session = null
        detector = null
        this.problem = problem
        // A hardware problem is a warning; a requested cancel is routine.
        if (problem != null) {
            record(LogLevel.WARN, "run.abandoned", "reason" to problem)
        } else {
            record(LogLevel.INFO, "run.abandoned", "reason" to "cancelled")
        }
        requireMoveTo(TimerState.Idle)
    }

    /**
     * True if [owner] is not the current run. Its callback is then ignored
     * (and logged against its own run), so it cannot affect a later run.
     */
    private fun isStale(owner: String?, callback: String): Boolean {
        if (owner == runId) return false
        record(LogLevel.DEBUG, "callback.ignored", "callback" to callback, "reason" to "stale_run", runId = owner, sessionId = null)
        return true
    }

    @Synchronized
    private fun onDelayElapsed(owner: String?) {
        if (isStale(owner, "delay")) return
        if (state != TimerState.WaitingForStart) return

        // The start beep establishes the timing origin (US03).
        beepStartedAt = clock.elapsed()
        session = ShootingSession(
            id = newSessionId(),
            startTime = clock.wallClockMillis(),
            settings = settings,
        )
        record(LogLevel.INFO, "beep.started")
        beepPlayer.play(
            onFinished = { onBeepFinished(owner) },
            onUnavailable = { error -> onBeepUnavailable(owner, error) },
        )
    }

    /**
     * The beep could not be played. Without the start signal the shooter has
     * no audible start and the times would be misleading (US03: the beep is
     * the timing start point), so the run is abandoned and the screen is told
     * why. Logged against the run that asked for the beep.
     */
    @Synchronized
    private fun onBeepUnavailable(owner: String?, error: BeepError) {
        val stillCurrent = owner == runId
        record(
            LogLevel.WARN, "beep.unavailable",
            "error" to error.error,
            diagnostics = mapOf("message" to error.message),
            runId = owner,
            sessionId = if (stillCurrent) session?.id else null,
        )
        if (stillCurrent && state == TimerState.WaitingForStart) abandonRun(RunProblem.BEEP_UNAVAILABLE)
    }

    @Synchronized
    private fun onBeepFinished(owner: String?) {
        if (isStale(owner, "beep_finished")) return
        // Also ignored (by the table) if this run already moved on, for
        // example when it was abandoned because the beep was unavailable.
        moveTo(TimerState.Recording)
    }

    @Synchronized
    private fun onSamples(owner: String?, samples: ShortArray) {
        // A buffer from an earlier run's capture: ignore it without logging
        // (it could arrive about 100 times a second).
        if (owner != runId) return
        // Not Recording: this drops audio during the delay and while the beep
        // plays, so the beep cannot be recorded as a shot, and drops late
        // buffers that arrive after stop().
        if (state != TimerState.Recording) return
        val current = session ?: return

        val timestamp = clock.elapsed() - beepStartedAt
        val peak = ShotDetector.peakAmplitude(samples)
        trackAudio(peak, timestamp)

        val event = detector?.process(samples, timestamp)
        if (event == null) {
            // Loud enough but not counted: only the cooldown can cause this.
            // Usually an echo; each shot can cause several, so they are
            // counted per second in "audio.level" and listed one by one only
            // when the log asks for detail (verbose mode).
            if (peak >= settings.detectionThreshold) {
                windowIgnoredCooldown++
                heardIgnoredCooldown++
                if (log.wantsDetail()) logIgnored("cooldown", timestamp, peak)
            }
            return
        }

        val previous = current.shots.lastOrNull()
        if (previous != null && event.timestamp <= previous.timestamp) {
            logIgnored("same_time", timestamp, peak)
            return
        }

        session = current.recordShot(event.timestamp)
        val shot = current.shots.size + 1
        record(
            LogLevel.INFO, "shot.recorded",
            "shot" to shot,
            "atMs" to event.timestamp.inWholeMilliseconds,
            "splitMs" to previous?.let { (event.timestamp - it.timestamp).inWholeMilliseconds },
            "peak" to rounded(peak),
        )
        notifyListener()
    }

    /**
     * The microphone stopped delivering sound. Logged against the run that
     * opened it ([owner]). If that run is still the current one it is ended,
     * because no more shots can be counted: Recording becomes Completed (the
     * shots so far are kept), and an unstarted run is abandoned. An error from
     * an earlier run's capture is only logged; it never changes a newer run.
     */
    @Synchronized
    private fun onAudioError(owner: String?, error: AudioInputError) {
        val stillCurrent = owner == runId
        record(
            LogLevel.ERROR, "microphone.read_error",
            "code" to error.code,
            "state" to if (stillCurrent) state else null,
            diagnostics = mapOf("message" to error.message),
            runId = owner,
            sessionId = if (stillCurrent) session?.id else null,
        )
        if (!stillCurrent) return
        when (state) {
            TimerState.Recording -> {
                problem = RunProblem.MICROPHONE_LOST
                finishRecording(reason = "microphone_lost")
            }
            TimerState.WaitingForStart -> abandonRun(RunProblem.MICROPHONE_LOST)
            else -> Unit // already ended; nothing more to do
        }
    }

    private fun logIgnored(reason: String, at: Duration, peak: Double) {
        record(
            LogLevel.DEBUG, "shot.ignored",
            "reason" to reason,
            "atMs" to at.inWholeMilliseconds,
            "peak" to rounded(peak),
        )
    }

    /** Counts audio heard while Recording and reports it once a second. */
    private fun trackAudio(peak: Double, at: Duration) {
        heardBuffers++
        heardMaxPeak = maxOf(heardMaxPeak, peak)
        val start = windowStart ?: at.also { windowStart = it }
        windowBuffers++
        windowMaxPeak = maxOf(windowMaxPeak, peak)
        if (at - start >= AUDIO_WINDOW) flushAudioWindow()
    }

    private fun flushAudioWindow() {
        val start = windowStart ?: return
        record(
            LogLevel.DEBUG, "audio.level",
            "fromMs" to start.inWholeMilliseconds,
            "buffers" to windowBuffers,
            "maxPeak" to rounded(windowMaxPeak),
            "threshold" to settings.detectionThreshold,
            "ignoredCooldown" to windowIgnoredCooldown,
        )
        windowStart = null
        windowBuffers = 0
        windowMaxPeak = 0.0
        windowIgnoredCooldown = 0
    }

    private fun resetAudioCounters() {
        heardBuffers = 0
        heardMaxPeak = 0.0
        heardIgnoredCooldown = 0
        windowStart = null
        windowBuffers = 0
        windowMaxPeak = 0.0
        windowIgnoredCooldown = 0
    }

    /**
     * Records an engine event. By default it carries the current run's id and
     * session id; callbacks from a specific run pass that run's ids instead.
     */
    private fun record(
        level: LogLevel,
        event: String,
        vararg data: Pair<String, Any?>,
        diagnostics: Map<String, Any?> = emptyMap(),
        runId: String? = this.runId,
        sessionId: String? = session?.id,
    ) {
        log.record(LogRecord(level, COMPONENT, event, runId, sessionId, linkedMapOf(*data), diagnostics))
    }

    /** Amplitudes rounded to 3 decimals keep log lines short and readable. */
    private fun rounded(value: Double): Double = Math.round(value * 1000) / 1000.0

    /**
     * The only place [state] changes. Applies the move if [TimerState.canMoveTo]
     * allows it and tells the listener; otherwise changes nothing and returns false.
     * Use it directly only for event callbacks, where a rejected (outdated) event
     * is expected and should be ignored.
     */
    private fun moveTo(next: TimerState): Boolean {
        if (!state.canMoveTo(next)) return false
        val from = state
        state = next
        record(LogLevel.INFO, "state.changed", "from" to from, "to" to next)
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
        runId = runId,
        problem = problem,
    )

    private companion object {
        const val COMPONENT = "engine"
        val AUDIO_WINDOW = 1.seconds
    }
}
