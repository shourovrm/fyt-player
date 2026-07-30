package com.fyiplayer.app.ui

import androidx.compose.runtime.Composable

/** Vertical full-screen pager (DESIGN.md §5). [onOpenDetail] is a TODO seam for the shorts phase. */
@Composable
fun ShortsScreen(onOpenDetail: (String) -> Unit) {
    EmptyStateScreen(
        title = "Shorts",
        message = "No short-form clips yet — the vertical pager lands in a later phase.",
    )
}
