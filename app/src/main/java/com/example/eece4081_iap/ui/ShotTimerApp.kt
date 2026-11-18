package com.example.eece4081_iap.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * The app's screens: the timer, and the Settings screen opened from it.
 * Both share one [TimerViewModel]. A simple switch is enough for two screens,
 * so no navigation library is used.
 */
@Composable
fun ShotTimerApp(
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = viewModel(factory = timerViewModelFactory()),
) {
    var showingSettings by rememberSaveable { mutableStateOf(false) }

    if (showingSettings) {
        SettingsScreen(
            initial = viewModel.settings,
            onSave = { newSettings -> if (viewModel.saveSettings(newSettings)) showingSettings = false },
            onCancel = { showingSettings = false },
            modifier = modifier,
        )
    } else {
        TimerScreen(
            modifier = modifier,
            viewModel = viewModel,
            onOpenSettings = { showingSettings = true },
        )
    }
}
