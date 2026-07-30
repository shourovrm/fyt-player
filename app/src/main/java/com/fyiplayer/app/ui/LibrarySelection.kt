package com.fyiplayer.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import com.fyiplayer.app.core.VideoRef

/**
 * Multi-select primitives shared by the Likes tab and playlist detail: pure Set<pageUrl> maths so
 * both screens toggle/select-all/order the same way (one definition, one unit test), plus the two
 * chrome pieces that swap in while a selection is active.
 */

/** Tap a row while selecting: in -> out, out -> in. Empty set == selection mode is off. */
internal fun Set<String>.toggled(pageUrl: String): Set<String> =
    if (pageUrl in this) this - pageUrl else this + pageUrl

/** "Select all" toggle: selects every displayed video, or clears if they're already all selected. */
internal fun selectAllOrNone(items: List<VideoRef>, selection: Set<String>): Set<String> {
    val all = items.mapTo(LinkedHashSet()) { it.pageUrl }
    return if (selection.containsAll(all) && all.isNotEmpty()) emptySet() else all
}

/** Selected videos in display order -- that is the play/enqueue order. */
internal fun selectedInOrder(items: List<VideoRef>, selection: Set<String>): List<VideoRef> =
    items.filter { it.pageUrl in selection }

/** Replaces the screen's own top bar while selecting: X · "N selected" · All · caller's actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Exit selection") } },
        actions = {
            TextButton(onClick = onSelectAll) { Text("All") }
            actions()
        },
    )
}

/** The primary selection action, bottom-right. */
@Composable
internal fun PlaySelectionFab(count: Int, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
        text = { Text("Play $count") },
    )
}
