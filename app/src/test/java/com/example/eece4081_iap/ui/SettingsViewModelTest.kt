package com.example.eece4081_iap.ui

import com.example.eece4081_iap.data.SessionRepository
import com.example.eece4081_iap.data.SettingsRepository
import com.example.eece4081_iap.domain.RandomDelay
import com.example.eece4081_iap.domain.ShootingSession
import com.example.eece4081_iap.domain.TimerSettings
import com.example.eece4081_iap.engine.FakeAudioInput
import com.example.eece4081_iap.engine.FakeBeepPlayer
import com.example.eece4081_iap.engine.FakeClock
import com.example.eece4081_iap.engine.FakeScheduler
import com.example.eece4081_iap.engine.TimerEngine
import com.example.eece4081_iap.logging.CapturingEventLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** US01: choosing settings for the next session, and remembering them. */
class SettingsViewModelTest {

    private class MemorySettings(var stored: TimerSettings? = null) : SettingsRepository {
        var failOnSave = false
        var failOnLoad = false
        override fun load(): TimerSettings? {
            if (failOnLoad) throw IOException("unreadable")
            return stored
        }
        override fun save(settings: TimerSettings) {
            if (failOnSave) throw IOException("disk full")
            stored = settings
        }
    }

    private object NoSessions : SessionRepository {
        override fun save(session: ShootingSession) = Unit
        override fun loadAll(): List<ShootingSession> = emptyList()
    }

    private val scheduler = FakeScheduler()
    private val beep = FakeBeepPlayer()
    private val engine = TimerEngine(FakeAudioInput(), beep, FakeClock(), scheduler)
    private val log = CapturingEventLog()
    private val custom = TimerSettings(0.35, 150.milliseconds, RandomDelay(2.seconds, 4.seconds))

    private fun viewModel(settings: SettingsRepository) =
        TimerViewModel(engine, NoSessions, { it() }, { it.run() }, log, settings)

    @Test
    fun savedSettings_areUsedByTheNextSessionAndRemembered() {
        val store = MemorySettings()
        val viewModel = viewModel(store)

        assertTrue(viewModel.saveSettings(custom))
        viewModel.start()

        assertEquals(custom, viewModel.settings)
        assertEquals(custom, store.stored)
        assertTrue(scheduler.scheduled.single().delay in 2.seconds..4.seconds)
        assertEquals("random", log.only("settings.changed").data["startDelayMode"])
    }

    @Test
    fun settingsCannotBeChangedWhileARunIsInProgress() {
        val store = MemorySettings()
        val viewModel = viewModel(store)
        viewModel.start() // waiting for the start delay

        assertFalse(viewModel.saveSettings(custom))

        assertEquals(TimerSettings.DEFAULT, viewModel.settings)
        assertNull("nothing remembered", store.stored)
        assertNotNull(viewModel.errorMessage)
    }

    @Test
    fun rememberedSettings_areLoadedWhenTheAppStarts() {
        val viewModel = viewModel(MemorySettings(stored = custom))

        assertEquals(custom, viewModel.settings)
        assertEquals(custom, engine.currentSettings())
        assertEquals(0.35, log.only("settings.loaded").data["threshold"])
    }

    @Test
    fun unreadableRememberedSettings_fallBackToTheDefaultsAndSaySo() {
        val viewModel = viewModel(MemorySettings().apply { failOnLoad = true })

        assertEquals(TimerSettings.DEFAULT, viewModel.settings)
        assertEquals("The saved settings could not be read, so the defaults are used.", viewModel.errorMessage)
        assertEquals("IOException", log.only("settings.load_failed").data["error"])
    }

    @Test
    fun aFailureToRememberSettings_isShownButTheyStillApply() {
        val viewModel = viewModel(MemorySettings().apply { failOnSave = true })

        assertTrue(viewModel.saveSettings(custom))

        assertEquals(custom, engine.currentSettings())
        assertTrue(viewModel.errorMessage!!.startsWith("Could not save the settings"))
        assertEquals("IOException", log.only("settings.save_failed").data["error"])
    }

    @Test
    fun aCompletedSessionKeepsTheSettingsItRanWith() {
        val viewModel = viewModel(MemorySettings())
        viewModel.start()
        scheduler.runNext()
        beep.finish()
        viewModel.stop()

        viewModel.saveSettings(custom) // allowed again once Completed

        assertEquals(TimerSettings.DEFAULT, viewModel.snapshot.session!!.settings)
    }
}
