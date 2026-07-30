package com.fyiplayer.app.ui

import androidx.compose.runtime.Composable

/**
 * One playlist, multi-select, play all (DESIGN.md §5). [id] is the decoded playlist id from the
 * `playlist/{id}` route. [onOpenDetail] is a TODO seam for the library phase.
 */
@Composable
fun PlaylistDetailScreen(id: String, onOpenDetail: (String) -> Unit) {
    EmptyStateScreen(
        title = "Playlist",
        message = if (id.isBlank()) "No playlist id." else "Nothing in playlist $id yet.",
    )
}
