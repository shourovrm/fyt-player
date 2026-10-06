package com.fyiplayer.app.source.newpipe

import com.fyiplayer.app.core.SearchFilter
import com.fyiplayer.app.core.SearchSort
import com.fyiplayer.app.core.SearchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.schabi.newpipe.extractor.search.filter.Filter
import org.schabi.newpipe.extractor.search.filter.FilterGroup
import org.schabi.newpipe.extractor.search.filter.FilterItem
import org.schabi.newpipe.extractor.stream.StreamSegment

private fun item(id: Int, name: String) = FilterItem(id, name)

private fun filterOf(vararg groups: List<FilterItem>): Filter =
    Filter.Builder(groups.mapIndexed { index, items -> FilterGroup(index, "group$index", true, items.toTypedArray()) }.toTypedArray()).build()

// Pure lookups only: no NewPipe.init, no network.
class SearchFilterItemsTest {

    private val all = item(0, "all")
    private val videos = item(1, "videos")
    private val channels = item(2, "channels")
    private val playlists = item(3, "playlists")
    private val contentFilters = filterOf(listOf(all, videos, channels, playlists), listOf(item(9, "music_songs")))

    private val rating = item(10, "sort_rating")
    private val views = item(11, "sort_view")
    private val sortFilters = filterOf(listOf(item(12, "sort_relevance"), rating, views), listOf(item(13, "all")))

    @Test fun `content names match the fork's registered names`() {
        assertEquals("all", contentFilterName(SearchType.ALL))
        assertEquals("videos", contentFilterName(SearchType.VIDEOS))
        assertEquals("channels", contentFilterName(SearchType.CHANNELS))
        assertEquals("playlists", contentFilterName(SearchType.PLAYLISTS))
    }

    @Test fun `relevance sends no sort item`() {
        assertNull(sortFilterName(SearchSort.RELEVANCE))
        val resolved = resolveSearchFilter(SearchFilter(), contentFilters, sortFilters, defaultContent = all)
        assertEquals(emptyList<FilterItem>(), resolved.sort)
    }

    @Test fun `type and sort resolve to the registered instances`() {
        val resolved = resolveSearchFilter(
            SearchFilter(SearchType.CHANNELS, SearchSort.VIEW_COUNT), contentFilters, sortFilters, defaultContent = all,
        )
        assertSame(channels, resolved.content.single())
        assertSame(views, resolved.sort.single())
    }

    @Test fun `an unregistered name degrades to the default content item`() {
        val resolved = resolveSearchFilter(
            SearchFilter(SearchType.PLAYLISTS, SearchSort.RATING), filterOf(listOf(all)), filterOf(listOf(rating)), defaultContent = all,
        )
        assertSame(all, resolved.content.single())
        assertSame(rating, resolved.sort.single())
    }

    @Test fun `a missing filter registry degrades instead of throwing`() {
        val resolved = resolveSearchFilter(SearchFilter(SearchType.VIDEOS, SearchSort.RATING), null, null, defaultContent = all)
        assertSame(all, resolved.content.single())
        assertEquals(emptyList<FilterItem>(), resolved.sort)
    }

    @Test fun `chapters are dropped when untitled, sorted by start and trimmed`() {
        val segments = listOf(
            StreamSegment("  Outro ", 300),
            StreamSegment("Intro", 0),
            StreamSegment("   ", 100),
            StreamSegment("Broken", -1),
        )
        val chapters = toChapters(segments)
        assertEquals(listOf("Intro", "Outro"), chapters.map { it.title })
        assertEquals(listOf(0, 300), chapters.map { it.startSeconds })
    }

    @Test fun `no segments gives no chapters`() {
        assertEquals(emptyList<Any>(), toChapters(null))
        assertEquals(emptyList<Any>(), toChapters(emptyList()))
    }
}
