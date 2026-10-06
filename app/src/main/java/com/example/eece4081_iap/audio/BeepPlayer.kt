package com.example.eece4081_iap.audio

/** Plays the start beep. The real implementation is added in a later step. */
interface BeepPlayer {

    /**
     * Starts playing the start beep and calls [onFinished] once, when the beep
     * has finished playing. TimerEngine ignores microphone input until then so
     * the beep cannot be recorded as a shot.
     */
    fun play(onFinished: () -> Unit)
}
