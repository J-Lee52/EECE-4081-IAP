package com.example.eece4081_iap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eece4081_iap.domain.SessionStatisticsCalculator
import com.example.eece4081_iap.domain.ShootingSession

/**
 * Shows one saved session, read-only (US07): when it was recorded, the
 * settings it ran with, every shot with its time and split, and its
 * statistics, recalculated from the stored shots. Nothing here changes the
 * stored session.
 *
 * Delete asks for confirmation first, because a deleted session cannot be
 * recovered. Back (the button or Android's Back) returns to the timer.
 */
@Composable
fun SessionReviewScreen(
    session: ShootingSession,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingDelete by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    val stats = SessionStatisticsCalculator.calculate(session.shots)

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this session?") },
            text = {
                Text(
                    "${ResultsText.sessionStart(session.startTime)}, ${ResultsText.shotCount(session.shots.size)}. " +
                        "This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text("Saved session", style = MaterialTheme.typography.headlineMedium) }
        item { Text(ResultsText.sessionStart(session.startTime), style = MaterialTheme.typography.titleMedium) }
        item { Text(SettingsDraft.summary(session.settings), style = MaterialTheme.typography.bodySmall) }
        item { HorizontalDivider() }
        item { Text(ResultsText.summary(stats), style = MaterialTheme.typography.titleMedium) }
        item { Text(ResultsText.splitStatistics(stats)) }
        items(session.shots, key = { "shot-${it.shotNumber}" }) { shot ->
            Text(ResultsText.shotRow(shot))
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onBack) { Text("Back") }
                Spacer(Modifier.width(12.dp))
                TextButton(
                    onClick = { confirmingDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            }
        }
    }
}
