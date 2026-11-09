package com.example.eece4081_iap.audio

/** Plays the start beep. */
interface BeepPlayer {

    /**
     * Starts playing the start beep and calls [onFinished] once, when the beep
     * has finished playing. TimerEngine ignores microphone input until then so
     * the beep cannot be recorded as a shot.
     *
     * If the beep cannot be played, [onUnavailable] is called first and
     * [onFinished] still follows, so a run is never left waiting. The
     * implementation only reports the problem; the caller logs it against
     * the run that asked for the beep.
     */
    fun play(onFinished: () -> Unit, onUnavailable: (BeepError) -> Unit)
}

/** Why the start beep could not be played: [error] is the failure's type name. */
data class BeepError(val error: String, val message: String?)
