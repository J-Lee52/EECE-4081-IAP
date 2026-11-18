package com.example.eece4081_iap.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.eece4081_iap.data.SessionRepository
import com.example.eece4081_iap.data.SettingsRepository
import com.example.eece4081_iap.domain.FixedDelay
import com.example.eece4081_iap.domain.RandomDelay
import com.example.eece4081_iap.domain.ShootingSession
import com.example.eece4081_iap.domain.TimerSettings
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
 * [snapshot], [history], [settings], [currentSessionSaved] and [errorMessage],
 * and call [start], [stop], [saveSettings], and for saved sessions (US07)
 * [saveCurrentSession], [discardCurrentSession] and [deleteSession].
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
    private val settingsRepository: SettingsRepository = SettingsRepository.NONE,
) : ViewModel() {

    private val log: EventLog = log.bestEffort()

    /** The settings the next session will use (US01). */
    var settings by mutableStateOf(engine.currentSettings())
        private set

    /** The timer as the screen should draw it right now. */
    var snapshot by mutableStateOf(engine.snapshot())
        private set

    /** Saved sessions, newest first. */
    var history by mutableStateOf<List<ShootingSession>>(emptyList())
        private set

    /** A message for the person, or null. Cleared by [start] and [dismissError]. */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /** True once the completed session on screen has been saved (US07). */
    var currentSessionSaved by mutableStateOf(false)
        private set

    private var saving = false

    private var lastState = snapshot.state
    private var lastProblem: RunProblem? = null

    init {
        engine.listener = { latest -> postToMain { onSnapshot(latest) } }
        loadSettings()
        refreshHistory()
    }

    /**
     * Uses [newSettings] for the next session and remembers them (US01).
     * Refused while a run is in progress (Preparing, WaitingForStart or
     * Recording): returns false and changes nothing. A failure to remember
     * them is shown, but they still apply until the app is closed.
     */
    fun saveSettings(newSettings: TimerSettings): Boolean {
        if (!engine.updateSettings(newSettings)) {
            record(LogLevel.WARN, "settings.rejected", null, null, "state" to engine.snapshot().state)
            errorMessage = "Settings can't be changed while a run is in progress."
            return false
        }
        settings = newSettings
        record(LogLevel.INFO, "settings.changed", null, null, *settingsFields(newSettings))
        background.execute {
            try {
                settingsRepository.save(newSettings)
            } catch (e: Exception) {
                record(
                    LogLevel.ERROR, "settings.save_failed", null, null,
                    "error" to e.javaClass.simpleName,
                    diagnostics = mapOf("message" to e.message),
                )
                postToMain { errorMessage = "Could not save the settings. They will apply until the app is closed." }
            }
        }
        return true
    }

    /**
     * Reads the remembered settings on the background thread and applies them
     * on the main thread. If they cannot be read, the defaults are used and the
     * person is told. (If a run was started in the few milliseconds before they
     * load, that run keeps the defaults.)
     */
    private fun loadSettings() {
        background.execute {
            val loaded = try {
                settingsRepository.load()
            } catch (e: Exception) {
                record(
                    LogLevel.WARN, "settings.load_failed", null, null,
                    "error" to e.javaClass.simpleName,
                    diagnostics = mapOf("message" to e.message),
                )
                postToMain { errorMessage = "The saved settings could not be read, so the defaults are used." }
                return@execute
            }
            if (loaded == null) return@execute
            postToMain {
                if (engine.updateSettings(loaded)) {
                    settings = loaded
                    record(LogLevel.INFO, "settings.loaded", null, null, *settingsFields(loaded))
                }
            }
        }
    }

    private fun settingsFields(settings: TimerSettings): Array<Pair<String, Any?>> {
        val delay = when (val d = settings.startDelay) {
            is FixedDelay -> arrayOf("startDelayMode" to "fixed", "startDelayMs" to d.duration.inWholeMilliseconds)
            is RandomDelay -> arrayOf(
                "startDelayMode" to "random",
                "minDelayMs" to d.minDelay.inWholeMilliseconds,
                "maxDelayMs" to d.maxDelay.inWholeMilliseconds,
            )
        }
        return arrayOf<Pair<String, Any?>>(
            "threshold" to settings.detectionThreshold,
            "cooldownMs" to settings.detectionCooldown.inWholeMilliseconds,
        ) + delay
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

    /**
     * True when a completed session is on screen and has not been saved, so
     * starting a new session would discard it (US07). The screen then asks
     * whether to save it, discard it, or cancel.
     */
    val needsSaveDecision: Boolean
        get() = snapshot.state == TimerState.Completed && snapshot.session != null && !currentSessionSaved

    /**
     * Saves the completed session on screen when the person taps Save (US07),
     * including a session with no shots. Does nothing if there is no completed
     * session, it is already saved, or a save is in progress. After a
     * successful save, [afterSaved] runs on the main thread (for "Save and
     * start"); after a failure it does not, and the error is shown.
     */
    fun saveCurrentSession(afterSaved: () -> Unit = {}) {
        val current = snapshot
        val session = current.session
        if (current.state != TimerState.Completed || session == null || currentSessionSaved || saving) return
        saving = true
        background.execute {
            try {
                repository.save(session)
            } catch (e: Exception) {
                record(
                    LogLevel.ERROR, "session.save_failed", current.runId, session.id,
                    "error" to e.javaClass.simpleName,
                    diagnostics = mapOf("message" to e.message),
                )
                postToMain {
                    saving = false
                    errorMessage = "Could not save the session."
                }
                return@execute
            }
            record(LogLevel.INFO, "session.saved", current.runId, session.id, "shots" to session.shots.size)
            loadHistory(current.runId)
            postToMain {
                saving = false
                if (snapshot.session?.id == session.id) currentSessionSaved = true
                afterSaved()
            }
        }
    }

    /** The person chose to start a new session without saving the completed one (US07). */
    fun discardCurrentSession() {
        if (!needsSaveDecision) return
        val current = snapshot
        record(
            LogLevel.INFO, "session.discarded", current.runId, current.session?.id,
            "shots" to current.session?.shots.orEmpty().size,
        )
    }

    /**
     * Permanently deletes the saved session with [id], after the person has
     * confirmed (US07). [afterDeleted] runs on the main thread once it is gone;
     * a failure is shown instead.
     */
    fun deleteSession(id: String, afterDeleted: () -> Unit = {}) {
        background.execute {
            try {
                repository.delete(id)
            } catch (e: Exception) {
                record(
                    LogLevel.ERROR, "session.delete_failed", null, id,
                    "error" to e.javaClass.simpleName,
                    diagnostics = mapOf("message" to e.message),
                )
                postToMain { errorMessage = "Could not delete the session." }
                return@execute
            }
            record(LogLevel.INFO, "session.deleted", null, id)
            loadHistory(runId = null)
            postToMain {
                // If it was the completed session still on screen, it is no longer saved.
                if (snapshot.session?.id == id) currentSessionSaved = false
                afterDeleted()
            }
        }
    }

    private fun onSnapshot(latest: TimerSnapshot) {
        val justCompleted = latest.state == TimerState.Completed && lastState != TimerState.Completed
        val problem = latest.problem
        if (problem != null && problem != lastProblem) errorMessage = messageFor(problem)
        lastState = latest.state
        lastProblem = problem
        snapshot = latest
        // A newly completed session is not saved until the person taps Save (US07).
        if (justCompleted) currentSessionSaved = false
    }

    private fun messageFor(problem: RunProblem): String = when (problem) {
        RunProblem.BEEP_UNAVAILABLE ->
            "The start beep could not be played, so the run was stopped before recording. " +
                "Check the phone's volume and try again."
        RunProblem.MICROPHONE_LOST ->
            "The microphone stopped working, so the run was ended. Shots already recorded are still here; tap Save session to keep them."
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
