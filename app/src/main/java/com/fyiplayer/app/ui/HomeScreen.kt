package com.fyiplayer.app.ui

import androidx.compose.runtime.Composable

/**
 * Per-source tabs, search field, infinite list (DESIGN.md §5). [onOpenDetail]/[onOpenListing]
 * are TODO seams for the source-browsing phase — take a canonical page URL / listing key.
 */
@Composable
fun HomeScreen(onOpenDetail: (String) -> Unit, onOpenListing: (String) -> Unit) {
    EmptyStateScreen(
        title = "Home",
        message = "Nothing to browse yet — sources connect in a later phase.",
    )
}
