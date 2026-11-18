package com.example.eece4081_iap.data

import com.example.eece4081_iap.domain.TimerSettings
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Remembers the timer settings the person chose, so they survive closing the
 * app. Blocking file work: call it off the main thread.
 */
interface SettingsRepository {

    /** The remembered settings, or null if none were saved yet. Throws if they cannot be read. */
    fun load(): TimerSettings?

    /** Remembers [settings]. Throws if they cannot be written; the previous copy is then kept. */
    fun save(settings: TimerSettings)

    companion object {
        /** Remembers nothing. The default, so settings storage is always optional. */
        val NONE = object : SettingsRepository {
            override fun load(): TimerSettings? = null
            override fun save(settings: TimerSettings) = Unit
        }
    }
}

/**
 * Keeps the settings as one JSON file, [file], written with the same safe
 * replacement as saved sessions ([replaceFileSafely]), so a failed save never
 * loses the settings remembered before it.
 */
class FileSettingsRepository(
    private val file: File,
    private val rename: (from: File, to: File) -> Boolean = { from, to -> from.renameTo(to) },
) : SettingsRepository {

    @Synchronized
    override fun load(): TimerSettings? {
        restoreInterruptedReplace(file, rename)
        if (!file.isFile) return null
        return TimerSettingsJson.decode(JSONObject(file.readText(Charsets.UTF_8)))
    }

    @Synchronized
    override fun save(settings: TimerSettings) {
        val folder = file.absoluteFile.parentFile
        if (folder != null && !folder.isDirectory && !folder.mkdirs()) {
            throw IOException("Cannot create folder: $folder")
        }
        replaceFileSafely(file, TimerSettingsJson.encode(settings).toString(), rename)
    }
}
