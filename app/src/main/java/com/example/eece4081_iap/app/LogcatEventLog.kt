package com.example.eece4081_iap.app

import android.util.Log
import com.example.eece4081_iap.logging.EventLog
import com.example.eece4081_iap.logging.LogJson
import com.example.eece4081_iap.logging.LogLevel
import com.example.eece4081_iap.logging.LogRecord

/**
 * Writes each record to Logcat as one JSON line under the tag [TAG], so an
 * operator can read only this app's records with:
 *
 *   adb logcat -s ShotTimer
 *
 * Verbose diagnostic mode adds exception messages (which can contain device
 * details or file paths) to error records, and one "shot.ignored" record per
 * ignored echo (normally only counted in "audio.level"). It is off by default
 * and is turned on and off from a computer, without rebuilding the app:
 *
 *   adb shell "setprop log.tag.ShotTimer VERBOSE"
 *   adb shell "setprop log.tag.ShotTimer ''"
 *
 * (Quote the whole command: adb drops an empty "" argument.)
 */
class LogcatEventLog(
    private val now: () -> Long = System::currentTimeMillis,
    private val verbose: () -> Boolean = { Log.isLoggable(TAG, Log.VERBOSE) },
) : EventLog {

    override fun record(record: LogRecord) {
        Log.println(priority(record.level), TAG, LogJson.format(record, now(), includeDiagnostics = verbose()))
    }

    /** Verbose mode also turns on per-event detail records, such as each ignored echo. */
    override fun wantsDetail(): Boolean = verbose()

    private fun priority(level: LogLevel): Int = when (level) {
        LogLevel.DEBUG -> Log.DEBUG
        LogLevel.INFO -> Log.INFO
        LogLevel.WARN -> Log.WARN
        LogLevel.ERROR -> Log.ERROR
    }

    companion object {
        const val TAG = "ShotTimer"
    }
}
