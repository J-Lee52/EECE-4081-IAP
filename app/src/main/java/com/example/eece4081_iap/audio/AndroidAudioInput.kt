package com.example.eece4081_iap.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread

/**
 * The real microphone: reads 16-bit mono PCM with [AudioRecord] on a
 * background thread and hands each buffer to the listener.
 *
 * The caller must already hold the RECORD_AUDIO permission. Without it
 * [start] throws, which TimerEngine turns into a return to Idle.
 *
 * Each buffer is [chunkMillis] long (10 ms by default) so shot timestamps
 * are accurate to about NFR 01's 0.01 s.
 *
 * [stop] does not wait for the audio thread. TimerEngine calls it while
 * holding its own lock, and the audio thread may be waiting for that same
 * lock inside the listener, so waiting here could deadlock. A buffer that
 * arrives just after stop() is ignored by the engine because it is no
 * longer Recording.
 */
class AndroidAudioInput(
    private val sampleRate: Int = SAMPLE_RATE,
    private val chunkMillis: Int = 10,
) : AudioInput {

    /** One recording run. Its own flag stops an old thread from seeing a restart. */
    private class Capture(val recorder: AudioRecord) {
        @Volatile
        var running = true
    }

    private var capture: Capture? = null

    @SuppressLint("MissingPermission") // the caller checks RECORD_AUDIO before start()
    @Synchronized
    override fun start(onSamples: (ShortArray) -> Unit) {
        check(capture == null) { "Microphone is already recording" }

        val chunkSamples = sampleRate * chunkMillis / 1000
        val minBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBytes > 0) { "Microphone format not supported (code $minBytes)" }
        val bufferBytes = maxOf(minBytes, chunkSamples * BYTES_PER_SAMPLE * 4)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferBytes,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Microphone could not be opened (permission or busy)")
        }
        try {
            recorder.startRecording()
        } catch (e: IllegalStateException) {
            recorder.release()
            throw e
        }
        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            recorder.release()
            throw IllegalStateException("Microphone did not start recording")
        }

        val run = Capture(recorder)
        capture = run
        thread(name = "ShotTimer-audio", isDaemon = true) {
            val buffer = ShortArray(chunkSamples)
            try {
                while (run.running) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read < 0) break // error such as ERROR_DEAD_OBJECT
                    if (read == 0) continue
                    if (!run.running) break
                    // Copy: the same array is reused for the next read.
                    onSamples(buffer.copyOf(read))
                }
            } finally {
                recorder.release()
            }
        }
    }

    @Synchronized
    override fun stop() {
        val run = capture ?: return
        capture = null
        run.running = false
        try {
            run.recorder.stop() // makes a blocked read() return
        } catch (e: IllegalStateException) {
            // already stopped; the audio thread still releases the recorder
        }
    }

    companion object {
        /** 44.1 kHz is the one rate every Android device must support. */
        const val SAMPLE_RATE = 44_100
        private const val BYTES_PER_SAMPLE = 2
    }
}
