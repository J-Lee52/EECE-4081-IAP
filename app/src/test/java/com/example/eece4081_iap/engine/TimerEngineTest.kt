package com.example.eece4081_iap.engine

import com.example.eece4081_iap.domain.FixedDelay
import com.example.eece4081_iap.domain.TimerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TimerEngineTest {

    private val audio = FakeAudioInput()
    private val beep = FakeBeepPlayer()
    private val clock = FakeClock()
    private val scheduler = FakeScheduler()

    private val settings = TimerSettings(
        detectionThreshold = 0.5,
        detectionCooldown = 200.milliseconds,
        startDelay = FixedDelay(3.seconds),
    )

    private var idCounter = 0

    private val engine = TimerEngine(
        audioInput = audio,
        beepPlayer = beep,
        clock = clock,
        scheduler = scheduler,
        newSessionId = { "session-${++idCounter}" },
        initialSettings = settings,
    )

    // Peak 20000 / 32768 = 0.61, above the 0.5 threshold.
    private val loud = shortArrayOf(0, 20000, -20000)
    private val quiet = shortArrayOf(0, 100, -100)

    private fun state() = engine.snapshot().state
    private fun shots() = engine.snapshot().session!!.shots

    /** Drives the engine from Idle to Recording. The beep starts at the current clock time. */
    private fun startAndReachRecording() {
        assertTrue(engine.start())
        scheduler.runNext() // start delay elapsed, beep begins
        beep.finish() // beep finished
    }

    // --- State transitions (NFR 03, US02, US03) ---

    @Test
    fun newEngine_isIdleWithNoSession() {
        val snapshot = engine.snapshot()

        assertEquals(TimerState.Idle, snapshot.state)
        assertNull(snapshot.session)
        assertEquals(0, snapshot.statistics.shotCount)
    }

    @Test
    fun start_fromIdle_waitsForStartAndOpensTheMicrophone() {
        assertTrue(engine.start())

        assertEquals(TimerState.WaitingForStart, state())
        assertTrue(audio.started)
        assertEquals(3.seconds, scheduler.scheduled.single().delay)
        assertEquals(0, beep.playCount)
    }

    @Test
    fun delayElapsed_playsTheBeepButStaysWaitingUntilItFinishes() {
        engine.start()
        scheduler.runNext()

        assertEquals(1, beep.playCount)
        assertEquals(TimerState.WaitingForStart, state())
    }

    @Test
    fun beepFinished_entersRecording() {
        startAndReachRecording()

        assertEquals(TimerState.Recording, state())
    }

    @Test
    fun start_whileASessionIsActive_isRejected() {
        engine.start()

        assertFalse(engine.start())
        assertEquals(1, scheduler.scheduled.size)
        assertEquals(1, audio.startCount)
    }

    @Test
    fun listener_seesEachStateInOrder() {
        val states = mutableListOf<TimerState>()
        engine.listener = { states.add(it.state) }

        startAndReachRecording()
        engine.stop()

        assertEquals(
            listOf(
                TimerState.Preparing,
                TimerState.WaitingForStart,
                TimerState.Recording,
                TimerState.Completed,
            ),
            states,
        )
    }

    // --- Beep suppression (US03) ---

    @Test
    fun audioBeforeAndDuringTheBeep_isNotRecordedAsAShot() {
        engine.start()
        audio.emit(loud) // during the start delay

        scheduler.runNext()
        audio.emit(loud) // while the beep is playing

        beep.finish()

        assertEquals(TimerState.Recording, state())
        assertTrue(shots().isEmpty())
    }

    // --- Shot detection and timing (US04, US05) ---

    @Test
    fun shotAfterTheBeep_isTimedFromTheStartOfTheBeep() {
        engine.start()
        clock.elapsedNow = 10.seconds
        scheduler.runNext() // beep starts at 10 s
        clock.advance(150.milliseconds)
        beep.finish()
        clock.advance(1200.milliseconds) // now 11.35 s

        audio.emit(loud)

        val shot = shots().single()
        assertEquals(1, shot.shotNumber)
        assertEquals(1350.milliseconds, shot.timestamp)
        assertNull(shot.splitTime)
    }

    @Test
    fun quietAudio_isNotRecorded() {
        startAndReachRecording()
        clock.advance(1.seconds)

        audio.emit(quiet)

        assertTrue(shots().isEmpty())
    }

    @Test
    fun secondLoudBufferInsideTheCooldown_isIgnored() {
        startAndReachRecording()
        clock.advance(1.seconds)
        audio.emit(loud)

        clock.advance(100.milliseconds)
        audio.emit(loud)

        assertEquals(1, shots().size)
    }

    @Test
    fun secondShotAfterTheCooldown_getsASplitFromTheFirst() {
        startAndReachRecording()
        clock.advance(1.seconds)
        audio.emit(loud)
        clock.advance(100.milliseconds)
        audio.emit(loud) // ignored, inside the cooldown

        clock.advance(150.milliseconds)
        audio.emit(loud)

        val recorded = shots()
        assertEquals(2, recorded.size)
        assertEquals(2, recorded[1].shotNumber)
        assertEquals(250.milliseconds, recorded[1].splitTime)
    }

    @Test
    fun snapshotStatistics_updateWithEachShot() {
        startAndReachRecording()
        clock.advance(1000.milliseconds)
        audio.emit(loud)
        clock.advance(500.milliseconds)
        audio.emit(loud)
        clock.advance(750.milliseconds)
        audio.emit(loud)

        val stats = engine.snapshot().statistics

        assertEquals(3, stats.shotCount)
        assertEquals(2250.milliseconds, stats.totalTime)
        assertEquals(500.milliseconds, stats.fastestSplit)
        assertEquals(625.milliseconds, stats.averageSplit)
        assertEquals(750.milliseconds, stats.slowestSplit)
    }

    @Test
    fun listener_isNotifiedWhenAShotIsRecorded() {
        startAndReachRecording()
        val shotCounts = mutableListOf<Int>()
        engine.listener = { shotCounts.add(it.statistics.shotCount) }

        clock.advance(1.seconds)
        audio.emit(loud)

        assertEquals(listOf(1), shotCounts)
    }

    // --- Stopping (US06) ---

    @Test
    fun stop_whileRecording_completesTheSessionAndReleasesTheMicrophone() {
        startAndReachRecording()
        clock.advance(1.seconds)
        audio.emit(loud)

        assertTrue(engine.stop())

        assertEquals(TimerState.Completed, state())
        assertFalse(audio.started)
        assertEquals(1, audio.stopCount)
        assertEquals(1, shots().size)
    }

    @Test
    fun audioAfterStop_isNotRecordedAndTheResultsDoNotChange() {
        startAndReachRecording()
        clock.advance(1.seconds)
        audio.emit(loud)
        engine.stop()
        val before = engine.snapshot()

        clock.advance(1.seconds)
        audio.emit(loud) // a late buffer after stop()

        assertEquals(before, engine.snapshot())
    }

    @Test
    fun stop_whenNotRecording_isRejectedAndChangesNothing() {
        assertFalse(engine.stop()) // Idle
        assertEquals(TimerState.Idle, state())

        engine.start()
        assertFalse(engine.stop()) // WaitingForStart
        assertEquals(TimerState.WaitingForStart, state())
        assertEquals(0, audio.stopCount)
    }

    // --- Starting again from Completed (US02, US06) ---

    @Test
    fun start_fromCompleted_beginsANewSessionWithNoShots() {
        startAndReachRecording()
        clock.advance(1.seconds)
        audio.emit(loud)
        engine.stop()

        assertTrue(engine.start())

        val snapshot = engine.snapshot()
        assertEquals(TimerState.WaitingForStart, snapshot.state)
        assertNull(snapshot.session)
        assertEquals(0, snapshot.statistics.shotCount)

        scheduler.runNext()
        beep.finish()
        clock.advance(1.seconds)
        audio.emit(loud)

        val session = engine.snapshot().session!!
        assertEquals("session-2", session.id)
        assertEquals(1, session.shots.size)
        assertEquals(1, session.shots[0].shotNumber)
    }

    // --- Settings (US01) ---

    @Test
    fun updateSettings_isAllowedInIdleAndCompletedButNotWhileActive() {
        val changed = settings.copy(detectionThreshold = 0.9)

        assertTrue(engine.updateSettings(changed)) // Idle
        assertEquals(changed, engine.currentSettings())

        engine.start()
        assertFalse(engine.updateSettings(settings)) // WaitingForStart
        scheduler.runNext()
        beep.finish()
        assertFalse(engine.updateSettings(settings)) // Recording
        assertEquals(changed, engine.currentSettings())

        engine.stop()
        assertTrue(engine.updateSettings(settings)) // Completed
        assertEquals(settings, engine.currentSettings())
    }

    @Test
    fun session_usesTheSettingsSelectedBeforeItStarted() {
        val strict = settings.copy(detectionThreshold = 0.9)
        engine.updateSettings(strict)

        startAndReachRecording()
        clock.advance(1.seconds)
        audio.emit(loud) // 0.61 is below the 0.9 threshold

        assertTrue(shots().isEmpty())
        assertEquals(strict, engine.snapshot().session!!.settings)
    }

    // --- Microphone failure ---

    @Test
    fun microphoneFailure_returnsToIdleAndSchedulesNothing() {
        audio.failOnStart = SecurityException("RECORD_AUDIO not granted")

        assertFalse(engine.start())

        assertEquals(TimerState.Idle, state())
        assertTrue(scheduler.scheduled.isEmpty())
        assertNotNull(engine.snapshot())
    }
}
