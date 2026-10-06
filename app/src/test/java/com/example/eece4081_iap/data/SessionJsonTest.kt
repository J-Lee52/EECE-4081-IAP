package com.example.eece4081_iap.data

import com.example.eece4081_iap.domain.FixedDelay
import com.example.eece4081_iap.domain.ShootingSession
import com.example.eece4081_iap.domain.TimerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class SessionJsonTest {

    private val customSettings = TimerSettings(
        detectionThreshold = 0.7,
        detectionCooldown = 250.milliseconds,
        startDelay = FixedDelay(5.seconds),
    )

    private fun sessionWithThreeShots() = ShootingSession(
        id = "session-1",
        startTime = 1_700_000_000_000L,
        settings = customSettings,
    )
        .recordShot(1200.milliseconds)
        .recordShot(1800.milliseconds)
        .recordShot(2700.milliseconds)

    @Test
    fun roundTrip_preservesIdStartTimeSettingsAndShots() {
        val original = sessionWithThreeShots()

        val restored = SessionJson.decode(SessionJson.encode(original))

        assertEquals(original, restored)
    }

    @Test
    fun roundTrip_recomputesTheSplits() {
        val restored = SessionJson.decode(SessionJson.encode(sessionWithThreeShots()))

        assertEquals(null, restored.shots[0].splitTime)
        assertEquals(600.milliseconds, restored.shots[1].splitTime)
        assertEquals(900.milliseconds, restored.shots[2].splitTime)
    }

    @Test
    fun roundTrip_sessionWithNoShots() {
        val original = ShootingSession(
            id = "empty",
            startTime = 5L,
            settings = TimerSettings.DEFAULT,
        )

        assertEquals(original, SessionJson.decode(SessionJson.encode(original)))
    }

    @Test
    fun roundTrip_keepsNanosecondPrecision() {
        val original = ShootingSession(
            id = "precise",
            startTime = 5L,
            settings = TimerSettings.DEFAULT,
        ).recordShot(1_234_567_891.nanoseconds)

        val restored = SessionJson.decode(SessionJson.encode(original))

        assertEquals(1_234_567_891.nanoseconds, restored.shots.single().timestamp)
    }

    @Test
    fun decode_text_thatIsNotJson_isRejected() {
        assertThrows(Exception::class.java) { SessionJson.decode("not json") }
    }

    @Test
    fun decode_missingField_isRejected() {
        assertThrows(Exception::class.java) { SessionJson.decode("{\"id\":\"x\"}") }
    }

    @Test
    fun decode_unknownStartDelayType_isRejected() {
        val text = SessionJson.encode(sessionWithThreeShots())
            .replace("\"fixed\"", "\"random\"")

        assertThrows(Exception::class.java) { SessionJson.decode(text) }
    }
}
