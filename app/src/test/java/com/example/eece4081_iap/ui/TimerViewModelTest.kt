package com.example.eece4081_iap.ui

import com.example.eece4081_iap.data.SessionRepository
import com.example.eece4081_iap.domain.ShootingSession
import com.example.eece4081_iap.domain.TimerSettings
import com.example.eece4081_iap.engine.FakeAudioInput
import com.example.eece4081_iap.engine.FakeBeepPlayer
import com.example.eece4081_iap.engine.FakeClock
import com.example.eece4081_iap.engine.FakeScheduler
import com.example.eece4081_iap.engine.TimerEngine
import com.example.eece4081_iap.engine.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executor
import kotlin.time.Duration.Companion.seconds

class TimerViewModelTest {

    /** In-memory stand-in for file storage. */
    private class InMemoryRepository(initial: List<ShootingSession> = emptyList()) : SessionRepository {
        val saved = initial.toMutableList()
        var failOnSave = false

        override fun save(session: ShootingSession) {
            if (failOnSave) throw IOException("disk full")
            saved.removeAll { it.id == session.id }
            saved.add(session)
        }

        override fun loadAll(): List<ShootingSession> = saved.sortedByDescending { it.startTime }
    }

    private val audio = FakeAudioInput()
    private val beep = FakeBeepPlayer()
    private val clock = FakeClock()
    private val scheduler = FakeScheduler()
    private var idCounter = 0

    private val engine = TimerEngine(
        audioInput = audio,
        beepPlayer = beep,
        clock = clock,
        scheduler = scheduler,
        newSessionId = { "session-${++idCounter}" },
        initialSettings = TimerSettings.DEFAULT,
    )

    // Peak 20000 / 32768 = 0.61, above the default 0.5 threshold.
    private val loud = shortArrayOf(0, 20000, -20000)

    /** Runs everything immediately and in order. */
    private fun viewModel(repository: SessionRepository) = TimerViewModel(
        engine = engine,
        repository = repository,
        postToMain = { it() },
        background = Executor { it.run() },
    )

    private fun reachRecording(viewModel: TimerViewModel) {
        viewModel.start()
        scheduler.runNext()
        beep.finish()
    }

    private fun recordOneShotAndStop(viewModel: TimerViewModel) {
        reachRecording(viewModel)
        clock.advance(1.seconds)
        audio.emit(loud)
        viewModel.stop()
    }

    @Test
    fun newViewModel_isIdleWithNoErrorAndEmptyHistory() {
        val viewModel = viewModel(InMemoryRepository())

        assertEquals(TimerState.Idle, viewModel.snapshot.state)
        assertNull(viewModel.errorMessage)
        assertTrue(viewModel.history.isEmpty())
    }

    @Test
    fun history_isLoadedFromTheRepositoryAtStartup() {
        val earlier = ShootingSession("old", 500L, TimerSettings.DEFAULT)
        val viewModel = viewModel(InMemoryRepository(listOf(earlier)))

        assertEquals(listOf(earlier), viewModel.history)
    }

    @Test
    fun snapshot_followsTheEngineThroughASession() {
        val viewModel = viewModel(InMemoryRepository())

        viewModel.start()
        assertEquals(TimerState.WaitingForStart, viewModel.snapshot.state)

        scheduler.runNext()
        beep.finish()
        assertEquals(TimerState.Recording, viewModel.snapshot.state)

        clock.advance(1.seconds)
        audio.emit(loud)
        assertEquals(1, viewModel.snapshot.statistics.shotCount)

        viewModel.stop()
        assertEquals(TimerState.Completed, viewModel.snapshot.state)
    }

    @Test
    fun completingASessionWithShots_savesItAndShowsItInHistory() {
        val repository = InMemoryRepository()
        val viewModel = viewModel(repository)

        recordOneShotAndStop(viewModel)

        val saved = repository.saved.single()
        assertEquals(1, saved.shots.size)
        assertEquals(listOf(saved), viewModel.history)
    }

    @Test
    fun completingASessionWithNoShots_doesNotSaveIt() {
        val repository = InMemoryRepository()
        val viewModel = viewModel(repository)

        reachRecording(viewModel)
        viewModel.stop()

        assertEquals(TimerState.Completed, viewModel.snapshot.state)
        assertTrue(repository.saved.isEmpty())
        assertTrue(viewModel.history.isEmpty())
    }

    @Test
    fun aSessionIsSavedOnlyOnce() {
        val repository = InMemoryRepository()
        val viewModel = viewModel(repository)

        recordOneShotAndStop(viewModel)
        viewModel.stop() // rejected by the engine, changes nothing

        assertEquals(1, repository.saved.size)
    }

    @Test
    fun aFailedSave_showsAnError() {
        val repository = InMemoryRepository().apply { failOnSave = true }
        val viewModel = viewModel(repository)

        recordOneShotAndStop(viewModel)

        assertNotNull(viewModel.errorMessage)
        assertTrue(viewModel.history.isEmpty())
    }

    @Test
    fun microphoneFailure_showsAnErrorAndStaysIdle() {
        audio.failOnStart = SecurityException("RECORD_AUDIO not granted")
        val viewModel = viewModel(InMemoryRepository())

        viewModel.start()

        assertNotNull(viewModel.errorMessage)
        assertEquals(TimerState.Idle, viewModel.snapshot.state)
    }

    @Test
    fun startingAgain_clearsAnEarlierError() {
        audio.failOnStart = SecurityException("RECORD_AUDIO not granted")
        val viewModel = viewModel(InMemoryRepository())
        viewModel.start()
        assertNotNull(viewModel.errorMessage)

        audio.failOnStart = null
        viewModel.start()

        assertNull(viewModel.errorMessage)
        assertEquals(TimerState.WaitingForStart, viewModel.snapshot.state)
    }

    @Test
    fun dismissError_clearsTheMessage() {
        audio.failOnStart = SecurityException("RECORD_AUDIO not granted")
        val viewModel = viewModel(InMemoryRepository())
        viewModel.start()

        viewModel.dismissError()

        assertNull(viewModel.errorMessage)
    }

    @Test
    fun startingWhileASessionIsActive_isNotReportedAsAMicrophoneError() {
        val viewModel = viewModel(InMemoryRepository())
        viewModel.start()

        viewModel.start() // rejected: a session is already active

        assertNull(viewModel.errorMessage)
        assertEquals(TimerState.WaitingForStart, viewModel.snapshot.state)
    }
}
