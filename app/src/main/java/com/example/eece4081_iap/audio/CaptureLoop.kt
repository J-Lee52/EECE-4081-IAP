package com.example.eece4081_iap.audio

/**
 * The microphone read loop used by [AndroidAudioInput], kept free of Android
 * types so its rules can be tested on the JVM:
 *
 * - each successful read is handed to [onSamples] as a copy (the buffer is
 *   reused for the next read);
 * - a read of 0 samples is skipped;
 * - a negative read result is a capture failure and is reported to [onError]
 *   once, but only while [isRunning] is still true. stop() deliberately makes a
 *   blocked read return early, and that must not be reported as a failure.
 *
 * Returns when [isRunning] becomes false or after a failure.
 */
internal fun runCaptureLoop(
    buffer: ShortArray,
    read: (ShortArray) -> Int,
    isRunning: () -> Boolean,
    onSamples: (ShortArray) -> Unit,
    onError: (AudioInputError) -> Unit,
) {
    while (isRunning()) {
        val count = read(buffer)
        if (count < 0) {
            if (isRunning()) onError(AudioInputError(count, "read returned $count"))
            return
        }
        if (count == 0) continue
        if (!isRunning()) return
        onSamples(buffer.copyOf(count))
    }
}
