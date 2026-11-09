package com.example.eece4081_iap.acceptance

import android.Manifest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.eece4081_iap.data.FileSessionRepository
import com.example.eece4081_iap.data.FileSettingsRepository
import com.example.eece4081_iap.engine.FakeAudioInput
import com.example.eece4081_iap.engine.FakeBeepPlayer
import com.example.eece4081_iap.engine.FakeClock
import com.example.eece4081_iap.engine.FakeScheduler
import com.example.eece4081_iap.engine.TimerEngine
import com.example.eece4081_iap.ui.ShotTimerApp
import com.example.eece4081_iap.ui.TimerViewModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * Acceptance test for US01, driving the real Timer and Settings screens:
 * open Settings, choose a Random Range start delay and a lower detection
 * threshold, save, start a session with them, and find them still chosen
 * after the app is reopened. Settings are locked while a run is in progress.
 *
 * Everything below the screens is the production code, storing settings in a
 * real file; only the hardware is replaced. Runs on the JVM and on a device.
 */
@RunWith(AndroidJUnit4::class)
class SettingsAcceptanceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val microphonePermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @get:Rule
    val storage = TemporaryFolder()

    private class Hardware {
        val microphone = FakeAudioInput()
        val beep = FakeBeepPlayer()
        val clock = FakeClock()
        val delay = FakeScheduler()
    }

    private fun launchApp(hardware: Hardware, appFolder: File): TimerViewModel = TimerViewModel(
        engine = TimerEngine(hardware.microphone, hardware.beep, hardware.clock, hardware.delay),
        repository = FileSessionRepository(File(appFolder, "sessions")),
        postToMain = { it() },
        background = { it.run() },
        settingsRepository = FileSettingsRepository(File(appFolder, "settings.json")),
    )

    private fun tap(text: String) {
        composeRule.onNodeWithText(text).performScrollTo().performClick()
    }

    private fun assertShown(text: String) {
        composeRule.onNodeWithText(text).assertExists()
    }

    @Test
    fun chosenSettingsAreUsedByTheNextSessionAndRememberedAfterReopening() {
        val appFolder = storage.root
        val firstRun = Hardware()
        var app by mutableStateOf(launchApp(firstRun, appFolder))
        composeRule.setContent { ShotTimerApp(viewModel = app) }

        // Given the defaults
        assertShown("Delay 3.0 s · Threshold 0.50 · Cooldown 80 ms")

        // When the shooter opens Settings, chooses a random start delay and a
        // lower detection threshold, and saves
        composeRule.onNodeWithText("Settings").assertIsEnabled().performClick()
        tap("Random range")
        assertShown("The beep comes at a random time between 2.0 s and 4.0 s.")
        composeRule.onNodeWithContentDescription("Detection threshold")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(30f) }
        tap("Save")

        // Then the timer screen shows them
        assertShown("Delay random 2.0–4.0 s · Threshold 0.30 · Cooldown 80 ms")

        // And the next session waits a random delay within the range
        composeRule.onNodeWithText("Start").performClick()
        val waited = firstRun.delay.scheduled.single().delay
        assertTrue("delay $waited is within 2–4 s", waited in 2.seconds..4.seconds)

        // And settings are locked while the run is in progress
        composeRule.onNodeWithText("Settings").assertIsNotEnabled()

        // When the app is closed and opened again (a new ViewModel, engine
        // and repositories; only the app's storage folder is kept)
        app = launchApp(Hardware(), appFolder)

        // Then the chosen settings are still in use
        assertShown("Delay random 2.0–4.0 s · Threshold 0.30 · Cooldown 80 ms")
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Random range").assertIsSelected()
    }

    @Test
    fun cancel_leavesTheSettingsUnchanged() {
        val app = launchApp(Hardware(), storage.root)
        composeRule.setContent { ShotTimerApp(viewModel = app) }

        composeRule.onNodeWithText("Settings").performClick()
        tap("Random range")
        tap("Cancel")

        assertShown("Delay 3.0 s · Threshold 0.50 · Cooldown 80 ms")
    }
}
