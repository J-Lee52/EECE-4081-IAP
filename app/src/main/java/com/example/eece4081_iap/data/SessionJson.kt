package com.example.eece4081_iap.data

import com.example.eece4081_iap.domain.ShootingSession
import org.json.JSONArray
import org.json.JSONObject
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Converts a [ShootingSession] to and from JSON text.
 *
 * Durations are stored as whole nanoseconds so nothing is lost. Splits are
 * not stored: they are recomputed on load by replaying the shot timestamps
 * through [ShootingSession.recordShot], so the same rules always apply. The
 * session's settings use the same format as the remembered settings
 * ([TimerSettingsJson]).
 */
object SessionJson {

    fun encode(session: ShootingSession): String {
        val shots = JSONArray()
        session.shots.forEach { shot ->
            shots.put(JSONObject().put("timestampNanos", shot.timestamp.inWholeNanoseconds))
        }
        return JSONObject()
            .put("id", session.id)
            .put("startTime", session.startTime)
            .put("settings", TimerSettingsJson.encode(session.settings))
            .put("shots", shots)
            .toString()
    }

    /** Throws if [text] is not valid session JSON. */
    fun decode(text: String): ShootingSession {
        val json = JSONObject(text)
        var session = ShootingSession(
            id = json.getString("id"),
            startTime = json.getLong("startTime"),
            settings = TimerSettingsJson.decode(json.getJSONObject("settings")),
        )
        val shots = json.getJSONArray("shots")
        for (i in 0 until shots.length()) {
            val timestamp = shots.getJSONObject(i).getLong("timestampNanos").nanoseconds
            session = session.recordShot(timestamp)
        }
        return session
    }
}
