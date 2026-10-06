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
) : ViewModel() {

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

    private fun onSnapshot(latest: TimerSnapshot) {
        val justCompleted = latest.state == TimerState.Completed && lastState != TimerState.Completed
        lastState = latest.state
        snapshot = latest
        if (justCompleted) saveCompleted(latest.session)
    }

    /** A session with no shots is not worth keeping, so it is not saved. */
    private fun saveCompleted(session: ShootingSession?) {
        if (session == null || session.shots.isEmpty()) return
        background.execute {
            try {
                repository.save(session)
            } catch (e: Exception) {
                postToMain { errorMessage = "Could not save the session." }
                return@execute
            }
            val all = repository.loadAll()
            postToMain { history = all }
        }
    }

    private fun refreshHistory() {
        background.execute {
            val all = repository.loadAll()
            postToMain { history = all }
        }
    }

    override fun onCleared() {
        engine.listener = null
        // Releases the microphone if a session is recording. The engine does
        // not yet allow cancelling during the start delay (known limitation).
        engine.stop()
    }
}
