package com.example.eece4081_iap.data

import com.example.eece4081_iap.domain.ShootingSession
import com.example.eece4081_iap.logging.EventLog
import com.example.eece4081_iap.logging.LogLevel
import com.example.eece4081_iap.logging.LogRecord
import com.example.eece4081_iap.logging.bestEffort
import java.io.File
import java.io.IOException

/**
 * Keeps each session as one JSON file, `<id>.json`, inside [directory]
 * (on a device, a folder in the app's private storage).
 *
 * A save writes a temporary file and then renames it over the real one, so
 * an interruption part-way through cannot leave a half-written session. A
 * previously saved copy is never deleted before its replacement is in place:
 * if the replacement fails, the previous copy is put back.
 *
 * The file work is small but blocking: call it off the main thread.
 *
 * [rename] is the file move used by [save]; tests replace it to simulate a
 * failing file system.
 */
class FileSessionRepository(
    private val directory: File,
    log: EventLog = EventLog.NONE,
    private val rename: (from: File, to: File) -> Boolean = { from, to -> from.renameTo(to) },
) : SessionRepository {

    private val log: EventLog = log.bestEffort()

    @Synchronized
    override fun save(session: ShootingSession) {
        // The id becomes a file name, so only plain characters are allowed.
        require(SAFE_ID.matches(session.id)) { "Unsupported session id: ${session.id}" }
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot create folder: $directory")
        }

        val target = File(directory, "${session.id}$EXTENSION")
        replaceFileSafely(target, SessionJson.encode(session), rename)
    }

    @Synchronized
    override fun loadAll(): List<ShootingSession> {
        restoreInterruptedSaves()
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(EXTENSION) }
            ?: return emptyList()
        return files
            .mapNotNull { file ->
                try {
                    SessionJson.decode(file.readText(Charsets.UTF_8))
                } catch (e: Exception) {
                    // A corrupt or unreadable file must not hide the other sessions.
                    log.record(
                        LogRecord(
                            LogLevel.WARN, "storage", "session.file_skipped",
                            data = linkedMapOf("file" to file.name, "error" to e.javaClass.simpleName),
                            diagnostics = mapOf("message" to e.message),
                        ),
                    )
                    null
                }
            }
            .sortedWith(compareByDescending<ShootingSession> { it.startTime }.thenBy { it.id })
    }

    /**
     * If the app stopped between moving a previous copy aside and putting its
     * replacement in place, only the backup exists. Put it back so the session
     * is not lost.
     */
    private fun restoreInterruptedSaves() {
        val backups = directory.listFiles { file -> file.isFile && file.name.endsWith("$EXTENSION$BACKUP_SUFFIX") }
            ?: return
        for (backup in backups) {
            restoreInterruptedReplace(File(directory, backup.name.removeSuffix(BACKUP_SUFFIX)), rename)
        }
    }

    private companion object {
        const val EXTENSION = ".json"
        val SAFE_ID = Regex("[A-Za-z0-9_-]+")
    }
}
