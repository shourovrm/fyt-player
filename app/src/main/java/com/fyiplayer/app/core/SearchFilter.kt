package com.fyiplayer.app.core

/** What kind of result a search returns. Source-neutral: an adapter maps it to its own filter. */
enum class SearchType(val label: String) {
    ALL("All"),
    VIDEOS("Videos"),
    CHANNELS("Channels"),
    PLAYLISTS("Playlists"),
}

enum class SearchSort(val label: String) {
    RELEVANCE("Relevance"),
    RATING("Rating"),
    VIEW_COUNT("View count"),
}

/** The default is what an unfiltered search already was: every type, platform relevance order. */
data class SearchFilter(
    val type: SearchType = SearchType.ALL,
    val sort: SearchSort = SearchSort.RELEVANCE,
)
