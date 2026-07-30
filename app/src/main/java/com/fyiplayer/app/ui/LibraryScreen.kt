package com.fyiplayer.app.ui

import androidx.compose.runtime.Composable

/**
 * Likes tab + Playlists tab, multi-select (DESIGN.md §5). [onOpenDetail]/[onOpenPlaylist] are
 * TODO seams for the library phase.
 */
@Composable
fun LibraryScreen(onOpenDetail: (String) -> Unit, onOpenPlaylist: (String) -> Unit) {
    EmptyStateScreen(
        title = "Library",
        message = "No likes or playlists yet — they land with the library phase.",
    )
}
