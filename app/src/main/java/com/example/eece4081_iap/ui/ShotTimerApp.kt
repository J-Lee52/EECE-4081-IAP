package com.example.eece4081_iap.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * The app's screens: the timer, the Settings screen (US01), and the review of
 * one saved session (US07), both opened from the timer. All share one
 * [TimerViewModel]. A simple switch is enough for three screens, so no
 * navigation library is used.
 */
@Composable
fun ShotTimerApp(
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = viewModel(factory = timerViewModelFactory()),
) {
    // Which screen is showing: TIMER, SETTINGS, or "review:<session id>".
    var screen by rememberSaveable { mutableStateOf(TIMER) }

    when {
        screen == SETTINGS -> SettingsScreen(
            initial = viewModel.settings,
            onSave = { newSettings -> if (viewModel.saveSettings(newSettings)) screen = TIMER },
            onCancel = { screen = TIMER },
            modifier = modifier,
        )
        screen.startsWith(REVIEW) -> {
            val id = screen.removePrefix(REVIEW)
            val session = viewModel.history.firstOrNull { it.id == id }
            if (session == null) {
                // Deleted, or no longer in the history: go back to the timer.
                LaunchedEffect(id) { screen = TIMER }
            } else {
                SessionReviewScreen(
                    session = session,
                    onBack = { screen = TIMER },
                    onDelete = { viewModel.deleteSession(session.id, afterDeleted = { screen = TIMER }) },
                    modifier = modifier,
                )
            }
        }
        else -> TimerScreen(
            modifier = modifier,
            viewModel = viewModel,
            onOpenSettings = { screen = SETTINGS },
            onOpenSession = { id -> screen = REVIEW + id },
        )
    }
}

private const val TIMER = "timer"
private const val SETTINGS = "settings"
private const val REVIEW = "review:"
