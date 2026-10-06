package com.example.eece4081_iap.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DurationFormatTest {

    @Test
    fun zero_showsTwoDecimals() {
        assertEquals("0.00 s", 0.milliseconds.toSecondsText())
    }

    @Test
    fun wholeSeconds_showTwoDecimals() {
        assertEquals("1.00 s", 1.seconds.toSecondsText())
        assertEquals("60.00 s", 60.seconds.toSecondsText())
    }

    @Test
    fun hundredths_areShown() {
        assertEquals("1.35 s", 1350.milliseconds.toSecondsText())
        assertEquals("0.25 s", 250.milliseconds.toSecondsText())
    }

    @Test
    fun extraPrecision_isCutOffNotRounded() {
        assertEquals("1.99 s", 1999.milliseconds.toSecondsText())
        assertEquals("0.00 s", 9.milliseconds.toSecondsText())
    }
}
