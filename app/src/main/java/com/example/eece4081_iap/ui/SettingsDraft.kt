package com.example.eece4081_iap.ui

import com.example.eece4081_iap.domain.FixedDelay
import com.example.eece4081_iap.domain.RandomDelay
import com.example.eece4081_iap.domain.StartDelay
import com.example.eece4081_iap.domain.TimerSettings
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * The settings being edited on the Settings screen (US01), before Save.
 *
 * Values are whole slider steps, so what the person sees is exactly what is
 * saved:
 * - threshold in hundredths, [THRESHOLD_MIN]..[THRESHOLD_MAX] (0.05..1.00);
 * - cooldown in milliseconds, 0..[COOLDOWN_MAX_MS] in steps of [COOLDOWN_STEP_MS];
 * - delays in tenths of a second, 0..[DELAY_MAX_TENTHS] (0.0..10.0 s).
 *
 * The Random Range minimum can never pass the maximum (US01): moving one past
 * the other moves the other with it, so [toSettings] always succeeds. Values
 * for the mode not in use are kept, so switching modes back and forth does not
 * lose them.
 */
data class SettingsDraft(
    val thresholdHundredths: Int,
    val cooldownMs: Int,
    val randomRange: Boolean,
    val fixedTenths: Int,
    val minTenths: Int,
    val maxTenths: Int,
) {
    init {
        require(thresholdHundredths in THRESHOLD_MIN..THRESHOLD_MAX) { "threshold out of range: $thresholdHundredths" }
        require(cooldownMs in 0..COOLDOWN_MAX_MS && cooldownMs % COOLDOWN_STEP_MS == 0) { "cooldown out of range: $cooldownMs" }
        require(fixedTenths in 0..DELAY_MAX_TENTHS) { "fixed delay out of range: $fixedTenths" }
        require(minTenths in 0..DELAY_MAX_TENTHS && maxTenths in 0..DELAY_MAX_TENTHS) { "delay out of range" }
        require(minTenths <= maxTenths) { "minimum delay exceeds maximum" }
    }

    fun withThreshold(hundredths: Int) = copy(thresholdHundredths = hundredths.coerceIn(THRESHOLD_MIN, THRESHOLD_MAX))

    fun withCooldown(ms: Int) = copy(cooldownMs = snapCooldown(ms))

    fun withRandomRange(random: Boolean) = copy(randomRange = random)

    fun withFixed(tenths: Int) = copy(fixedTenths = tenths.coerceIn(0, DELAY_MAX_TENTHS))

    /** Sets the minimum; if it would pass the maximum, the maximum moves up with it. */
    fun withMin(tenths: Int): SettingsDraft {
        val min = tenths.coerceIn(0, DELAY_MAX_TENTHS)
        return copy(minTenths = min, maxTenths = maxOf(maxTenths, min))
    }

    /** Sets the maximum; if it would go below the minimum, the minimum moves down with it. */
    fun withMax(tenths: Int): SettingsDraft {
        val max = tenths.coerceIn(0, DELAY_MAX_TENTHS)
        return copy(maxTenths = max, minTenths = minOf(minTenths, max))
    }

    fun toSettings(): TimerSettings = TimerSettings(
        detectionThreshold = thresholdHundredths / 100.0,
        detectionCooldown = cooldownMs.milliseconds,
        startDelay = if (randomRange) {
            RandomDelay(minDelay = (minTenths * 100).milliseconds, maxDelay = (maxTenths * 100).milliseconds)
        } else {
            FixedDelay((fixedTenths * 100).milliseconds)
        },
    )

    companion object {
        const val THRESHOLD_MIN = 5
        const val THRESHOLD_MAX = 100
        const val COOLDOWN_STEP_MS = 10
        const val COOLDOWN_MAX_MS = 1000
        const val DELAY_MAX_TENTHS = 100

        // Used for the mode that is not in use when the screen opens.
        private const val DEFAULT_FIXED_TENTHS = 30
        private const val DEFAULT_MIN_TENTHS = 20
        private const val DEFAULT_MAX_TENTHS = 40

        /** A draft showing [settings], with each value snapped to the nearest slider step. */
        fun from(settings: TimerSettings): SettingsDraft {
            val delay = settings.startDelay
            return SettingsDraft(
                thresholdHundredths = (settings.detectionThreshold * 100).roundToInt().coerceIn(THRESHOLD_MIN, THRESHOLD_MAX),
                cooldownMs = snapCooldown(settings.detectionCooldown.inWholeMilliseconds.toInt()),
                randomRange = delay is RandomDelay,
                fixedTenths = (delay as? FixedDelay)?.duration?.let(::tenths) ?: DEFAULT_FIXED_TENTHS,
                minTenths = (delay as? RandomDelay)?.minDelay?.let(::tenths) ?: DEFAULT_MIN_TENTHS,
                maxTenths = (delay as? RandomDelay)?.maxDelay?.let(::tenths) ?: DEFAULT_MAX_TENTHS,
            )
        }

        /** A short description for the timer screen, for example "Delay random 2.0–4.0 s · Threshold 0.50 · Cooldown 80 ms". */
        fun summary(settings: TimerSettings): String {
            val delay = when (val d: StartDelay = settings.startDelay) {
                is FixedDelay -> "Delay ${seconds(d.duration.inWholeMilliseconds)}"
                is RandomDelay -> "Delay random ${seconds(d.minDelay.inWholeMilliseconds, unit = false)}–" +
                    seconds(d.maxDelay.inWholeMilliseconds)
            }
            return "$delay · Threshold ${"%.2f".format(java.util.Locale.ROOT, settings.detectionThreshold)} · " +
                "Cooldown ${settings.detectionCooldown.inWholeMilliseconds} ms"
        }

        /** Text for a delay slider value in tenths of a second, for example "3.0 s". */
        fun tenthsText(tenths: Int): String = "${tenths / 10}.${tenths % 10} s"

        private fun snapCooldown(ms: Int): Int =
            ((ms.toDouble() / COOLDOWN_STEP_MS).roundToInt() * COOLDOWN_STEP_MS).coerceIn(0, COOLDOWN_MAX_MS)

        private fun tenths(duration: kotlin.time.Duration): Int =
            (duration.inWholeMilliseconds / 100.0).roundToInt().coerceIn(0, DELAY_MAX_TENTHS)

        private fun seconds(ms: Long, unit: Boolean = true): String {
            val tenths = (ms / 100.0).roundToInt()
            return "${tenths / 10}.${tenths % 10}" + if (unit) " s" else ""
        }
    }
}
