package com.fyiplayer.app.ui

import androidx.compose.runtime.Composable

/** Queue with pause/resume/cancel (DESIGN.md §5). Real queue wires up in the downloads phase. */
@Composable
fun DownloadsScreen() {
    EmptyStateScreen(
        title = "Downloads",
        message = "The download queue is empty — the queue lands in a later phase.",
    )
}
