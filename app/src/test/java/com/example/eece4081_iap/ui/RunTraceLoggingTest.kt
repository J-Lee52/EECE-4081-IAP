package com.example.eece4081_iap.ui

import com.example.eece4081_iap.audio.AudioInputError
import com.example.eece4081_iap.audio.BeepError
import com.example.eece4081_iap.data.SessionRepository
import com.example.eece4081_iap.domain.ShootingSession
import com.example.eece4081_iap.domain.TimerSettings
import com.example.eece4081_iap.engine.FakeAudioInput
import com.example.eece4081_iap.engine.FakeBeepPlayer
import com.example.eece4081_iap.engine.FakeClock
import com.example.eece4081_iap.engine.FakeScheduler
import com.example.eece4081_iap.engine.TimerEngine
import com.example.eece4081_iap.engine.RunProblem
import com.example.eece4081_iap.engine.TimerState
import com.example.eece4081_iap.logging.CapturingEventLog
import com.example.eece4081_iap.logging.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * One timer run must be traceable through the log by its runId, across the
 * engine and the ViewModel, from Start to the saved session.
 */
class RunTraceLoggingTest {

    private class InMemoryRepository : SessionRepository {
        var failOnSave = false
        var failOnLoad = false
        val saved = mutableListOf<ShootingSession>()
        override fun save(session: ShootingSession) {
            if (failOnSave) throw IOException("disk full at /data/user/0/app/files")
            saved += session
        }
        override fun loadAll(): List<ShootingSession> {
            if (failOnLoad) throw IOException("cannot read /data/user/0/app/files")
            return saved.toList()
        }
    }

    private val log = CapturingEventLog()
    private val audio = FakeAudioInput()
    private val beep = FakeBeepPlayer()
    private val clock = FakeClock()
    private val scheduler = FakeScheduler()
    private val repository = InMemoryRepository()
    private var runCounter = 0

    private val engine = TimerEngine(
        audioInput = audio,
        beepPlayer = beep,
        clock = clock,
        scheduler = scheduler,
        newSessionId = { "session-1" },
        initialSettings = TimerSettings.DEFAULT, // threshold 0.5, cooldown 200 ms
        newRunId = { "run-${++runCounter}" },
        log = log,
    )

    private val viewModel = TimerViewModel(
        engine = engine,
        repository = repository,
        postToMain = { it() },
        background = { it.run() },
        log = log,
    )

    private val loud = shortArrayOf(0, 31000, -29500) // peak 0.946
    private val quiet = shortArrayOf(0, 9800, -7000) // peak 0.299, below the 0.5 threshold

    private fun startAndReachRecording() {
        viewModel.start()
        scheduler.runNext() // delay over, beep starts
        clock.advance(300.milliseconds)
        beep.finish()
    }

    @Test
    fun aCompleteRun_isTraceableFromStartToTheSavedSessionByOneRunId() {
        log.records.clear() // ignore the history load at startup

        startAndReachRecording()
        clock.advance(950.milliseconds)
        audio.emit(loud) // shot 1 at 1.25 s
        clock.advance(400.milliseconds)
        audio.emit(loud) // shot 2 at 1.65 s
        viewModel.stop()

        assertEquals(
            listOf(
                "run.started", "state.changed", "microphone.opened", "state.changed",
                "beep.started", "state.changed", "shot.recorded", "shot.recorded",
                "audio.level", "run.stopped", "state.changed",
                "session.saved", "history.loaded",
            ),
            log.events(),
        )
        assertTrue("every line carries the same runId", log.records.all { it.runId == "run-1" })
        assertTrue(
            "the run is traced across components",
            log.records.map { it.component }.toSet() == setOf("engine", "viewmodel"),
        )
        assertEquals("session-1", log.only("session.saved").sessionId)
        assertEquals(400L, log.records.filter { it.event == "shot.recorded" }[1].data["splitMs"])
    }

    @Test
    fun sessionId_appearsOnlyOnceTheBeepHasCreatedTheSession() {
        log.records.clear()
        startAndReachRecording()

        val beforeBeep = log.records.takeWhile { it.event != "beep.started" }
        assertTrue(beforeBeep.isNotEmpty() && beforeBeep.all { it.sessionId == null })
        assertEquals("session-1", log.only("beep.started").sessionId)
    }

    @Test
    fun eachStart_getsANewRunId() {
        startAndReachRecording()
        viewModel.stop()
        startAndReachRecording()

        val runIds = log.records.filter { it.event == "run.started" }.map { it.runId }
        assertEquals(listOf("run-1", "run-2"), runIds)
        assertNotEquals(runIds[0], runIds[1])
    }

    @Test
    fun soundsBelowTheThreshold_areReportedSoAnOperatorCanSeeWhyNoShotCounted() {
        startAndReachRecording()
        repeat(12) {
            clock.advance(100.milliseconds)
            audio.emit(quiet)
        }
        viewModel.stop()

        assertTrue(log.records.none { it.event == "shot.recorded" })
        val level = log.records.first { it.event == "audio.level" }
        assertEquals(0.299, level.data["maxPeak"])
        assertEquals(0.5, level.data["threshold"])
        val stopped = log.only("run.stopped")
        assertEquals(0, stopped.data["shots"])
        assertEquals(12, stopped.data["buffersHeard"])
        assertEquals(LogLevel.INFO, log.only("session.not_saved").level)
    }

    @Test
    fun noAudioArriving_showsAsZeroBuffersHeard() {
        startAndReachRecording()
        clock.advance(5.seconds)
        viewModel.stop()

        assertEquals(0, log.only("run.stopped").data["buffersHeard"])
        assertTrue(log.records.none { it.event == "audio.level" })
    }

    @Test
    fun echoesInsideTheCooldown_areCountedInAudioLevelNotLoggedOneByOne() {
        startAndReachRecording()
        clock.advance(1.seconds)
        audio.emit(loud) // shot
        clock.advance(50.milliseconds)
        audio.emit(loud) // echo
        clock.advance(20.milliseconds)
        audio.emit(loud) // echo
        viewModel.stop()

        assertTrue(log.records.none { it.event == "shot.ignored" })
        assertEquals(2, log.records.filter { it.event == "audio.level" }.sumOf { it.data["ignoredCooldown"] as Int })
        assertEquals(2, log.only("run.stopped").data["ignoredCooldown"])
    }

    @Test
    fun inVerboseMode_eachEchoIsAlsoLoggedWithItsReason() {
        log.detail = true
        startAndReachRecording()
        clock.advance(1.seconds)
        audio.emit(loud)
        clock.advance(50.milliseconds)
        audio.emit(loud) // echo

        val ignored = log.only("shot.ignored")
        assertEquals("cooldown", ignored.data["reason"])
        assertEquals(LogLevel.DEBUG, ignored.level)
    }

    @Test
    fun aLongNoisyRun_staysAtAboutOneAudioRecordPerSecond() {
        startAndReachRecording()
        // 60 s of a loud sound every 50 ms: one counted shot per 200 ms
        // cooldown, the other three in each 200 ms ignored as echoes.
        repeat(1200) {
            clock.advance(50.milliseconds)
            audio.emit(loud)
        }
        viewModel.stop()

        // A window closes once a full second has passed since its first
        // buffer, so with 50 ms buffers each covers 1.05 s: 57 full windows
        // plus the partial one flushed at stop. Never more than one a second.
        val audioRecords = log.records.filter { it.event == "audio.level" }
        assertTrue("about one per second, was ${audioRecords.size}", audioRecords.size in 55..60)
        assertTrue(log.records.none { it.event == "shot.ignored" })
        assertEquals(900, log.only("run.stopped").data["ignoredCooldown"])
    }

    @Test
    fun microphoneFailure_isAnErrorOnTheRunsTrace() {
        audio.failOnStart = SecurityException("RECORD_AUDIO not granted")

        viewModel.start()

        val failure = log.only("microphone.open_failed")
        assertEquals(LogLevel.ERROR, failure.level)
        assertEquals("run-1", failure.runId)
        assertEquals("SecurityException", failure.data["error"])
        assertNull(failure.sessionId)
    }

    @Test
    fun aMicrophoneReadError_isOnTheAffectedRunsTrace() {
        startAndReachRecording()

        audio.fail(AudioInputError(-6, "AudioRecord.read returned -6"))

        val failure = log.only("microphone.read_error")
        assertEquals(LogLevel.ERROR, failure.level)
        assertEquals("engine", failure.component)
        assertEquals("run-1", failure.runId)
        assertEquals("session-1", failure.sessionId)
        assertEquals(-6, failure.data["code"])
        assertEquals(TimerState.Recording, failure.data["state"])
    }

    @Test
    fun aLateReadError_isCreditedToTheRunThatOwnedTheMicrophone() {
        startAndReachRecording()
        val firstRunsCapture = audio.captureErrorCallback()
        viewModel.stop()
        startAndReachRecording() // run-2 is now current

        firstRunsCapture(AudioInputError(-6, "AudioRecord.read returned -6"))

        val failure = log.only("microphone.read_error")
        assertEquals("run-1", failure.runId)
        assertNull("not attached to the newer run's session", failure.sessionId)
        assertNull(failure.data["state"])

        // ...and it neither changes nor ends the newer run, which keeps counting shots.
        assertEquals(TimerState.Recording, engine.snapshot().state)
        assertNull(engine.snapshot().problem)
        assertNull(viewModel.errorMessage)
        clock.advance(1.seconds)
        audio.emit(loud)
        assertEquals(1, engine.snapshot().statistics.shotCount)
    }

    @Test
    fun lateSamplesFromAnEarlierRunsCapture_areNotCountedInANewerRun() {
        startAndReachRecording()
        val firstRunsSamples = audio.captureSamplesCallback()
        viewModel.stop()
        startAndReachRecording() // run-2

        clock.advance(1.seconds)
        firstRunsSamples(loud)
        assertEquals(0, engine.snapshot().statistics.shotCount)

        audio.emit(loud)
        assertEquals(1, engine.snapshot().statistics.shotCount)
    }

    @Test
    fun aReadErrorWhileRecording_endsTheRunKeepsItsShotsAndWarnsThePerson() {
        startAndReachRecording()
        clock.advance(1.seconds)
        audio.emit(loud)

        audio.fail(AudioInputError(-6, "AudioRecord.read returned -6"))

        assertEquals(TimerState.Completed, engine.snapshot().state)
        assertEquals(RunProblem.MICROPHONE_LOST, engine.snapshot().problem)
        assertFalse(audio.started)
        assertEquals("microphone_lost", log.only("run.stopped").data["reason"])
        assertEquals("the shot before the failure is saved", 1, repository.saved.single().shots.size)
        assertTrue(viewModel.errorMessage!!.contains("microphone stopped working"))
    }

    @Test
    fun anUnavailableBeep_abandonsTheRunBeforeRecordingAndWarnsThePerson() {
        viewModel.start()
        scheduler.runNext()

        beep.reportUnavailable(BeepError("RuntimeException", "no tone generator"))

        assertEquals(TimerState.Idle, engine.snapshot().state)
        assertEquals(RunProblem.BEEP_UNAVAILABLE, engine.snapshot().problem)
        assertNull(engine.snapshot().session)
        assertFalse("microphone released", audio.started)
        assertEquals(RunProblem.BEEP_UNAVAILABLE, log.only("run.abandoned").data["reason"])
        assertTrue(viewModel.errorMessage!!.contains("start beep could not be played"))

        beep.finish() // the player still reports "finished" afterwards
        assertEquals("never records without the start signal", TimerState.Idle, engine.snapshot().state)
    }

    @Test
    fun aStaleDelayCallback_cannotStartTheBeepOfALaterRun() {
        viewModel.start() // run-1 schedules its delay
        audio.fail(AudioInputError(-6, "lost")) // run-1 is abandoned while waiting
        viewModel.start() // run-2 schedules its own delay

        scheduler.runNext() // run-1's delay fires late
        assertEquals(0, beep.playCount)
        assertEquals(TimerState.WaitingForStart, engine.snapshot().state)
        val ignored = log.only("callback.ignored")
        assertEquals("delay", ignored.data["callback"])
        assertEquals("run-1", ignored.runId)

        scheduler.runNext() // run-2's own delay
        assertEquals(1, beep.playCount)
    }

    @Test
    fun aStaleBeepFinished_cannotStartRecordingInALaterRun() {
        viewModel.start()
        scheduler.runNext() // run-1's beep is playing
        audio.fail(AudioInputError(-6, "lost")) // run-1 abandoned
        viewModel.start() // run-2 waiting for its delay

        beep.finish() // run-1's beep reports finished late

        assertEquals(TimerState.WaitingForStart, engine.snapshot().state)
        assertEquals("beep_finished", log.records.last { it.event == "callback.ignored" }.data["callback"])
    }

    @Test
    fun aLoggerThatThrows_neverInterruptsTimingStateChangesOrSaving() {
        val brokenLog = com.example.eece4081_iap.logging.EventLog { throw IllegalStateException("log full") }
        val brokenAudio = FakeAudioInput()
        val brokenBeep = FakeBeepPlayer()
        val brokenScheduler = FakeScheduler()
        val store = InMemoryRepository()
        val engine = TimerEngine(brokenAudio, brokenBeep, clock, brokenScheduler, log = brokenLog)
        val viewModel = TimerViewModel(engine, store, { it() }, { it.run() }, log = brokenLog)

        viewModel.start()
        brokenScheduler.runNext()
        brokenBeep.finish()
        clock.advance(1.seconds)
        brokenAudio.emit(loud)
        viewModel.stop()

        assertEquals(TimerState.Completed, engine.snapshot().state)
        assertEquals(1, store.saved.single().shots.size)
    }

    @Test
    fun exceptionMessages_areKeptOutOfNormalLogData() {
        audio.failOnStart = SecurityException("Permission denial: uid 10123 /dev/snd")

        viewModel.start()

        val failure = log.only("microphone.open_failed")
        assertFalse(failure.data.containsKey("message"))
        assertEquals("SecurityException", failure.data["error"])
        assertEquals("Permission denial: uid 10123 /dev/snd", failure.diagnostics["message"])
    }

    @Test
    fun aHistoryLoadFailureAtStartup_isLoggedAndShownWithoutCrashing() {
        val failing = InMemoryRepository().apply { failOnLoad = true }
        val failingLog = CapturingEventLog()

        val viewModel = TimerViewModel(engine, failing, { it() }, { it.run() }, log = failingLog)

        assertEquals("Could not load past sessions.", viewModel.errorMessage)
        assertTrue(viewModel.history.isEmpty())
        assertEquals("IOException", failingLog.only("history.load_failed").data["error"])
    }

    @Test
    fun aHistoryLoadFailureAfterSaving_keepsTheCompletedSessionOnScreen() {
        startAndReachRecording()
        clock.advance(1.seconds)
        audio.emit(loud)
        repository.failOnLoad = true

        viewModel.stop()

        assertEquals("the session itself was saved", 1, repository.saved.size)
        assertEquals(TimerState.Completed, viewModel.snapshot.state)
        assertEquals(1, viewModel.snapshot.statistics.shotCount)
        assertEquals("Could not load past sessions.", viewModel.errorMessage)
        assertEquals("run-1", log.only("history.load_failed").runId)
    }

    @Test
    fun anUnavailableBeep_isOnTheRunsTrace() {
        viewModel.start()
        scheduler.runNext() // delay over, the engine asks for the beep

        beep.reportUnavailable(BeepError("RuntimeException", "no tone generator"))

        val warning = log.only("beep.unavailable")
        assertEquals(LogLevel.WARN, warning.level)
        assertEquals("engine", warning.component)
        assertEquals("run-1", warning.runId)
        assertEquals("session-1", warning.sessionId)
        assertEquals("RuntimeException", warning.data["error"])
    }

    @Test
    fun aFailedSave_isAnErrorCarryingBothIds() {
        repository.failOnSave = true
        startAndReachRecording()
        clock.advance(1.seconds)
        audio.emit(loud)
        viewModel.stop()

        val failure = log.only("session.save_failed")
        assertEquals(LogLevel.ERROR, failure.level)
        assertEquals("run-1", failure.runId)
        assertEquals("session-1", failure.sessionId)
    }

    @Test
    fun deniedPermission_isLoggedAsAWarning() {
        viewModel.microphonePermissionDenied()

        assertEquals(LogLevel.WARN, log.only("permission.denied").level)
    }
}
