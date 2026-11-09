package com.example.eece4081_iap.logging

/** Severity of a [LogRecord]. */
enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * One structured log event. Every record has the same top-level fields, so a
 * tool (or a person with a filter) can rely on them:
 *
 * - [component]: which part of the app wrote it ("engine", "viewmodel", ...)
 * - [event]: what happened, as a stable dotted name ("shot.recorded", ...)
 * - [runId]: the timer run it belongs to, created when Start is tapped; null
 *   for events outside a run (such as loading history at app start)
 * - [sessionId]: the saved session's id, once the start beep has created it
 * - [data]: event-specific details. Keys use lowerCamelCase; durations are
 *   whole milliseconds with an "Ms" suffix; amplitudes are 0.0..1.0. Errors are
 *   described by stable values only (an error type name or a numeric code).
 * - [diagnostics]: device-dependent detail such as exception messages, which
 *   may contain implementation details or file paths. It is written only in
 *   verbose diagnostic mode, never in normal logs.
 */
data class LogRecord(
    val level: LogLevel,
    val component: String,
    val event: String,
    val runId: String? = null,
    val sessionId: String? = null,
    val data: Map<String, Any?> = emptyMap(),
    val diagnostics: Map<String, Any?> = emptyMap(),
)

/**
 * Where structured log records go (a port, like Clock). The app writes them
 * to Logcat; tests capture them in a list; [NONE] drops them.
 */
fun interface EventLog {
    fun record(record: LogRecord)

    /**
     * True in verbose diagnostic mode, when callers may also send records
     * that are normally summarised (for example, one record per ignored
     * echo instead of a count per second). False by default.
     */
    fun wantsDetail(): Boolean = false

    companion object {
        /** Discards every record. The default, so logging is always optional. */
        val NONE = EventLog { }
    }
}

/**
 * Wraps this log so that recording can never throw into the caller. The timer
 * engine logs inside its timing, audio and state-transition code; a failing
 * logger must not interrupt shot detection or leave a transition half done.
 * A record that fails to be written is dropped.
 */
fun EventLog.bestEffort(): EventLog = this as? BestEffortEventLog ?: BestEffortEventLog(this)

private class BestEffortEventLog(private val delegate: EventLog) : EventLog {
    override fun record(record: LogRecord) {
        try {
            delegate.record(record)
        } catch (_: Exception) {
            // Logging is best effort: losing one record is better than
            // breaking the timer.
        }
    }

    override fun wantsDetail(): Boolean = try {
        delegate.wantsDetail()
    } catch (_: Exception) {
        false
    }
}
