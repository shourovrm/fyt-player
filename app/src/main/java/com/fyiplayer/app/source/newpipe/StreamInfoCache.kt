package com.fyiplayer.app.source.newpipe

import com.fyiplayer.app.engine.isCacheFresh
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

private const val MAX_ENTRIES = 60

private class Entry(val deferred: CompletableDeferred<StreamInfo>, val insertedAtMillis: Long)

/**
 * One StreamInfo fetch per video url (~5 HTTP calls each), shared by NewPipeYoutubeSource.detail,
 * NewPipeResolver.resolve and NewPipeYoutubeSource.seekThumbnails -- opening a video used to run
 * all three concurrently, tripling network cost. Memory only: [StreamInfo] carries signed stream
 * URLs, never persisted/logged per project rule. TTL matches engine/ChainResolver's resolve cache.
 */
internal object StreamInfoCache {
    // LinkedHashMap(accessOrder=true) + removeEldestEntry: LRU for free, same pattern as
    // ChainResolver's resolve cache. @Synchronized guards it under concurrent detail/resolve/seek calls.
    // Fetches run on their own scope, not the first caller's: Detail's effect gets cancelled on
    // every navigation, and a cancelled fetch would fail the playback resolve awaiting it too.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val map = object : LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = size > MAX_ENTRIES
    }

    suspend fun get(url: String, force: Boolean = false): StreamInfo {
        val (deferred, shouldFetch) = claim(url, force)
        // Only the caller that inserted a NEW deferred fetches; concurrent/cached callers just
        // await it below -- that's the in-flight dedup.
        if (shouldFetch) scope.launch { fetch(url, deferred) }
        return deferred.await()
    }

    @Synchronized
    fun invalidate(url: String) {
        map.remove(url)
    }

    @Synchronized
    private fun claim(url: String, force: Boolean): Pair<CompletableDeferred<StreamInfo>, Boolean> {
        val existing = map[url]
        if (!force && existing != null && isCacheFresh(existing.insertedAtMillis, System.currentTimeMillis())) {
            return existing.deferred to false
        }
        val fresh = CompletableDeferred<StreamInfo>()
        map[url] = Entry(fresh, System.currentTimeMillis())
        return fresh to true
    }

    private fun fetch(url: String, deferred: CompletableDeferred<StreamInfo>) {
        try {
            deferred.complete(StreamInfo.getInfo(ServiceList.YouTube, url))
        } catch (e: Throwable) {
            // A failed fetch must not poison the cache -- drop it so the next call retries, but
            // only if nobody has already replaced this entry (e.g. a force=true refresh raced in).
            dropIfCurrent(url, deferred)
            deferred.completeExceptionally(e)
        }
    }

    @Synchronized
    private fun dropIfCurrent(url: String, deferred: CompletableDeferred<StreamInfo>) {
        if (map[url]?.deferred === deferred) map.remove(url)
    }
}
