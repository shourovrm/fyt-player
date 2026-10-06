package com.fyiplayer.app.source.newpipe

import com.fyiplayer.app.core.SearchFilter
import com.fyiplayer.app.core.SearchSort
import com.fyiplayer.app.core.SearchType
import org.schabi.newpipe.extractor.search.filter.Filter
import org.schabi.newpipe.extractor.search.filter.FilterItem

/** The fork's filter items for one search: exactly what `searchQHFactory.fromQuery` takes. */
internal class SearchFilterItems(val content: List<FilterItem>, val sort: List<FilterItem>)

// Item names as registered by the fork's YoutubeFilters. Names, not numeric ids: the fork hands
// out ids from one counter shared by items and groups, so an id shifts whenever a group is added.
internal fun contentFilterName(type: SearchType): String = when (type) {
    SearchType.ALL -> "all"
    SearchType.VIDEOS -> "videos"
    SearchType.CHANNELS -> "channels"
    SearchType.PLAYLISTS -> "playlists"
}

internal fun sortFilterName(sort: SearchSort): String? = when (sort) {
    // Relevance is the request an unfiltered search already sends; no explicit item for it.
    SearchSort.RELEVANCE -> null
    SearchSort.RATING -> "sort_rating"
    SearchSort.VIEW_COUNT -> "sort_view"
}

internal fun findFilterItem(filter: Filter?, name: String): FilterItem? =
    filter?.filterGroups.orEmpty()
        .flatMap { group -> group.filterItems.orEmpty().asList() }
        .firstOrNull { it.name == name }

/**
 * Resolves [filter] against the fork's registries. [defaultContent] is the registered "all" item;
 * a name the fork no longer registers degrades to it (and to relevance order) rather than
 * throwing, because the fork REQUIRES a registered content item (an empty list throws).
 */
internal fun resolveSearchFilter(
    filter: SearchFilter,
    contentFilters: Filter?,
    sortFilters: Filter?,
    defaultContent: FilterItem,
): SearchFilterItems {
    val content = findFilterItem(contentFilters, contentFilterName(filter.type)) ?: defaultContent
    val sortName = sortFilterName(filter.sort)
    val sort = sortName?.let { findFilterItem(sortFilters, it) }
    return SearchFilterItems(listOf(content), listOfNotNull(sort))
}
