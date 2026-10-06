package com.example.eece4081_iap.data

import com.example.eece4081_iap.domain.ShootingSession
import com.example.eece4081_iap.domain.TimerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

class FileSessionRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun directory() = File(folder.root, "sessions")

    private fun repository() = FileSessionRepository(directory())

    private fun session(id: String, startTime: Long, vararg shotMillis: Long): ShootingSession {
        var session = ShootingSession(id, startTime, TimerSettings.DEFAULT)
        shotMillis.forEach { session = session.recordShot(it.milliseconds) }
        return session
    }

    @Test
    fun loadAll_beforeAnythingIsSaved_isEmpty() {
        assertTrue(repository().loadAll().isEmpty())
    }

    @Test
    fun save_thenLoadAll_returnsTheSession() {
        val saved = session("a", 1_000L, 1200, 1800)
        val repository = repository()

        repository.save(saved)

        assertEquals(listOf(saved), repository.loadAll())
    }

    @Test
    fun loadAll_returnsNewestFirst() {
        val repository = repository()
        val older = session("older", 1_000L, 1200)
        val newer = session("newer", 2_000L, 900)
        val newest = session("newest", 3_000L, 800)

        repository.save(newer)
        repository.save(older)
        repository.save(newest)

        assertEquals(listOf(newest, newer, older), repository.loadAll())
    }

    @Test
    fun save_sameIdAgain_replacesTheEarlierCopy() {
        val repository = repository()
        repository.save(session("a", 1_000L, 1200))

        val updated = session("a", 1_000L, 1200, 1800)
        repository.save(updated)

        assertEquals(listOf(updated), repository.loadAll())
    }

    @Test
    fun aNewRepositoryOnTheSameFolder_seesSavedSessions() {
        // The closest JVM stand-in for closing and reopening the app.
        val saved = session("a", 1_000L, 1200, 1800)
        repository().save(saved)

        assertEquals(listOf(saved), repository().loadAll())
    }

    @Test
    fun aCorruptFile_isSkippedAndOtherSessionsStillLoad() {
        val repository = repository()
        val good = session("good", 1_000L, 1200)
        repository.save(good)
        File(directory(), "bad.json").writeText("{ this is not valid")

        assertEquals(listOf(good), repository.loadAll())
    }

    @Test
    fun save_leavesNoTemporaryFilesBehind() {
        val repository = repository()
        repository.save(session("a", 1_000L, 1200))
        repository.save(session("a", 1_000L, 1200, 1800))

        assertEquals(listOf("a.json"), directory().list()!!.toList())
    }

    @Test
    fun save_withAnIdThatIsNotAPlainFileName_isRejected() {
        val repository = repository()

        assertThrows(IllegalArgumentException::class.java) {
            repository.save(session("../evil", 1_000L, 1200))
        }
        assertTrue(repository.loadAll().isEmpty())
    }
}
