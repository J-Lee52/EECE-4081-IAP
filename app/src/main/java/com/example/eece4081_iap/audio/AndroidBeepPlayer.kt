package com.example.eece4081_iap.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * The real start beep, played with [ToneGenerator].
 *
 * [onFinished] is called on the main thread once the tone's duration plus a
 * short settle time has passed, so the microphone does not hear the tail of
 * the beep as a shot.
 *
 * If the device cannot create a tone generator the beep is skipped and
 * [onFinished] still runs, so a timer run is never left stuck waiting.
 */
class AndroidBeepPlayer(
    private val toneMillis: Int = 300,
    private val settleMillis: Long = 50,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : BeepPlayer {

    override fun play(onFinished: () -> Unit) {
        val tone = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME_PERCENT)
        } catch (e: RuntimeException) {
            Log.w(TAG, "No tone generator; skipping the beep", e)
            handler.post(onFinished)
            return
        }

        tone.startTone(ToneGenerator.TONE_PROP_BEEP, toneMillis)
        handler.postDelayed({
            tone.release()
            onFinished()
        }, toneMillis + settleMillis)
    }

    private companion object {
        const val TAG = "AndroidBeepPlayer"
        const val VOLUME_PERCENT = 100
    }
}
