package com.example.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.music.data.UserState

@Composable
fun SettingsScreen(
    state: UserState,
    contentPadding: PaddingValues,
    trackCount: Int,
    onEditName: () -> Unit,
    onPickPhoto: () -> Unit,
    onChange: ((UserState) -> UserState) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            ProfileHeader(
                name = state.displayName,
                avatarUri = state.avatarUri,
                trackCount = trackCount,
                playedCount = state.playCounts.size,
                favouriteCount = state.favorites.size,
                playlistCount = state.playlists.size,
                onEdit = onEditName,
                onPickPhoto = onPickPhoto,
            )
        }
        item {
            Section("Playback") {
                Text(
                    if (state.crossfadeSeconds == 0) "Fade between tracks: off"
                    else "Fade between tracks: ${state.crossfadeSeconds}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextHi,
                )
                Slider(
                    value = state.crossfadeSeconds.toFloat(),
                    onValueChange = { v -> onChange { it.copy(crossfadeSeconds = Math.round(v)) } },
                    valueRange = 0f..12f,
                    colors = sliderColors(),
                )
                Text(
                    "Speed: ${"%.2f".format(state.playbackSpeed)}x",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextHi,
                )
                Slider(
                    value = state.playbackSpeed,
                    onValueChange = { v ->
                        // Snap to 0.05 steps; a slider that lands on 1.0033x is not usable.
                        onChange { it.copy(playbackSpeed = (Math.round(v * 20f) / 20f)) }
                    },
                    valueRange = 0.5f..2.0f,
                    colors = sliderColors(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Skip silence", color = TextHi, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Shortens quiet gaps inside a track",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextLo,
                        )
                    }
                    Switch(
                        checked = state.skipSilence,
                        onCheckedChange = { on -> onChange { it.copy(skipSilence = on) } },
                    )
                }
                Text(
                    "Gapless playback is always on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLo,
                )
            }
        }

        item {
            Section("Equalizer") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enabled", Modifier.weight(1f), color = TextHi)
                    Switch(
                        checked = state.eqEnabled,
                        onCheckedChange = { on -> onChange { it.copy(eqEnabled = on) } },
                    )
                }
                if (state.eqEnabled) {
                    PresetChips(state.eqPreset) { p -> onChange { it.copy(eqPreset = p) } }
                    Text("Bass boost", color = TextHi, style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = state.bassBoost.toFloat(),
                        onValueChange = { v -> onChange { it.copy(bassBoost = v.toInt()) } },
                        valueRange = 0f..1000f,
                        colors = sliderColors(),
                    )
                }
                Text(
                    "Effects depend on device hardware and may not apply on every phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLo,
                )
            }
        }

        item {
            Section("Sleep timer") {
                SleepTimerControls(state, onChange)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetChips(selected: Int, onPick: (Int) -> Unit) {
    // Standard AudioEffect preset ordering; names come from the device but the order is fixed.
    val names = listOf("Normal", "Classical", "Dance", "Flat", "Folk", "Heavy Metal", "Hip Hop",
        "Jazz", "Pop", "Rock")
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        names.forEachIndexed { i, name ->
            FilterChip(selected = i == selected, onClick = { onPick(i) }, label = { Text(name) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimerControls(state: UserState, onChange: ((UserState) -> UserState) -> Unit) {
    val active = state.sleepTimerEndsAt > System.currentTimeMillis()
    val remaining = ((state.sleepTimerEndsAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)

    Text(
        if (active) "Stops in about $remaining min" else "Off",
        style = MaterialTheme.typography.bodyMedium,
        color = if (active) LocalAccent.current else TextLo,
    )
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(15, 30, 45, 60, 90).forEach { minutes ->
            FilterChip(
                selected = false,
                onClick = {
                    val endsAt = System.currentTimeMillis() + minutes * 60_000L
                    onChange { it.copy(sleepTimerEndsAt = endsAt) }
                },
                label = { Text("$minutes min") },
            )
        }
        FilterChip(
            selected = false,
            onClick = { onChange { it.copy(sleepTimerEndsAt = 0) } },
            label = { Text("Off") },
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Finish the current track", color = TextHi, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Wait for the song to end instead of stopping mid-way",
                style = MaterialTheme.typography.bodySmall,
                color = TextLo,
            )
        }
        Switch(
            checked = state.sleepAtTrackEnd,
            onCheckedChange = { on -> onChange { it.copy(sleepAtTrackEnd = on) } },
        )
    }
}

@Composable
private fun sliderColors() = androidx.compose.material3.SliderDefaults.colors(
    thumbColor = LocalAccent.current,
    activeTrackColor = LocalAccent.current,
    inactiveTrackColor = Surface2,
)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = LocalAccent.current,
        )
        content()
    }
}
