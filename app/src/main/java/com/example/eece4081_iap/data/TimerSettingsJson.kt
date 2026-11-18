package com.example.eece4081_iap.data

import com.example.eece4081_iap.domain.FixedDelay
import com.example.eece4081_iap.domain.RandomDelay
import com.example.eece4081_iap.domain.TimerSettings
import org.json.JSONObject
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Converts [TimerSettings] to and from a JSON object. Used for the settings
 * stored with each saved session ([SessionJson]) and for the remembered
 * settings ([FileSettingsRepository]), so both use one format.
 *
 * Durations are stored as whole nanoseconds so nothing is lost.
 */
internal object TimerSettingsJson {

    fun encode(settings: TimerSettings): JSONObject {
        val startDelay = when (val delay = settings.startDelay) {
            is FixedDelay -> JSONObject()
                .put("type", "fixed")
                .put("nanos", delay.duration.inWholeNanoseconds)
            is RandomDelay -> JSONObject()
                .put("type", "random")
                .put("minNanos", delay.minDelay.inWholeNanoseconds)
                .put("maxNanos", delay.maxDelay.inWholeNanoseconds)
        }
        return JSONObject()
            .put("detectionThreshold", settings.detectionThreshold)
            .put("detectionCooldownNanos", settings.detectionCooldown.inWholeNanoseconds)
            .put("startDelay", startDelay)
    }

    /** Throws if [json] is not valid settings JSON, or holds settings the domain rejects. */
    fun decode(json: JSONObject): TimerSettings {
        val delayJson = json.getJSONObject("startDelay")
        val startDelay = when (val type = delayJson.getString("type")) {
            "fixed" -> FixedDelay(delayJson.getLong("nanos").nanoseconds)
            "random" -> RandomDelay(
                minDelay = delayJson.getLong("minNanos").nanoseconds,
                maxDelay = delayJson.getLong("maxNanos").nanoseconds,
            )
            else -> throw IllegalArgumentException("Unknown start delay type: $type")
        }
        return TimerSettings(
            detectionThreshold = json.getDouble("detectionThreshold"),
            detectionCooldown = json.getLong("detectionCooldownNanos").nanoseconds,
            startDelay = startDelay,
        )
    }
}
