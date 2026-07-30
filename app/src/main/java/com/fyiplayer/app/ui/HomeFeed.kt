package com.fyiplayer.app.ui

import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.SearchPage
import com.fyiplayer.app.core.VideoRef

/**
 * Pure (no Compose, no Android) state and merge logic behind the Home tab row. Kept out of the
 * composable file so pagination bookkeeping and the round-robin merge are plain JVM-testable.
 */

const val ALL_TAB_ID = "all"

/** Per-tab (one source, or the merged "All" tab) pagination/loading/error state. */
internal data class TabResult(
    val displayName: String,
    val items: List<VideoRef> = emptyList(),
    val nextPage: String? = null,
    /** The page token that failed, so Retry re-requests exactly that page (null = the first page). */
    val retryPage: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** True once an [ExtractionError.AccessChallenge] stopped this tab -- an honest wall, never a
     *  retry prompt (project rule: no access-control bypass, no retry storms). */
    val blocked: Boolean = false,
    /** Source has no feed at all for this ([ExtractionError.Unsupported]) -- permanent, not retryable. */
    val unsupported: Boolean = false,
    /** True once the first request (success, error, blocked, or unsupported) has completed. */
    val loaded: Boolean = false,
) {
    val canContinue: Boolean get() = loading || nextPage != null
    val exhausted: Boolean get() = loaded && !loading && error == null && !blocked && nextPage == null
}

/** Which tab index is valid to keep selected when the enabled-source list changes. `size <= 1`
 *  means only the implicit single tab exists (nothing to switch between) -- never a reason to
 *  reset a selection that will become valid again once sources finish loading. */
internal fun resolveSelectedTab(current: String, tabIds: List<String>): String =
    if (tabIds.size <= 1 || current in tabIds) current else ALL_TAB_ID

/**
 * Round-robin merge across sources' own accumulated item lists, preserving each source's internal
 * order. An empty or failing source simply never gets a slot -- it does not break the alternation
 * between sources that do have items.
 */
internal fun interleave(bySource: List<List<VideoRef>>): List<VideoRef> {
    val seen = HashSet<String>()
    val merged = ArrayList<VideoRef>()
    var round = 0
    var any = true
    while (any) {
        any = false
        for (list in bySource) {
            if (round < list.size) {
                any = true
                val ref = list[round]
                if (seen.add(ref.pageUrl)) merged += ref
            }
        }
        round++
    }
    return merged
}

/** A page fetch succeeded: page==null replaces (fresh load), otherwise appends, deduped by page URL. */
internal fun applySuccess(current: TabResult, requestedPage: String?, result: SearchPage): TabResult {
    val base = if (requestedPage == null) emptyList() else current.items
    val seen = base.mapTo(HashSet()) { it.pageUrl }
    val newItems = result.items.filter { seen.add(it.pageUrl) }
    return current.copy(
        items = base + newItems,
        nextPage = result.nextPage,
        retryPage = null,
        loading = false,
        error = null,
        blocked = false,
        unsupported = false,
        loaded = true,
    )
}

/** A page fetch failed. [ExtractionError.AccessChallenge] is a hard stop (blocked, no retry
 *  affordance); everything else keeps the failing page token so Retry re-requests exactly it. */
internal fun applyError(current: TabResult, requestedPage: String?, error: ExtractionError): TabResult = when (error) {
    is ExtractionError.AccessChallenge -> current.copy(
        loading = false, error = error.userMessage(), blocked = true, nextPage = null, loaded = true,
    )
    else -> current.copy(
        loading = false, error = error.userMessage(), retryPage = requestedPage, nextPage = null, loaded = true,
    )
}

/** The source has no feed for this at all -- permanent, not retryable. */
internal fun applyUnsupported(current: TabResult): TabResult =
    current.copy(loading = false, error = null, unsupported = true, nextPage = null, loaded = true)
