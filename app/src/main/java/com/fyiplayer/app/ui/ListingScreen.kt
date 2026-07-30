package com.fyiplayer.app.ui

import androidx.compose.runtime.Composable

/**
 * A channel/tag/uploader listing (DESIGN.md §5). [key] is the decoded listing key from the
 * `listing/{key}` route. [onOpenDetail] is a TODO seam for the source-browsing phase.
 */
@Composable
fun ListingScreen(key: String, onOpenDetail: (String) -> Unit) {
    EmptyStateScreen(
        title = "Listing",
        message = if (key.isBlank()) "No listing key." else "Nothing browsable yet for:\n$key",
    )
}
