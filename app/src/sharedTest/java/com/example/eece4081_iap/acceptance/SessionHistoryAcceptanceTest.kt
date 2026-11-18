package com.example.eece4081_iap.acceptance

import android.Manifest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.eece4081_iap.data.FileSessionRepository
import com.example.eece4081_iap.engine.FakeAudioInput
import com.example.eece4081_iap.engine.FakeBeepPlayer
import com.example.eece4081_iap.engine.FakeClock
import com.example.eece4081_iap.engine.FakeScheduler
import com.example.eece4081_iap.engine.TimerEngine
import com.example.eece4081_iap.ui.ShotTimerApp
import com.example.eece4081_iap.ui.TimerViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/**
 * Acceptance test for US07, driving the real screens: a completed session is
 * saved only when the shooter taps Save, a saved session can be opened from
 * the history and reviewed, and deleted after a confirmation; starting a new
 * session over an unsaved one asks first.
 *
 * Everything below the screens is the production code, with sessions stored
 * as real files; only the hardware is replaced. Runs on the JVM and on a device.
 */
@RunWith(AndroidJUnit4::class)
class SessionHistoryAcceptanceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val microphonePermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @get:Rule
    val storage = TemporaryFolder()

    private val microphone = FakeAudioInput()
    private val beep = FakeBeepPlayer()
    private val clock = FakeClock()
    private val delay = FakeScheduler()
    private val gunshot = shortArrayOf(120, -31000, 29500, -8000, 400)

    private fun sessionsFolder() = File(storage.root, "sessions")
    private fun savedFiles(): Int = sessionsFolder().listFiles { f -> f.name.endsWith(".json") }?.size ?: 0

    private fun launchApp() {
        val app = TimerViewModel(
            engine = TimerEngine(microphone, beep, clock, delay),
            repository = FileSessionRepository(sessionsFolder()),
            postToMain = { it() },
            background = { it.run() },
        )
        composeRule.setContent { ShotTimerApp(viewModel = app) }
    }

    /** For a session already started: ends the start delay and beep, fires shots at [shotMillis] after the beep, taps Stop. */
    private fun recordSession(vararg shotMillis: Long) {
        composeRule.runOnIdle {
            delay.runNext() // the start delay ends
            beep.finish()
            var at = 0L
            for (ms in shotMillis) {
                clock.advance((ms - at).milliseconds)
                microphone.emit(gunshot)
                at = ms
            }
        }
        tap("Stop")
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(matcher)
    }

    private fun tap(text: String, substring: Boolean = false) {
        scrollTo(hasText(text, substring = substring))
        composeRule.onNodeWithText(text, substring = substring).performClick()
    }

    private fun assertShown(text: String, substring: Boolean = false) {
        scrollTo(hasText(text, substring = substring))
        composeRule.onNodeWithText(text, substring = substring).assertExists()
    }

    private fun tapInDialog(text: String) {
        composeRule.onNode(hasText(text) and hasAnyAncestor(isDialog())).performClick()
    }

    @Test
    fun aSavedSessionCanBeReviewed_andDeletedAfterConfirming() {
        launchApp()
        tap("Start")
        recordSession(1250, 1650)

        // Given the shooter saves the completed session
        assertEquals("nothing saved before Save", 0, savedFiles())
        tap("Save session")
        assertShown("Saved ✓")
        assertEquals(1, savedFiles())

        // When they open it from the history
        tap("2 shots, total 1.65 s", substring = true)

        // Then its recorded results and the settings it ran with are shown
        assertShown("Saved session")
        assertShown("Delay 3.0 s · Threshold 0.50 · Cooldown 80 ms")
        assertShown("Shots: 2    Total: 1.65 s")
        assertShown("#1   1.25 s   split —")
        assertShown("#2   1.65 s   split 0.40 s")

        // And Back returns to the timer
        tap("Back")
        assertShown("Past sessions")

        // When they choose Delete but cancel, the session is kept
        tap("2 shots, total 1.65 s", substring = true)
        tap("Delete")
        composeRule.onNodeWithText("Delete this session?").assertExists()
        tapInDialog("Cancel")
        assertShown("Saved session")
        assertEquals(1, savedFiles())

        // When they delete and confirm
        tap("Delete")
        tapInDialog("Delete")

        // Then the session is gone from the history and from storage
        assertShown("No saved sessions yet.")
        assertEquals(0, savedFiles())
    }

    @Test
    fun startingOverAnUnsavedSession_asksToSaveDiscardOrCancel() {
        launchApp()
        tap("Start")
        recordSession() // no shots

        // When the shooter taps Start without saving, they are asked first
        tap("Start")
        composeRule.onNodeWithText("This session hasn't been saved. Start a new one anyway?").assertExists()

        // Cancel keeps the completed session on screen
        tapInDialog("Cancel")
        assertShown("Session complete")

        // Discard starts a new session without saving the old one
        tap("Start")
        tapInDialog("Discard")
        assertShown("Get ready — the beep is coming")
        assertEquals(0, savedFiles())

        // Save and start keeps the completed session (even with no shots) and starts again
        recordSession()
        tap("Start")
        tapInDialog("Save and start")
        assertShown("Get ready — the beep is coming")
        assertEquals(1, savedFiles())
        assertShown("0 shots, total time unavailable", substring = true)
    }
}
