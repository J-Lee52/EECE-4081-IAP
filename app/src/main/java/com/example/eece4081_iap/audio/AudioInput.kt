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
     * Throws if the microphone cannot be opened (for example, when the
     * RECORD_AUDIO permission has not been granted).
     */
    fun start(onSamples: (ShortArray) -> Unit)

    /** Stops capturing and releases the microphone. Safe to call when not started. */
    fun stop()
}
