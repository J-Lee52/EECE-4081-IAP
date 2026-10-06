package com.example.eece4081_iap.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.eece4081_iap.app.AndroidClock
import com.example.eece4081_iap.app.MainThreadScheduler
import com.example.eece4081_iap.audio.AndroidAudioInput
import com.example.eece4081_iap.audio.AndroidBeepPlayer
import com.example.eece4081_iap.data.FileSessionRepository
import com.example.eece4081_iap.engine.TimerEngine
import java.io.File
import java.util.concurrent.Executors

/**
 * Builds [TimerViewModel] with the real microphone, beep, clock, scheduler
 * and file storage. Kept apart from the ViewModel so its unit tests never
 * load Android classes.
 *
 * Use it as `viewModel(factory = timerViewModelFactory())`.
 */
fun timerViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
        val mainHandler = Handler(Looper.getMainLooper())
        val ioThread = Executors.newSingleThreadExecutor { task ->
            Thread(task, "ShotTimer-io").apply { isDaemon = true }
        }
        TimerViewModel(
            engine = TimerEngine(
                audioInput = AndroidAudioInput(),
                beepPlayer = AndroidBeepPlayer(),
                clock = AndroidClock(),
                scheduler = MainThreadScheduler(),
            ),
            repository = FileSessionRepository(File(application.filesDir, "sessions")),
            postToMain = { action -> mainHandler.post(action) },
            background = ioThread,
        )
    }
}
