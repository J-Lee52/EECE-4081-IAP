package com.example.eece4081_iap.data

import com.example.eece4081_iap.domain.FixedDelay
import com.example.eece4081_iap.domain.ShootingSession
import com.example.eece4081_iap.domain.TimerSettings
import org.json.JSONArray
import org.json.JSONObject
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Converts a [ShootingSession] to and from JSON text.
 *
 * Durations are stored as whole nanoseconds so nothing is lost. Splits are
 * not stored: they are recomputed on load by replaying the shot timestamps
 * through [ShootingSession.recordShot], so the same rules always apply.
 */
object SessionJson {

    fun encode(session: ShootingSession): String {
        val settings = session.settings
        val startDelay = when (val delay = settings.startDelay) {
            is FixedDelay -> JSONObject()
                .put("type", "fixed")
                .put("nanos", delay.duration.inWholeNanoseconds)
        }
        val shots = JSONArray()
        session.shots.forEach { shot ->
            shots.put(JSONObject().put("timestampNanos", shot.timestamp.inWholeNanoseconds))
        }
        return JSONObject()
            .put("id", session.id)
            .put("startTime", session.startTime)
            .put(
                "settings",
                JSONObject()
                    .put("detectionThreshold", settings.detectionThreshold)
                    .put("detectionCooldownNanos", settings.detectionCooldown.inWholeNanoseconds)
                    .put("startDelay", startDelay),
            )
            .put("shots", shots)
            .toString()
    }

    /** Throws if [text] is not valid session JSON. */
    fun decode(text: String): ShootingSession {
        val json = JSONObject(text)
        val settingsJson = json.getJSONObject("settings")
        val delayJson = settingsJson.getJSONObject("startDelay")
        val startDelay = when (val type = delayJson.getString("type")) {
            "fixed" -> FixedDelay(delayJson.getLong("nanos").nanoseconds)
            else -> throw IllegalArgumentException("Unknown start delay type: $type")
        }
        val settings = TimerSettings(
            detectionThreshold = settingsJson.getDouble("detectionThreshold"),
            detectionCooldown = settingsJson.getLong("detectionCooldownNanos").nanoseconds,
            startDelay = startDelay,
        )

        var session = ShootingSession(
            id = json.getString("id"),
            startTime = json.getLong("startTime"),
            settings = settings,
        )
        val shots = json.getJSONArray("shots")
        for (i in 0 until shots.length()) {
            val timestamp = shots.getJSONObject(i).getLong("timestampNanos").nanoseconds
            session = session.recordShot(timestamp)
        }
        return session
    }
}
