package com.fyiplayer.app.ui

import androidx.compose.runtime.Composable

/**
 * Pinned player header + metadata, storyboard, related (DESIGN.md §5). [pageUrl] is the decoded
 * canonical page URL from the `detail/{pageUrl}` route. [onOpenDetail] is a TODO seam for
 * related-video navigation once a real source/player exist.
 */
@Composable
fun DetailScreen(pageUrl: String, onOpenDetail: (String) -> Unit) {
    EmptyStateScreen(
        title = "Detail",
        message = if (pageUrl.isBlank()) "No page URL." else "Nothing resolved yet for:\n$pageUrl",
    )
}
