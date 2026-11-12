package com.fyiplayer.app.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.theme.ControlShape
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing

/** The preset choices, in minutes. Anything else is a custom timer. */
private val SLEEP_TIMER_PRESETS = listOf(15, 30, 45, 60)

/**
 * Sleep timer picker, same shape as [CaptionSheet]: Off, the presets, then Custom, which asks for
 * a number of minutes. A preset or Off closes the sheet. While a timer runs, a line under the
 * title counts down and the chosen row carries the check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(onDismiss: () -> Unit) {
    val running by SleepTimer.running.collectAsState()
    var askCustom by remember { mutableStateOf(false) }
    val chosen = running?.minutes
    val customChosen = chosen != null && chosen !in SLEEP_TIMER_PRESETS

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(modifier = Modifier.padding(bottom = Spacing.s16)) {
            item { SheetTitleRow(stringResource(R.string.player_sleep_timer), onClose = onDismiss) }
            running?.let { timer ->
                item {
                    Text(
                        stringResource(R.string.sleep_timer_pauses_in, formatSleepTimerRemaining(timer.remainingMs)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.pageMargin),
                    )
                }
            }
            item {
                ChoiceListItem(
                    headline = stringResource(R.string.sleep_timer_off),
                    selected = chosen == null,
                    onSelect = { SleepTimer.cancel(); onDismiss() },
                )
            }
            items(SLEEP_TIMER_PRESETS) { minutes ->
                ChoiceListItem(
                    headline = sleepTimerPresetLabel(minutes),
                    selected = minutes == chosen,
                    onSelect = { SleepTimer.start(minutes); onDismiss() },
                )
            }
            item {
                ChoiceListItem(
                    headline = stringResource(R.string.sleep_timer_custom),
                    selected = customChosen,
                    onSelect = { askCustom = true },
                )
            }
        }
    }
    if (askCustom) {
        CustomSleepTimerDialog(
            onStart = { minutes -> SleepTimer.start(minutes); askCustom = false; onDismiss() },
            onDismiss = { askCustom = false },
        )
    }
}

@Composable
private fun sleepTimerPresetLabel(minutes: Int): String =
    if (minutes == 60) {
        stringResource(R.string.sleep_timer_one_hour)
    } else {
        pluralStringResource(R.plurals.sleep_timer_minutes, minutes, minutes)
    }

/** Asks for whole minutes; Start stays disabled until the field holds 1 to 720. */
@Composable
private fun CustomSleepTimerDialog(onStart: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val minutes = parseSleepTimerMinutes(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_timer_custom_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.sleep_timer_minutes_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(shape = ControlShape, enabled = minutes != null, onClick = { minutes?.let(onStart) }) {
                Text(stringResource(R.string.sleep_timer_start))
            }
        },
        dismissButton = {
            TextButton(shape = ControlShape, onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * The sleep timer button's face: a crescent moon, plus a badge with the whole minutes left while
 * a timer runs. Shared by the player's top corner and the Shorts rail.
 */
@Composable
fun SleepTimerGlyph(tint: Color, size: Dp = Sizes.icon) {
    val running by SleepTimer.running.collectAsState()
    Box(Modifier.size(size)) {
        MoonGlyph(tint, size)
        running?.let { timer ->
            Text(
                sleepTimerMinutesLeft(timer.remainingMs).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.scrim,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-6).dp)
                    .background(tint, CircleShape)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

/** Crescent: a disc minus a smaller disc shifted to the upper right. Hand-drawn because
 *  `material-icons-core` has no moon or bedtime glyph. */
@Composable
private fun MoonGlyph(tint: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val side = this.size.minDimension
        val disc = Path().apply { addOval(Rect(center, side * 0.42f)) }
        val bite = Path().apply { addOval(Rect(Offset(side * 0.72f, side * 0.3f), side * 0.34f)) }
        drawPath(Path.combine(PathOperation.Difference, disc, bite), tint)
    }
}
