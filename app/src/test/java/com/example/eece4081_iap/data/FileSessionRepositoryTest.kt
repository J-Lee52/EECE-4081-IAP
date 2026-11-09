package com.example.eece4081_iap.data

import com.example.eece4081_iap.domain.ShootingSession
import com.example.eece4081_iap.domain.TimerSettings
import com.example.eece4081_iap.logging.CapturingEventLog
import com.example.eece4081_iap.logging.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
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
    fun aCorruptFile_isLoggedAsSkippedWithItsName() {
        val log = CapturingEventLog()
        File(directory().apply { mkdirs() }, "bad.json").writeText("{ this is not valid")

        FileSessionRepository(directory(), log).loadAll()

        val skipped = log.only("session.file_skipped")
        assertEquals(LogLevel.WARN, skipped.level)
        assertEquals("bad.json", skipped.data["file"])
    }

    @Test
    fun aFailedReplacement_keepsThePreviouslySavedCopy() {
        val original = session("a", 1_000L, 1200)
        repository().save(original)
        // A file system that refuses to move the new copy into place.
        val failing = FileSessionRepository(directory(), rename = { from, to ->
            if (from.name.endsWith(".tmp")) false else from.renameTo(to)
        })

        assertThrows(IOException::class.java) { failing.save(session("a", 1_000L, 1200, 1800)) }

        assertEquals(listOf(original), repository().loadAll())
        assertEquals(listOf("a.json"), directory().list()!!.toList())
    }

    @Test
    fun replacingOnAFileSystemThatCannotRenameOverAFile_stillReplacesAndLeavesNoBackup() {
        repository().save(session("a", 1_000L, 1200))
        // Like some file systems: a rename onto an existing file fails.
        val noOverwrite = FileSessionRepository(directory(), rename = { from, to ->
            if (to.exists()) false else from.renameTo(to)
        })
        val updated = session("a", 1_000L, 1200, 1800)

        noOverwrite.save(updated)

        assertEquals(listOf(updated), repository().loadAll())
        assertEquals(listOf("a.json"), directory().list()!!.toList())
    }

    @Test
    fun aBackupLeftByAnInterruptedSave_isRestoredOnLoad() {
        val saved = session("a", 1_000L, 1200)
        repository().save(saved)
        // The app stopped after moving the old copy aside and before replacing it.
        File(directory(), "a.json").renameTo(File(directory(), "a.json.bak"))

        assertEquals(listOf(saved), repository().loadAll())
        assertTrue(File(directory(), "a.json").exists())
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

    @Test
    fun save_whenTheStorageFolderCannotBeCreated_throwsIOException() {
        // A plain file where the folder should be, so mkdirs() must fail.
        directory().writeText("not a folder")

        assertThrows(IOException::class.java) {
            repository().save(session("a", 1_000L, 1200))
        }
    }

    @Test
    fun loadAll_ignoresFilesThatAreNotSessions() {
        val repository = repository()
        val good = session("good", 1_000L, 1200)
        repository.save(good)
        File(directory(), "notes.txt").writeText("{}")
        File(directory(), "half-written.json.tmp").writeText("{")
        File(directory(), "folder.json").mkdir()

        assertEquals(listOf(good), repository.loadAll())
    }
}
