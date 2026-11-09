package com.example.eece4081_iap.audio

/**
 * Source of live microphone samples (ADR-001: audio acquisition is kept
 * separate from shot detection).
 *
 * This interface has no Android types so the engine and detector can be
 * tested on the JVM with a fake. The real implementation (AudioRecord) is
 * added in a later step.
 */
interface AudioInput {

    /**
     * Starts capturing. [onSamples] is called repeatedly with buffers of
     * 16-bit PCM samples, possibly from a background thread. A buffer is only
     * valid during the callback; copy it if it must be kept.
     *
     * [onError] is called at most once if capturing fails after it started
     * (for example, the audio device was lost). No more samples follow it.
     * The implementation only reports the failure; the caller decides what
     * to do about it and logs it against the run that owns the capture.
     *
     * Throws if the microphone cannot be opened (for example, when the
     * RECORD_AUDIO permission has not been granted).
     */
    fun start(onSamples: (ShortArray) -> Unit, onError: (AudioInputError) -> Unit)

    /** Stops capturing and releases the microphone. Safe to call when not started. */
    fun stop()
}

/**
 * A capture failure reported by [AudioInput] after capturing had started.
 * [code] is the platform's error code (for AudioRecord, a negative read result).
 */
data class AudioInputError(val code: Int, val message: String)
