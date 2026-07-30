package com.fyiplayer.app.ui

import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.core.VideoSource
import kotlinx.coroutines.CancellationException

/**
 * Pure paging for the Shorts pager, kept out of the composable file like [HomeFeed.kt]'s tab
 * logic. Each source keeps its own opaque `nextPage` token ([SearchPage.nextPage]'s contract) --
 * unlike a shared page-number index, this works for any source regardless of how it encodes a
 * page. Results are round-robin interleaved with [interleave] so a multi-source feed never runs
 * one platform in a streak.
 */

/** One source's paging position: its last token, or null once it has run out. */
internal data class ShortsFeedState(
    val tokens: Map<String, String?> = emptyMap(),
    val exhausted: Set<String> = emptySet(),
) {
    fun hasMore(sources: List<VideoSource>): Boolean = sources.any { it.id !in exhausted }
}

internal data class ShortsRound(val items: List<VideoRef>, val state: ShortsFeedState)

/** Fetches one page from every source that has not run out yet, merges them, and advances each
 *  source's own token. A source that throws is marked exhausted rather than retried forever --
 *  an endless pager has no per-source retry affordance. */
internal suspend fun loadShortsRound(sources: List<VideoSource>, state: ShortsFeedState): ShortsRound {
    val tokens = state.tokens.toMutableMap()
    val exhausted = state.exhausted.toMutableSet()
    val bySource = sources.map { src ->
        if (src.id in exhausted) return@map emptyList()
        try {
            val page = src.shorts(tokens[src.id])
            tokens[src.id] = page.nextPage
            if (page.nextPage == null) exhausted += src.id
            page.items
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            exhausted += src.id
            emptyList()
        }
    }
    return ShortsRound(interleave(bySource), ShortsFeedState(tokens, exhausted))
}
