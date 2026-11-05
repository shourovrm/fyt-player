package com.fyiplayer.app.ui

/** How Home's feed is laid out. Stored by name in Prefs as the default; the app-bar icon cycles in declaration order. */
enum class HomeLayout {
    /** Single column, full-width thumbnail with title and meta under it. */
    FULL_CARD,
    /** Single column, small thumbnail beside title and meta (the ResultRow list). */
    COMPACT,
    /** Two columns (four in landscape) of VideoGridCards. */
    GRID;

    fun next(): HomeLayout = entries[(ordinal + 1) % entries.size]

    companion object {
        /** An unknown or missing name reads as FULL_CARD, the default. */
        fun fromName(name: String?): HomeLayout = entries.firstOrNull { it.name == name } ?: FULL_CARD
    }
}
