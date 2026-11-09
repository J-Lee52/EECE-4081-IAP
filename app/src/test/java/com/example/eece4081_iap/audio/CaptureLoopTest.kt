package com.example.eece4081_iap.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureLoopTest {

    private val samples = mutableListOf<List<Short>>()
    private val errors = mutableListOf<AudioInputError>()

    /** Plays back [results] as read() return values, filling the buffer with the result as a sample value. */
    private fun run(results: List<Int>, running: () -> Boolean = { true }, onRead: (Int) -> Unit = {}) {
        val queue = ArrayDeque(results)
        runCaptureLoop(
            buffer = ShortArray(4),
            read = { buffer ->
                val result = queue.removeFirstOrNull() ?: -1000 // end the loop if a test forgets
                onRead(result)
                if (result > 0) buffer.fill(result.toShort(), 0, result)
                result
            },
            isRunning = running,
            onSamples = { samples += it.toList() },
            onError = { errors += it },
        )
    }

    @Test
    fun eachRead_isHandedOnAsACopyOfOnlyTheSamplesRead() {
        run(listOf(2, 3, -1))

        assertEquals(listOf(listOf<Short>(2, 2), listOf<Short>(3, 3, 3)), samples)
    }

    @Test
    fun aReadOfZero_isSkipped() {
        run(listOf(0, 1, -1))

        assertEquals(listOf(listOf<Short>(1)), samples)
    }

    @Test
    fun aFailedReadWhileRunning_isReportedOnceAndEndsTheLoop() {
        run(listOf(1, -6, 1))

        assertEquals(listOf(AudioInputError(-6, "read returned -6")), errors)
        assertEquals("nothing after the failure", 1, samples.size)
    }

    @Test
    fun stop_whichMakesTheBlockedReadReturnAnError_isNotReportedAsAFailure() {
        var running = true
        // stop() runs while read() is blocked: it clears the flag and the read
        // returns an error code, exactly as AudioRecord does after stop().
        run(listOf(1, -3), running = { running }, onRead = { result -> if (result < 0) running = false })

        assertTrue("stop() must not produce a read error", errors.isEmpty())
        assertEquals(1, samples.size)
    }

    @Test
    fun samplesReadJustAfterStop_areDropped() {
        var running = true
        run(listOf(2), running = { running }, onRead = { running = false })

        assertTrue(samples.isEmpty())
        assertTrue(errors.isEmpty())
    }
}
