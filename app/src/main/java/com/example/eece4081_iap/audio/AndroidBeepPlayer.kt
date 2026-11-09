package com.example.eece4081_iap.audio

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * The real start beep, played with [ToneGenerator].
 *
 * The finished callback of [play] runs on the main thread once the tone's
 * duration plus a short settle time has passed, so the microphone does not
 * hear the tail of the beep as a shot.
 *
 * If the device cannot create a tone generator, no beep is played: the
 * unavailable callback reports a [BeepError] (TimerEngine then ends the run
 * and tells the person), and the finished callback still follows, so nothing
 * is left waiting.
 */
class AndroidBeepPlayer(
    private val toneMillis: Int = 300,
    private val settleMillis: Long = 50,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : BeepPlayer {

    override fun play(onFinished: () -> Unit, onUnavailable: (BeepError) -> Unit) {
        val tone = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME_PERCENT)
        } catch (e: RuntimeException) {
            onUnavailable(BeepError(e.javaClass.simpleName, e.message))
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
        const val VOLUME_PERCENT = 100
    }
}
