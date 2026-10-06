package com.example.eece4081_iap.data

import com.example.eece4081_iap.domain.ShootingSession
import java.io.File
import java.io.IOException

/**
 * Keeps each session as one JSON file, `<id>.json`, inside [directory]
 * (on a device, a folder in the app's private storage).
 *
 * A save writes a temporary file and then renames it over the real one, so
 * an interruption part-way through cannot leave a half-written session.
 *
 * The file work is small but blocking: call it off the main thread.
 */
class FileSessionRepository(private val directory: File) : SessionRepository {

    @Synchronized
    override fun save(session: ShootingSession) {
        // The id becomes a file name, so only plain characters are allowed.
        require(SAFE_ID.matches(session.id)) { "Unsupported session id: ${session.id}" }
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Cannot create folder: $directory")
        }

        val target = File(directory, "${session.id}$EXTENSION")
        val temp = File(directory, "${session.id}$EXTENSION.tmp")
        temp.writeText(SessionJson.encode(session), Charsets.UTF_8)
        if (!temp.renameTo(target)) {
            // Some file systems will not rename over an existing file.
            target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IOException("Cannot save session ${session.id}")
            }
        }
    }

    @Synchronized
    override fun loadAll(): List<ShootingSession> {
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(EXTENSION) }
            ?: return emptyList()
        return files
            .mapNotNull { file ->
                try {
                    SessionJson.decode(file.readText(Charsets.UTF_8))
                } catch (e: Exception) {
                    null // a corrupt or unreadable file must not hide the other sessions
                }
            }
            .sortedWith(compareByDescending<ShootingSession> { it.startTime }.thenBy { it.id })
    }

    private companion object {
        const val EXTENSION = ".json"
        val SAFE_ID = Regex("[A-Za-z0-9_-]+")
    }
}
