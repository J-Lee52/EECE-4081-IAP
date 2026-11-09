package com.example.eece4081_iap.data

import com.example.eece4081_iap.domain.RandomDelay
import com.example.eece4081_iap.domain.TimerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FileSettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun file() = File(folder.root, "app/settings.json")

    private val custom = TimerSettings(0.35, 150.milliseconds, RandomDelay(2.seconds, 4.seconds))

    @Test
    fun beforeAnythingIsSaved_loadReturnsNull() {
        assertNull(FileSettingsRepository(file()).load())
    }

    @Test
    fun savedSettings_areLoadedByANewRepository_likeAfterReopeningTheApp() {
        FileSettingsRepository(file()).save(custom)

        assertEquals(custom, FileSettingsRepository(file()).load())
    }

    @Test
    fun savingAgain_replacesTheEarlierSettings() {
        val repository = FileSettingsRepository(file())
        repository.save(TimerSettings.DEFAULT)

        repository.save(custom)

        assertEquals(custom, repository.load())
        assertEquals(listOf("settings.json"), file().parentFile!!.list()!!.toList())
    }

    @Test
    fun anUnreadableSettingsFile_makesLoadThrow() {
        file().parentFile!!.mkdirs()
        file().writeText("{ not settings")

        assertThrows(Exception::class.java) { FileSettingsRepository(file()).load() }
    }

    @Test
    fun aFailedSave_keepsThePreviouslyRememberedSettings() {
        FileSettingsRepository(file()).save(custom)
        val failing = FileSettingsRepository(file(), rename = { from, to ->
            if (from.name.endsWith(".tmp")) false else from.renameTo(to)
        })

        assertThrows(IOException::class.java) { failing.save(TimerSettings.DEFAULT) }

        assertEquals(custom, FileSettingsRepository(file()).load())
    }
}
