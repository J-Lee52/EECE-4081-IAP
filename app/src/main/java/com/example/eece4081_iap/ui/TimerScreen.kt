package com.example.eece4081_iap.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.eece4081_iap.engine.TimerState
import java.text.DateFormat
import java.util.Date

/**
 * The one screen of the walking skeleton: Start/Stop, the live shots and
 * statistics, and the list of saved sessions. It only draws what the
 * [TimerViewModel] holds and passes button presses to it. The microphone
 * permission is requested here, because only the screen can show the prompt.
 */
@Composable
fun TimerScreen(
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = viewModel(factory = timerViewModelFactory()),
) {
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        if (granted) viewModel.start()
    }

    val onStartClicked = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            permissionDenied = false
            viewModel.start()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val snapshot = viewModel.snapshot
    val state = snapshot.state
    val stats = snapshot.statistics
    val shots = snapshot.session?.shots.orEmpty()
    val history = viewModel.history
    val errorMessage = viewModel.errorMessage

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Shot Timer", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            Text(statusText(state), style = MaterialTheme.typography.titleMedium)
        }
        item {
            when (state) {
                TimerState.Recording -> Button(onClick = { viewModel.stop() }) { Text("Stop") }
                TimerState.Idle, TimerState.Completed ->
                    Button(onClick = { onStartClicked() }) { Text("Start") }
                else -> Button(onClick = {}, enabled = false) { Text("Please wait") }
            }
        }

        if (permissionDenied) {
            item {
                Text(
                    "Microphone permission is needed to detect shots. " +
                        "Tap Start to ask again, or allow it in the app's settings.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (errorMessage != null) {
            item {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { viewModel.dismissError() }) { Text("Dismiss") }
            }
        }

        item { HorizontalDivider() }
        item {
            Text(
                "Shots: ${stats.shotCount}    Total: ${stats.totalTime.orDash()}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            Text(
                "Fastest split: ${stats.fastestSplit.orDash()}\n" +
                    "Average split: ${stats.averageSplit.orDash()}\n" +
                    "Slowest split: ${stats.slowestSplit.orDash()}",
            )
        }
        items(shots, key = { "shot-${it.shotNumber}" }) { shot ->
            Text(
                "#${shot.shotNumber}   ${shot.timestamp.toSecondsText()}" +
                    "   split ${shot.splitTime.orDash()}",
            )
        }

        item { HorizontalDivider() }
        item {
            Text("Past sessions", style = MaterialTheme.typography.titleMedium)
        }
        if (history.isEmpty()) {
            item { Text("No saved sessions yet.") }
        }
        items(history, key = { "session-${it.id}" }) { session ->
            Text(
                "${formatStart(session.startTime)} — ${session.shots.size} shots, " +
                    "total ${session.shots.lastOrNull()?.timestamp.orDash()}",
            )
        }
    }
}

private fun statusText(state: TimerState): String = when (state) {
    TimerState.Idle -> "Ready"
    TimerState.Preparing -> "Preparing…"
    TimerState.WaitingForStart -> "Get ready — the beep is coming"
    TimerState.Recording -> "Recording — fire when ready"
    TimerState.Completed -> "Session complete"
}

private fun kotlin.time.Duration?.orDash(): String = this?.toSecondsText() ?: "—"

private fun formatStart(startTime: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(startTime))
