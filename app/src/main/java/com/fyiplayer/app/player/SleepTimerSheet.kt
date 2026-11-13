package com.fyiplayer.app.player

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.theme.ControlShape
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import java.util.Date

/** The preset choices, in minutes. Anything else is a custom timer. */
private val SLEEP_TIMER_PRESETS = listOf(15, 30, 45, 60)

/**
 * Sleep timer picker, same shape as [CaptionSheet]: Off, the presets, then Custom, which asks for
 * hours and minutes on two wheels. A preset or Off closes the sheet. While a timer runs, a line under the
 * title counts down and the chosen row carries the check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(onDismiss: () -> Unit) {
    val running by SleepTimer.running.collectAsState()
    val pausesAtEndOfVideo by SleepTimer.pausesAtEndOfVideo.collectAsState()
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
            if (pausesAtEndOfVideo) {
                item {
                    Text(
                        stringResource(R.string.sleep_timer_pauses_at_end),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.pageMargin),
                    )
                }
            }
            item {
                ChoiceListItem(
                    headline = stringResource(R.string.sleep_timer_off),
                    selected = chosen == null && !pausesAtEndOfVideo,
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
                    headline = stringResource(R.string.sleep_timer_end_of_video),
                    selected = pausesAtEndOfVideo,
                    onSelect = { SleepTimer.pauseAtEndOfVideo(); onDismiss() },
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

/** Hours and minutes wheels; Start stays disabled at 0:00 and past 12 hours. */
@Composable
private fun CustomSleepTimerDialog(onStart: (Int) -> Unit, onDismiss: () -> Unit) {
    var hours by remember { mutableIntStateOf(1) }
    var minutes by remember { mutableIntStateOf(30) }
    val total = sleepTimerTotalMinutes(hours, minutes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_timer_custom_title)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WheelWithLabel(stringResource(R.string.sleep_timer_hours_label), Modifier.weight(1f)) {
                        NumberWheel(SLEEP_TIMER_HOURS, hours, onSelect = { hours = it }, format = Int::toString)
                    }
                    Text(stringResource(R.string.sleep_timer_separator), style = MaterialTheme.typography.titleLarge)
                    WheelWithLabel(stringResource(R.string.sleep_timer_minutes_label), Modifier.weight(1f)) {
                        NumberWheel(SLEEP_TIMER_MINUTES, minutes, onSelect = { minutes = it }, format = { "%02d".format(it) })
                    }
                }
                if (total != null) {
                    Text(
                        stringResource(R.string.sleep_timer_pauses_at, pauseClockTime(total)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.s16),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(shape = ControlShape, enabled = total != null, onClick = { total?.let(onStart) }) {
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

private val SLEEP_TIMER_HOURS = (0..12).toList()
private val SLEEP_TIMER_MINUTES = (0..55 step 5).toList()

@Composable
private fun WheelWithLabel(label: String, modifier: Modifier, wheel: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        wheel()
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The wall-clock time [minutes] from now, in the phone's 12- or 24-hour format. */
@Composable
private fun pauseClockTime(minutes: Int): String {
    val format = DateFormat.getTimeFormat(LocalContext.current)
    return format.format(Date(System.currentTimeMillis() + minutes * 60_000L))
}

/**
 * The sleep timer button's face: a crescent moon, plus a badge with the whole minutes left while
 * a timer runs, or a plain dot while it waits for the video to end. Shared by the player's top corner and the Shorts rail.
 */
@Composable
fun SleepTimerGlyph(tint: Color, size: Dp = Sizes.icon) {
    val running by SleepTimer.running.collectAsState()
    val pausesAtEndOfVideo by SleepTimer.pausesAtEndOfVideo.collectAsState()
    Box(Modifier.size(size)) {
        MoonGlyph(tint, size)
        if (pausesAtEndOfVideo) {
            Box(Modifier.align(Alignment.TopEnd).size(Spacing.s8).background(tint, CircleShape))
        }
        running?.let { timer ->
            Text(
                sleepTimerMinutesLeft(timer.remainingMs).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.scrim,
                maxLines = 1,
                softWrap = false,
                // Wider than the glyph from 100 minutes up; it grows left instead of wrapping.
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .wrapContentWidth(Alignment.End, unbounded = true)
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
