package com.example.eece4081_iap.acceptance

import android.Manifest
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.eece4081_iap.data.FileSessionRepository
import com.example.eece4081_iap.domain.FixedDelay
import com.example.eece4081_iap.domain.TimerSettings
import com.example.eece4081_iap.engine.FakeAudioInput
import com.example.eece4081_iap.engine.FakeBeepPlayer
import com.example.eece4081_iap.engine.FakeClock
import com.example.eece4081_iap.engine.FakeScheduler
import com.example.eece4081_iap.engine.TimerEngine
import com.example.eece4081_iap.ui.TimerScreen
import com.example.eece4081_iap.ui.TimerViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Acceptance test for the walking-skeleton thin slice (M5):
 *
 *   Start -> fixed delay -> start beep -> shots detected from microphone
 *   audio -> splits and statistics on screen -> Stop -> session saved ->
 *   app reopened -> session still listed under "Past sessions".
 *
 * It drives the real TimerScreen by its button labels and checks what the
 * person would read. Everything between the screen and the disk is the
 * production code: TimerViewModel, TimerEngine, ShotDetector,
 * SessionStatisticsCalculator, SessionJson and FileSessionRepository writing
 * real files. The ViewModel's thread hand-offs are the real ones too: the
 * main-thread Handler and a background thread for file work.
 *
 * Only the hardware edge is replaced, because a test cannot fire a gun or
 * wait out a real delay: the microphone (the test supplies 16-bit PCM
 * buffers), the beep, the clock and the delay scheduler.
 *
 * The same file runs on the JVM under Robolectric (./gradlew testDebugUnitTest)
 * and on a phone or emulator (./gradlew connectedDebugAndroidTest).
 */
@RunWith(AndroidJUnit4::class)
class ThinSliceAcceptanceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val microphonePermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @get:Rule
    val storage = TemporaryFolder()

    private val ioThreads = mutableListOf<ExecutorService>()

    @After
    fun shutDownIoThreads() {
        ioThreads.forEach { it.shutdownNow() }
    }

    /** The hardware edge for one run of the app. */
    private class Hardware {
        val microphone = FakeAudioInput()
        val beep = FakeBeepPlayer()
        val clock = FakeClock()
        val delay = FakeScheduler()
    }

    /**
     * The settings this scenario is written for. Stated here rather than taken
     * from TimerSettings.DEFAULT, whose values are placeholders that will be tuned.
     */
    private val sliceSettings = TimerSettings(
        detectionThreshold = 0.5,
        detectionCooldown = 200.milliseconds,
        startDelay = FixedDelay(3.seconds),
    )

    /**
     * Builds the app the way TimerViewModelFactory does, minus the hardware.
     * The ViewModel lives in [store], so clearing the store closes it the way
     * Android does when the app is closed.
     */
    private fun launchApp(hardware: Hardware, sessionsFolder: File, store: ViewModelStore): TimerViewModel {
        val mainHandler = Handler(Looper.getMainLooper())
        val ioThread = Executors.newSingleThreadExecutor().also { ioThreads += it }
        val factory = viewModelFactory {
            initializer {
                TimerViewModel(
                    engine = TimerEngine(
                        audioInput = hardware.microphone,
                        beepPlayer = hardware.beep,
                        clock = hardware.clock,
                        scheduler = hardware.delay,
                        initialSettings = sliceSettings,
                    ),
                    repository = FileSessionRepository(sessionsFolder),
                    postToMain = { action -> mainHandler.post(action) },
                    background = ioThread,
                )
            }
        }
        return ViewModelProvider(store, factory)[TimerViewModel::class.java]
    }

    // 16-bit PCM buffers. Full scale is 32768, so a 0.5 threshold is 16384.
    private val gunshot = shortArrayOf(120, -31000, 29500, -8000, 400)
    private val roomNoise = shortArrayOf(300, -1200, 900, -500, 150)

    /** Scrolls the screen's list until a node with [text] is composed, then checks it is shown. */
    private fun assertShown(text: String, substring: Boolean = false) {
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasText(text, substring = substring))
        composeRule.onNodeWithText(text, substring = substring).assertExists()
    }

    private fun tap(label: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(label))
        composeRule.onNodeWithText(label).performClick()
    }

    /**
     * Waits for the background save or load to reach the screen. Scrolls on
     * each try, because the row may be below the visible part of the list.
     */
    private fun waitUntilShown(text: String) {
        val deadline = System.currentTimeMillis() + 5_000
        while (true) {
            try {
                assertShown(text, substring = true)
                return
            } catch (notYet: AssertionError) {
                if (System.currentTimeMillis() > deadline) throw notYet
                Thread.sleep(50) // let the background thread finish the file work
            }
        }
    }

    @Test
    fun aTimedStringIsShownLiveSavedAndStillListedAfterTheAppIsReopened() {
        val sessionsFolder = File(storage.root, "sessions")
        val firstRun = Hardware()
        val firstRunStore = ViewModelStore()
        var app by mutableStateOf(launchApp(firstRun, sessionsFolder, firstRunStore))
        composeRule.setContent { TimerScreen(viewModel = app) }

        // Given a fresh install
        assertShown("Ready")
        waitUntilShown("No saved sessions yet.")

        // When the shooter taps Start, the timer waits out the start delay
        tap("Start")
        assertShown("Get ready — the beep is coming")
        assertEquals(3.seconds, firstRun.delay.scheduled.single().delay)

        // and the beep sounds. The beep itself is loud, but it must not count as a shot.
        composeRule.runOnIdle {
            firstRun.delay.runNext()
            firstRun.microphone.emit(gunshot)
            firstRun.clock.advance(300.milliseconds)
            firstRun.beep.finish()
        }
        assertShown("Recording — fire when ready")
        assertShown("Shots: 0    Total: —")

        // When three shots are fired (with an echo and room noise in between).
        // The two splits differ, so fastest, average and slowest are distinct.
        composeRule.runOnIdle {
            firstRun.clock.advance(950.milliseconds) // 1.25 s after the beep started
            firstRun.microphone.emit(gunshot)
            firstRun.clock.advance(50.milliseconds)
            firstRun.microphone.emit(gunshot) // echo inside the 200 ms cooldown
            firstRun.clock.advance(150.milliseconds)
            firstRun.microphone.emit(roomNoise)
            firstRun.clock.advance(200.milliseconds) // 1.65 s
            firstRun.microphone.emit(gunshot)
            firstRun.clock.advance(700.milliseconds) // 2.35 s
            firstRun.microphone.emit(gunshot)
        }

        // Then each shot appears with its time from the beep and its split,
        // and the statistics are calculated from them
        assertShown("#1   1.25 s   split —")
        assertShown("#2   1.65 s   split 0.40 s")
        assertShown("#3   2.35 s   split 0.70 s")
        assertShown("Shots: 3    Total: 2.35 s")
        assertShown("Fastest split: 0.40 s\nAverage split: 0.55 s\nSlowest split: 0.70 s")

        // When the shooter taps Stop
        tap("Stop")
        assertShown("Session complete")

        // Then the session is saved to storage and listed under Past sessions
        waitUntilShown("3 shots, total 2.35 s")
        val savedFiles = sessionsFolder.listFiles { f -> f.name.endsWith(".json") }?.map { it.name }
        assertEquals("one saved session file in $sessionsFolder", 1, savedFiles?.size)

        // When the app is closed (Android clears the ViewModel, which must
        // release the microphone) and opened again with a new ViewModel,
        // engine and repository; only the storage folder is kept
        composeRule.runOnIdle { firstRunStore.clear() }
        assertFalse("microphone still open after the app closed", firstRun.microphone.started)
        app = launchApp(Hardware(), sessionsFolder, ViewModelStore())

        // Then the new run starts clean but still lists the saved session
        assertShown("Ready")
        assertShown("Shots: 0    Total: —")
        waitUntilShown("3 shots, total 2.35 s")
        assertShown("Past sessions")
    }
}
