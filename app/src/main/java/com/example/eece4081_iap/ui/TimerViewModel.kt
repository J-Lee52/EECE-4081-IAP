package com.example.eece4081_iap.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.eece4081_iap.data.SessionRepository
import com.example.eece4081_iap.domain.ShootingSession
import com.example.eece4081_iap.engine.TimerEngine
import com.example.eece4081_iap.engine.TimerSnapshot
import com.example.eece4081_iap.engine.TimerState
import com.example.eece4081_iap.logging.EventLog
import com.example.eece4081_iap.logging.LogLevel
import com.example.eece4081_iap.logging.LogRecord
import com.example.eece4081_iap.logging.bestEffort
import com.example.eece4081_iap.engine.RunProblem
import java.util.concurrent.Executor

/**
 * Connects the screens to the timer workflow (ADR-001). The screens read
 * [snapshot], [history] and [errorMessage] and call [start] and [stop].
 *
 * The engine reports changes on whatever thread caused them (often the audio
 * thread), so every change is handed to [postToMain] before it touches the
 * state the screens observe. Saving and loading use [background], because
 * file work must stay off the main thread.
 *
 * The thread hand-offs are injected, so the unit tests can run everything
 * immediately and in order.
 */
class TimerViewModel(
    private val engine: TimerEngine,
    private val repository: SessionRepository,
    private val postToMain: (() -> Unit) -> Unit,
    private val background: Executor,
    log: EventLog = EventLog.NONE,
) : ViewModel() {

    private val log: EventLog = log.bestEffort()

    /** The timer as the screen should draw it right now. */
    var snapshot by mutableStateOf(engine.snapshot())
        private set

    /** Saved sessions, newest first. */
    var history by mutableStateOf<List<ShootingSession>>(emptyList())
        private set

    /** A message for the person, or null. Cleared by [start] and [dismissError]. */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var lastState = snapshot.state
    private var lastProblem: RunProblem? = null

    init {
        engine.listener = { latest -> postToMain { onSnapshot(latest) } }
        refreshHistory()
    }

    fun start() {
        errorMessage = null
        // The engine returns false either because a session is already active
        // or because the microphone could not be opened. Only the second
        // leaves it in Idle.
        if (!engine.start() && engine.snapshot().state == TimerState.Idle) {
            errorMessage = "Could not open the microphone. Check that microphone access is allowed."
        }
    }

    fun stop() {
        engine.stop()
    }

    fun dismissError() {
        errorMessage = null
    }

    /** Called by the screen when the person refuses the microphone permission. */
    fun microphonePermissionDenied() {
        record(LogLevel.WARN, "permission.denied", null, null, "permission" to "RECORD_AUDIO")
    }

    private fun onSnapshot(latest: TimerSnapshot) {
        val justCompleted = latest.state == TimerState.Completed && lastState != TimerState.Completed
        val problem = latest.problem
        if (problem != null && problem != lastProblem) errorMessage = messageFor(problem)
        lastState = latest.state
        lastProblem = problem
        snapshot = latest
        if (justCompleted) saveCompleted(latest.session, latest.runId)
    }

    private fun messageFor(problem: RunProblem): String = when (problem) {
        RunProblem.BEEP_UNAVAILABLE ->
            "The start beep could not be played, so the run was stopped before recording. " +
                "Check the phone's volume and try again."
        RunProblem.MICROPHONE_LOST ->
            "The microphone stopped working, so the run was ended. Any shots already recorded were kept."
    }

    /** A session with no shots is not worth keeping, so it is not saved. */
    private fun saveCompleted(session: ShootingSession?, runId: String?) {
        if (session == null || session.shots.isEmpty()) {
            record(LogLevel.INFO, "session.not_saved", runId, session?.id, "reason" to "no_shots")
            return
        }
        background.execute {
            try {
                repository.save(session)
            } catch (e: Exception) {
                record(
                    LogLevel.ERROR, "session.save_failed", runId, session.id,
                    "error" to e.javaClass.simpleName,
                    diagnostics = mapOf("message" to e.message),
                )
                postToMain { errorMessage = "Could not save the session." }
                return@execute
            }
            record(LogLevel.INFO, "session.saved", runId, session.id, "shots" to session.shots.size)
            loadHistory(runId)
        }
    }

    private fun refreshHistory() {
        background.execute { loadHistory(runId = null) }
    }

    /**
     * Reloads the saved sessions on the background thread. A read failure is
     * logged and shown to the person; the history already on screen and the
     * current (for example, just completed) session stay as they are.
     */
    private fun loadHistory(runId: String?) {
        val all = try {
            repository.loadAll()
        } catch (e: Exception) {
            record(
                LogLevel.ERROR, "history.load_failed", runId, null,
                "error" to e.javaClass.simpleName,
                diagnostics = mapOf("message" to e.message),
            )
            postToMain { errorMessage = "Could not load past sessions." }
            return
        }
        record(LogLevel.INFO, "history.loaded", runId, null, "sessions" to all.size)
        postToMain { history = all }
    }

    private fun record(
        level: LogLevel,
        event: String,
        runId: String?,
        sessionId: String?,
        vararg data: Pair<String, Any?>,
        diagnostics: Map<String, Any?> = emptyMap(),
    ) {
        log.record(LogRecord(level, COMPONENT, event, runId, sessionId, linkedMapOf(*data), diagnostics))
    }

    private companion object {
        const val COMPONENT = "viewmodel"
    }

    override fun onCleared() {
        engine.listener = null
        // Ends the run at any stage, including the start delay, so the
        // microphone is never left open after the screen has gone.
        engine.cancel()
    }
}
