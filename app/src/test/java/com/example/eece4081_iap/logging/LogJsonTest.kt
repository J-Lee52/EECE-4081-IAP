package com.example.eece4081_iap.logging

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogJsonTest {

    private val record = LogRecord(
        level = LogLevel.INFO,
        component = "engine",
        event = "shot.recorded",
        runId = "a1b2c3d4",
        sessionId = "session-1",
        data = linkedMapOf("shot" to 2, "atMs" to 1650L, "splitMs" to 400L, "peak" to 0.946),
    )

    @Test
    fun everyLine_hasTheSameTopLevelFieldsInTheSameOrder() {
        val line = LogJson.format(record, timestampMillis = 1_760_000_000_000L)

        assertEquals(
            "{\"v\":1,\"ts\":1760000000000,\"level\":\"INFO\",\"component\":\"engine\"," +
                "\"event\":\"shot.recorded\",\"runId\":\"a1b2c3d4\",\"sessionId\":\"session-1\"," +
                "\"data\":{\"shot\":2,\"atMs\":1650,\"splitMs\":400,\"peak\":0.946}}",
            line,
        )
    }

    @Test
    fun unknownIds_areWrittenAsNullSoTheFieldsAreStillPresent() {
        val line = LogJson.format(LogRecord(LogLevel.INFO, "viewmodel", "history.loaded"), 0L)
        val json = JSONObject(line)

        assertTrue(json.has("runId") && json.isNull("runId"))
        assertTrue(json.has("sessionId") && json.isNull("sessionId"))
        assertEquals(0, json.getJSONObject("data").length())
    }

    @Test
    fun output_isValidJsonThatAParserReadsBack() {
        val json = JSONObject(LogJson.format(record, 42L))

        assertEquals(1, json.getInt("v"))
        assertEquals(42L, json.getLong("ts"))
        assertEquals("shot.recorded", json.getString("event"))
        assertEquals(400L, json.getJSONObject("data").getLong("splitMs"))
    }

    @Test
    fun quotesNewlinesAndBackslashesInMessages_areEscaped() {
        val tricky = LogRecord(
            LogLevel.ERROR, "engine", "microphone.open_failed",
            data = linkedMapOf("message" to "said \"no\"\nC:\\path\tend"),
        )

        val line = LogJson.format(tricky, 0L)

        assertFalse("one record must stay on one line", line.contains('\n'))
        assertEquals("said \"no\"\nC:\\path\tend", JSONObject(line).getJSONObject("data").getString("message"))
    }

    private val failure = LogRecord(
        LogLevel.ERROR, "viewmodel", "session.save_failed", "run-1", "session-1",
        data = linkedMapOf("error" to "IOException"),
        diagnostics = linkedMapOf("message" to "ENOSPC /data/user/0/com.example/files/sessions"),
    )

    @Test
    fun diagnostics_areLeftOutOfNormalLogs() {
        val line = LogJson.format(failure, 0L)

        assertFalse(line.contains("ENOSPC"))
        assertFalse(JSONObject(line).getJSONObject("data").has("diagnostics"))
    }

    @Test
    fun diagnostics_areWrittenInsideDataInVerboseModeWithoutChangingTopLevelFields() {
        val json = JSONObject(LogJson.format(failure, 0L, includeDiagnostics = true))

        assertEquals(
            setOf("v", "ts", "level", "component", "event", "runId", "sessionId", "data"),
            json.keys().asSequence().toSet(),
        )
        assertEquals(
            "ENOSPC /data/user/0/com.example/files/sessions",
            json.getJSONObject("data").getJSONObject("diagnostics").getString("message"),
        )
    }

    @Test
    fun bestEffort_swallowsAFailingLogger() {
        val broken = EventLog { throw IllegalStateException("log full") }

        broken.bestEffort().record(record) // must not throw
    }

    @Test
    fun nullValuesEnumsAndNonFiniteNumbers_areWrittenAsValidJson() {
        val line = LogJson.format(
            LogRecord(
                LogLevel.DEBUG, "engine", "x",
                data = linkedMapOf("splitMs" to null, "level" to LogLevel.WARN, "peak" to Double.NaN),
            ),
            0L,
        )
        val data = JSONObject(line).getJSONObject("data")

        assertTrue(data.isNull("splitMs"))
        assertEquals("WARN", data.getString("level"))
        assertTrue(data.isNull("peak"))
    }
}
