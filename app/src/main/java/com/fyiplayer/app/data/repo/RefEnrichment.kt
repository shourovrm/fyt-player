package com.fyiplayer.app.data.repo

import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.core.VideoRef

/** Like/playlist-add is user-initiated, so one detail() fetch here is acceptable when the caller
 *  only had a bare ref (empty title -- share/URL-only open, a feed pager that skipped enrichment).
 *  Free when Detail already warmed StreamInfoCache. Never blocks the write: any failure (offline,
 *  unsupported URL, access wall) just persists the bare ref. */
internal suspend fun VideoRef.withTitleIfBlank(): VideoRef {
    if (title.isNotBlank()) return this
    return runCatching { SourceRegistry.forUrl(pageUrl)?.detail(this)?.ref }.getOrNull() ?: this
}
