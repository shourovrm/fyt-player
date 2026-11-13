package com.fyiplayer.app.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.theme.ControlShape
import com.fyiplayer.app.ui.theme.NumeralStyle
import com.fyiplayer.app.ui.theme.Radius
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing

private val PRESETS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f, 3f)

/**
 * Playback-speed control as a bottom sheet: live value, nudge buttons around a slider that snaps
 * to 0.25 marks, presets. Every change applies at once through [onChange]; the sheet stays open
 * so the user hears it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(current: Float, onChange: (Float) -> Unit, onDismiss: () -> Unit) {
    // Fully open: half-open in landscape (Shorts) cut the presets off below the screen edge.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.padding(bottom = Spacing.s24)) {
            SheetTitleRow(stringResource(R.string.player_menu_speed), onClose = onDismiss)
            Column(modifier = Modifier.padding(horizontal = Spacing.pageMargin)) {
                SpeedValue(current)
                SectionLabel(R.string.player_speed_fine_tune)
                SpeedFineTune(current, onChange)
                SectionLabel(R.string.player_speed_presets)
                SpeedPresets(current, onChange)
            }
        }
    }
}

/**
 * The same control for the fullscreen player: a small card above the seek bar, so the video
 * stays visible. A full-width sheet covered most of a landscape screen. Fills its parent to
 * catch the tap outside that closes it.
 */
@Composable
fun SpeedCard(current: Float, onChange: (Float) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val closeLabel = stringResource(R.string.action_close)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = null, indication = null, onClickLabel = closeLabel, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.m),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            // Bottom gap = the control row plus the seek bar under the card.
            modifier = Modifier
                .padding(bottom = Sizes.touchTarget + Spacing.s40 + Spacing.s8)
                .width(Sizes.speedCardWidth),
        ) {
            Column(modifier = Modifier.padding(start = Spacing.s16, end = Spacing.s16, top = Spacing.s8, bottom = Spacing.s16)) {
                SpeedValue(current)
                SpeedFineTune(current, onChange)
                SectionLabel(R.string.player_speed_presets)
                SpeedPresets(current, onChange)
            }
        }
    }
}

@Composable
private fun SpeedValue(current: Float) {
    Text(
        formatSpeed(current),
        style = MaterialTheme.typography.headlineMedium.merge(NumeralStyle),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SectionLabel(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.s16, bottom = Spacing.s8),
    )
}

/** Minus, slider, plus. The buttons keep the slider's ends clear of the system back-gesture edge. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedFineTune(current: Float, onChange: (Float) -> Unit) {
    val sliderLabel = stringResource(R.string.player_menu_speed)
    val sliderState = formatSpeed(current)
    val fraction = (current - SPEED_MIN) / (SPEED_MAX - SPEED_MIN)
    val accent = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
        NudgeButton(R.string.player_speed_minus, R.string.player_speed_slower) { onChange(roundSpeed(current - SPEED_STEP)) }
        Slider(
            value = current,
            onValueChange = { onChange(snapSpeed(it)) },
            valueRange = SPEED_MIN..SPEED_MAX,
            // The player's thin seek-bar look, in the accent colour.
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
                .weight(1f)
                .height(Spacing.s40)
                .semantics { contentDescription = sliderLabel; stateDescription = sliderState },
        )
        NudgeButton(R.string.player_speed_plus, R.string.player_speed_faster) { onChange(roundSpeed(current + SPEED_STEP)) }
    }
}

/** Six equal tiles across the full width. Material chips packed from the left edge and needed a
 *  scroll at 360 dp; the selected tile is outlined, not filled, so it does not glare. */
@Composable
private fun SpeedPresets(current: Float, onChange: (Float) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
        PRESETS.forEach { preset ->
            val selected = current == preset
            val borderColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            Surface(
                onClick = { onChange(preset) },
                shape = ControlShape,
                color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.weight(1f).height(Sizes.touchTarget).semantics { this.selected = selected },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        formatSpeed(preset),
                        style = MaterialTheme.typography.labelMedium.merge(NumeralStyle),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
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
