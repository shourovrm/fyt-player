package com.fyiplayer.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.theme.ControlShape
import com.fyiplayer.app.ui.theme.NumeralStyle
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
            SheetTitleRow(stringResource(R.string.player_menu_speed), onClose = onDismiss)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NudgeButton(R.string.player_speed_minus, R.string.player_speed_slower) { onChange(roundSpeed(current - SPEED_STEP)) }
                Text(
                    formatSpeed(current),
                    style = MaterialTheme.typography.displayMedium.merge(NumeralStyle),
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
                            .clip(RoundedCornerShape(2.dp))
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
            // Scrolls rather than wraps: six numeral labels overflow a 360 dp phone.
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s8),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.s16),
            ) {
                PRESETS.forEach { preset ->
                    FilterChip(
                        selected = current == preset,
                        onClick = { onChange(preset) },
                        label = { Text(formatSpeed(preset), style = MaterialTheme.typography.labelLarge.merge(NumeralStyle)) },
                        shape = ControlShape,
                    )
                }
            }
            FilledTonalButton(
                onClick = { onChange(1f) },
                enabled = current != 1f,
                shape = ControlShape,
                modifier = Modifier.padding(start = Spacing.s16, top = Spacing.s16),
            ) {
                Text(stringResource(R.string.player_speed_reset))
            }
        }
    }
}

// A text glyph: material-icons-core has no Remove icon. A square tile, not FilledTonalIconButton,
// whose shape is a circle.
@Composable
private fun NudgeButton(glyph: Int, description: Int, onClick: () -> Unit) {
    val label = stringResource(description)
    Surface(
        onClick = onClick,
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(Sizes.touchTarget).semantics { contentDescription = label },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(glyph), style = MaterialTheme.typography.titleLarge)
        }
    }
}
