package com.fyiplayer.app.ui

import androidx.compose.runtime.Composable

/** Sections (DESIGN.md §5): sources, resolution, container, gestures, history, backup. */
@Composable
fun SettingsScreen() {
    EmptyStateScreen(
        title = "Settings",
        message = "Nothing to configure yet — settings sections land in a later phase.",
    )
}
