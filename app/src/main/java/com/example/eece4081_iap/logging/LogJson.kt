package com.example.eece4081_iap.logging

/**
 * Formats a [LogRecord] as one line of JSON with a fixed field order:
 *
 *   {"v":1,"ts":<epoch ms>,"level":..,"component":..,"event":..,"runId":..,"sessionId":..,"data":{..}}
 *
 * Every field is always present ([LogRecord.runId] and [LogRecord.sessionId]
 * as null when unknown), so each line can be parsed the same way.
 *
 * [LogRecord.diagnostics] is written only when includeDiagnostics is true
 * (verbose diagnostic mode), as a "diagnostics" object inside "data", so the
 * top-level fields never change.
 *
 * Hand-written rather than using org.json, so it is pure Kotlin and behaves
 * the same in JVM tests and on a device. Supports strings, numbers, booleans,
 * null, enums (as their name) and nested maps; anything else is written with
 * toString().
 */
object LogJson {

    /** Version of the line format; bump it if a top-level field changes. */
    const val VERSION = 1

    fun format(record: LogRecord, timestampMillis: Long, includeDiagnostics: Boolean = false): String = buildString {
        val data = if (includeDiagnostics && record.diagnostics.isNotEmpty()) {
            record.data + ("diagnostics" to record.diagnostics)
        } else {
            record.data
        }
        append('{')
        field("v", VERSION, first = true)
        field("ts", timestampMillis)
        field("level", record.level.name)
        field("component", record.component)
        field("event", record.event)
        field("runId", record.runId)
        field("sessionId", record.sessionId)
        field("data", data)
        append('}')
    }

    private fun StringBuilder.field(name: String, value: Any?, first: Boolean = false) {
        if (!first) append(',')
        string(name)
        append(':')
        value(value)
    }

    private fun StringBuilder.value(value: Any?) {
        when (value) {
            null -> append("null")
            is String -> string(value)
            is Boolean -> append(value)
            is Double -> if (value.isFinite()) append(value) else append("null")
            is Float -> if (value.isFinite()) append(value) else append("null")
            is Number -> append(value)
            is Enum<*> -> string(value.name)
            is Map<*, *> -> {
                append('{')
                value.entries.forEachIndexed { index, (key, item) ->
                    if (index > 0) append(',')
                    string(key.toString())
                    append(':')
                    value(item)
                }
                append('}')
            }
            else -> string(value.toString())
        }
    }

    private fun StringBuilder.string(text: String) {
        append('"')
        for (c in text) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}
