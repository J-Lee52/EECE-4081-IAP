package com.example.eece4081_iap.ui

import com.example.eece4081_iap.domain.FixedDelay
import com.example.eece4081_iap.domain.RandomDelay
import com.example.eece4081_iap.domain.TimerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SettingsDraftTest {

    private val defaults = SettingsDraft.from(TimerSettings.DEFAULT)

    @Test
    fun theDefaultSettings_openAsAFixedThreeSecondDelay() {
        assertEquals(50, defaults.thresholdHundredths)
        assertEquals(80, defaults.cooldownMs)
        assertFalse(defaults.randomRange)
        assertEquals(30, defaults.fixedTenths)
        assertEquals(TimerSettings.DEFAULT, defaults.toSettings())
    }

    @Test
    fun choosingRandomRange_offersATwoToFourSecondRangeAndSavesIt() {
        val settings = defaults.withRandomRange(true).toSettings()

        assertEquals(RandomDelay(2.seconds, 4.seconds), settings.startDelay)
    }

    @Test
    fun raisingTheMinimumPastTheMaximum_movesTheMaximumUpWithIt() {
        val draft = defaults.withRandomRange(true).withMin(60) // max was 4.0 s

        assertEquals(60, draft.minTenths)
        assertEquals(60, draft.maxTenths)
    }

    @Test
    fun loweringTheMaximumBelowTheMinimum_movesTheMinimumDownWithIt() {
        val draft = defaults.withRandomRange(true).withMax(10) // min was 2.0 s

        assertEquals(10, draft.minTenths)
        assertEquals(10, draft.maxTenths)
    }

    @Test
    fun theMinimumNeverExceedsTheMaximum_whateverOrderTheSlidersMove() {
        var draft = defaults.withRandomRange(true)
        for (step in listOf(90, 5, 100, 0, 37, 63)) {
            draft = draft.withMin(step).withMax(100 - step)
            assertTrue(draft.minTenths <= draft.maxTenths)
            draft.toSettings() // the domain accepts it
        }
    }

    @Test
    fun valuesOutsideTheSliders_areClampedToTheNearestAllowedStep() {
        val draft = defaults.withThreshold(0).withCooldown(1234).withFixed(500)

        assertEquals(SettingsDraft.THRESHOLD_MIN, draft.thresholdHundredths)
        assertEquals(1000, draft.cooldownMs)
        assertEquals(100, draft.fixedTenths)
    }

    @Test
    fun switchingModes_keepsTheValuesOfTheOtherMode() {
        val draft = defaults.withFixed(55).withRandomRange(true).withMin(15).withRandomRange(false)

        assertEquals(FixedDelay(5500.milliseconds), draft.toSettings().startDelay)
        assertEquals(15, draft.minTenths)
    }

    @Test
    fun savedSettingsBetweenSteps_openSnappedToTheNearestStep() {
        val odd = TimerSettings(0.537, 203.milliseconds, RandomDelay(1234.milliseconds, 3456.milliseconds))

        val draft = SettingsDraft.from(odd)

        assertEquals(54, draft.thresholdHundredths)
        assertEquals(200, draft.cooldownMs)
        assertEquals(12, draft.minTenths)
        assertEquals(35, draft.maxTenths)
    }

    @Test
    fun summary_describesTheSettingsInOneLine() {
        assertEquals("Delay 3.0 s · Threshold 0.50 · Cooldown 80 ms", SettingsDraft.summary(TimerSettings.DEFAULT))
        assertEquals(
            "Delay random 2.0–4.0 s · Threshold 0.35 · Cooldown 150 ms",
            SettingsDraft.summary(TimerSettings(0.35, 150.milliseconds, RandomDelay(2.seconds, 4.seconds))),
        )
    }
}
