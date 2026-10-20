package com.example.eece4081_iap.domain

import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class ShotTest {

    @Test
    fun shotNumberZero_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Shot(shotNumber = 0, timestamp = 1.milliseconds, splitTime = null)
        }
    }

    @Test
    fun aShotBeforeTheBeep_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Shot(shotNumber = 1, timestamp = (-1).milliseconds, splitTime = null)
        }
    }

    @Test
    fun aNegativeSplit_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            Shot(shotNumber = 2, timestamp = 1.milliseconds, splitTime = (-1).milliseconds)
        }
    }
}
