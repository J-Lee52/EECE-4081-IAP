package com.example.eece4081_iap.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.eece4081_iap.domain.TimerSettings
import com.example.eece4081_iap.ui.SettingsDraft.Companion.COOLDOWN_MAX_MS
import com.example.eece4081_iap.ui.SettingsDraft.Companion.COOLDOWN_STEP_MS
import com.example.eece4081_iap.ui.SettingsDraft.Companion.DELAY_MAX_TENTHS
import com.example.eece4081_iap.ui.SettingsDraft.Companion.THRESHOLD_MAX
import com.example.eece4081_iap.ui.SettingsDraft.Companion.THRESHOLD_MIN
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The Settings screen (US01): detection threshold, shot cooldown and the
 * start delay (Fixed, or a Random Range with a minimum and maximum).
 *
 * Edits stay in a [SettingsDraft] until Save; Cancel or Android's Back
 * button leaves without changing anything. The draft cannot hold a minimum
 * above the maximum, so Save always has valid settings to pass on.
 */
@Composable
fun SettingsScreen(
    initial: TimerSettings,
    onSave: (TimerSettings) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(initial) { mutableStateOf(SettingsDraft.from(initial)) }
    BackHandler(onBack = onCancel)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        StepSlider(
            label = "Detection threshold",
            valueText = "%.2f".format(Locale.ROOT, draft.thresholdHundredths / 100.0),
            value = draft.thresholdHundredths,
            range = THRESHOLD_MIN..THRESHOLD_MAX,
            step = 1,
            onChange = { draft = draft.withThreshold(it) },
        )
        Text(
            "A sound counts as a shot when its loudness reaches this level. Lower it if shots are missed.",
            style = MaterialTheme.typography.bodySmall,
        )

        StepSlider(
            label = "Shot cooldown",
            valueText = "${draft.cooldownMs} ms",
            value = draft.cooldownMs,
            range = 0..COOLDOWN_MAX_MS,
            step = COOLDOWN_STEP_MS,
            onChange = { draft = draft.withCooldown(it) },
        )
        Text(
            "Sounds within this time after a shot are treated as its echo.",
            style = MaterialTheme.typography.bodySmall,
        )

        Text("Start delay", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            ModeOption("Fixed", selected = !draft.randomRange) { draft = draft.withRandomRange(false) }
            ModeOption("Random range", selected = draft.randomRange) { draft = draft.withRandomRange(true) }
        }

        if (draft.randomRange) {
            StepSlider(
                label = "Minimum delay",
                valueText = SettingsDraft.tenthsText(draft.minTenths),
                value = draft.minTenths,
                range = 0..DELAY_MAX_TENTHS,
                step = 1,
                onChange = { draft = draft.withMin(it) },
            )
            StepSlider(
                label = "Maximum delay",
                valueText = SettingsDraft.tenthsText(draft.maxTenths),
                value = draft.maxTenths,
                range = 0..DELAY_MAX_TENTHS,
                step = 1,
                onChange = { draft = draft.withMax(it) },
            )
            Text(
                "The beep comes at a random time between " +
                    "${SettingsDraft.tenthsText(draft.minTenths)} and ${SettingsDraft.tenthsText(draft.maxTenths)}.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            StepSlider(
                label = "Delay",
                valueText = SettingsDraft.tenthsText(draft.fixedTenths),
                value = draft.fixedTenths,
                range = 0..DELAY_MAX_TENTHS,
                step = 1,
                onChange = { draft = draft.withFixed(it) },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(12.dp))
            Button(onClick = { onSave(draft.toSettings()) }) { Text("Save") }
        }
    }
}

/** A labelled slider over whole steps of [step] within [range]; [label] is also its accessibility name. */
@Composable
private fun StepSlider(
    label: String,
    valueText: String,
    value: Int,
    range: IntRange,
    step: Int,
    onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Text(valueText)
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange((it / step).roundToInt() * step) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first) / step - 1,
        modifier = Modifier.semantics { contentDescription = label },
    )
}

@Composable
private fun ModeOption(text: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}
