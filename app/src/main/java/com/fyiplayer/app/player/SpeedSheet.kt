package com.fyiplayer.app.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/** Playback-speed picker, same RadioButton-in-a-sheet shape as [QualitySheet]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(current: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(modifier = Modifier.padding(bottom = 16.dp)) {
            items(SPEEDS) { speed ->
                ListItem(
                    headlineContent = { Text("${trimSpeed(speed)}x") },
                    leadingContent = { RadioButton(selected = speed == current, onClick = { onSelect(speed) }) },
                    modifier = Modifier.clickable { onSelect(speed) },
                )
            }
        }
    }
}

/** "1" not "1.0", "1.25" not "1.25000". */
internal fun trimSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()
