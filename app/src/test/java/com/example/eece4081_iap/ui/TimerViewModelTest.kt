package com.example.eece4081_iap.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class TimerViewModelTest {

    /** In-memory stand-in for file storage. */
    private class InMemoryRepository(initial: List<ShootingSession> = emptyList()) : SessionRepository {
        val saved = initial.toMutableList()
        var failOnSave = false
        var failOnDelete = false
        var saveCalls = 0

        override fun save(session: ShootingSession) {
            saveCalls++
            if (failOnSave) throw IOException("disk full")
            saved.removeAll { it.id == session.id }
            saved.add(session)
        }

        override fun loadAll(): List<ShootingSession> = saved.sortedByDescending { it.startTime }

        override fun delete(id: String) {
            if (failOnDelete) throw IOException("read-only")
            saved.removeAll { it.id == id }
        }
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
        background = { it.run() },
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
    fun aCompletedSession_isNotSavedUntilThePersonSavesIt() {
        val repository = InMemoryRepository()
        val viewModel = viewModel(repository)

        recordOneShotAndStop(viewModel)

        assertTrue("not saved automatically", repository.saved.isEmpty())
        assertFalse(viewModel.currentSessionSaved)
        assertTrue(viewModel.needsSaveDecision)

        viewModel.saveCurrentSession()

        val saved = repository.saved.single()
        assertEquals(1, saved.shots.size)
        assertEquals(listOf(saved), viewModel.history)
        assertTrue(viewModel.currentSessionSaved)
        assertFalse(viewModel.needsSaveDecision)
    }

    @Test
    fun aCompletedSessionWithNoShots_canBeSaved() {
        val repository = InMemoryRepository()
        val viewModel = viewModel(repository)
        reachRecording(viewModel)
        viewModel.stop()

        viewModel.saveCurrentSession()

        assertEquals(0, repository.saved.single().shots.size)
        assertEquals(1, viewModel.history.size)
    }

    @Test
    fun aSessionIsSavedOnlyOnce() {
        val repository = InMemoryRepository()
        val viewModel = viewModel(repository)
        recordOneShotAndStop(viewModel)

        viewModel.saveCurrentSession()
        viewModel.saveCurrentSession() // already saved: ignored

        assertEquals(1, repository.saveCalls)
    }

    @Test
    fun thereIsNothingToSaveBeforeASessionIsCompleted() {
        val repository = InMemoryRepository()
        val viewModel = viewModel(repository)
        reachRecording(viewModel)

        viewModel.saveCurrentSession()

        assertFalse(viewModel.needsSaveDecision)
        assertEquals(0, repository.saveCalls)
    }

    @Test
    fun saveAndStart_startsTheNextSessionOnlyAfterTheSaveSucceeds() {
        val repository = InMemoryRepository()
        val viewModel = viewModel(repository)
        recordOneShotAndStop(viewModel)
        var started = false

        viewModel.saveCurrentSession(afterSaved = { started = true })

        assertTrue(started)
        assertEquals(1, repository.saved.size)
    }

    @Test
    fun saveAndStart_whenTheSaveFails_doesNotStartAndKeepsTheSessionOnScreen() {
        val repository = InMemoryRepository().apply { failOnSave = true }
        val viewModel = viewModel(repository)
        recordOneShotAndStop(viewModel)
        var started = false

        viewModel.saveCurrentSession(afterSaved = { started = true })

        assertFalse(started)
        assertEquals(TimerState.Completed, viewModel.snapshot.state)
        assertTrue("can still be saved or discarded", viewModel.needsSaveDecision)
    }

    @Test
    fun discardingThenStarting_beginsANewSessionWithoutSavingTheOldOne() {
        val repository = InMemoryRepository()
        val viewModel = viewModel(repository)
        recordOneShotAndStop(viewModel)

        viewModel.discardCurrentSession()
        viewModel.start()

        assertTrue(repository.saved.isEmpty())
        assertEquals(TimerState.WaitingForStart, viewModel.snapshot.state)
        assertFalse(viewModel.needsSaveDecision)
    }

    @Test
    fun deletingASavedSession_removesItFromStorageAndHistory() {
        val older = ShootingSession("old", 500L, TimerSettings.DEFAULT)
        val repository = InMemoryRepository(listOf(older))
        val viewModel = viewModel(repository)
        var afterDeleted = false

        viewModel.deleteSession("old", afterDeleted = { afterDeleted = true })

        assertTrue(repository.saved.isEmpty())
        assertTrue(viewModel.history.isEmpty())
        assertTrue(afterDeleted)
    }

    @Test
    fun deletingTheJustSavedSession_meansItIsUnsavedAgain() {
        val repository = InMemoryRepository()
        val viewModel = viewModel(repository)
        recordOneShotAndStop(viewModel)
        viewModel.saveCurrentSession()

        viewModel.deleteSession(viewModel.snapshot.session!!.id)

        assertFalse(viewModel.currentSessionSaved)
        assertTrue(viewModel.needsSaveDecision)
    }

    @Test
    fun aFailedDelete_showsAnErrorAndKeepsTheSession() {
        val older = ShootingSession("old", 500L, TimerSettings.DEFAULT)
        val repository = InMemoryRepository(listOf(older)).apply { failOnDelete = true }
        val viewModel = viewModel(repository)
        var afterDeleted = false

        viewModel.deleteSession("old", afterDeleted = { afterDeleted = true })

        assertEquals("Could not delete the session.", viewModel.errorMessage)
        assertEquals(listOf(older), viewModel.history)
        assertFalse(afterDeleted)
    }

    @Test
    fun aFailedSave_showsAnError() {
        val repository = InMemoryRepository().apply { failOnSave = true }
        val viewModel = viewModel(repository)
        recordOneShotAndStop(viewModel)

        viewModel.saveCurrentSession()

        assertNotNull(viewModel.errorMessage)
        assertTrue(viewModel.history.isEmpty())
        assertFalse("it can be tried again", viewModel.currentSessionSaved)
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

    /** Creates the ViewModel inside [store], so clearing the store calls onCleared() as Android does. */
    private fun viewModelIn(store: ViewModelStore, repository: SessionRepository): TimerViewModel =
        ViewModelProvider(store, viewModelFactory { initializer { viewModel(repository) } })[TimerViewModel::class.java]

    @Test
    fun closingTheAppWhileRecording_releasesTheMicrophone() {
        val store = ViewModelStore()
        val viewModel = viewModelIn(store, InMemoryRepository())
        reachRecording(viewModel)
        assertTrue(audio.started)

        store.clear() // what Android does when the screen is finished

        assertFalse(audio.started)
        assertEquals(TimerState.Completed, engine.snapshot().state)
    }

    @Test
    fun closingTheAppDuringTheStartDelay_releasesTheMicrophone() {
        val store = ViewModelStore()
        val viewModel = viewModelIn(store, InMemoryRepository())
        viewModel.start() // waiting for the start delay
        assertTrue(audio.started)

        store.clear()

        assertFalse("microphone must not stay open after the screen is gone", audio.started)
        assertEquals(TimerState.Idle, engine.snapshot().state)
    }

    @Test
    fun closingTheAppWhileTheBeepPlays_releasesTheMicrophoneAndNeverRecords() {
        val store = ViewModelStore()
        val viewModel = viewModelIn(store, InMemoryRepository())
        viewModel.start()
        scheduler.runNext() // beep playing

        store.clear()
        beep.finish() // the beep ends after the screen has gone

        assertFalse(audio.started)
        assertEquals(TimerState.Idle, engine.snapshot().state)
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
