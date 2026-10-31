package com.fyiplayer.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing

private val PRESETS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f, 3f)

/**
 * Playback-speed control: live value, nudge buttons, slider that snaps to 0.25 marks, presets.
 * Every change applies at once through [onChange]; the sheet stays open so the user hears it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(current: Float, onChange: (Float) -> Unit, onDismiss: () -> Unit) {
    // Fully open: half-open in landscape fullscreen cut the presets off below the screen edge.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.padding(bottom = Spacing.s24)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.s24, end = Spacing.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.player_menu_speed),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onChange(1f) }, enabled = current != 1f) {
                    Text(stringResource(R.string.player_speed_reset))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NudgeButton(R.string.player_speed_minus, R.string.player_speed_slower) { onChange(roundSpeed(current - SPEED_STEP)) }
                Text(
                    formatSpeed(current),
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                NudgeButton(R.string.player_speed_plus, R.string.player_speed_faster) { onChange(roundSpeed(current + SPEED_STEP)) }
            }
            val sliderLabel = stringResource(R.string.player_menu_speed)
            val sliderState = formatSpeed(current)
            // The player's thin seek-bar look, in the accent colour. 80% wide and centred, so a
            // drag to either end starts clear of the system back-gesture edge.
            val fraction = (current - SPEED_MIN) / (SPEED_MAX - SPEED_MIN)
            val accent = MaterialTheme.colorScheme.primary
            Slider(
                value = current,
                onValueChange = { onChange(snapSpeed(it)) },
                valueRange = SPEED_MIN..SPEED_MAX,
                track = {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(Sizes.progressThin)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                    ) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(accent))
                    }
                },
                thumb = {
                    Box(Modifier.size(Spacing.s24), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(Sizes.iconSmall).clip(CircleShape).background(accent))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .align(Alignment.CenterHorizontally)
                    .height(Spacing.s40)
                    .semantics { contentDescription = sliderLabel; stateDescription = sliderState },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
                modifier = Modifier.padding(horizontal = Spacing.s16),
            ) {
                PRESETS.forEach { preset ->
                    FilterChip(
                        selected = current == preset,
                        onClick = { onChange(preset) },
                        label = { Text(formatSpeed(preset)) },
                    )
                }
            }
        }
    }
}

// A text glyph: material-icons-core has no Remove icon.
@Composable
private fun NudgeButton(glyph: Int, description: Int, onClick: () -> Unit) {
    val label = stringResource(description)
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(Sizes.touchTarget).semantics { contentDescription = label }) {
        Text(stringResource(glyph), style = MaterialTheme.typography.titleLarge)
    }
}
