package com.fyiplayer.app.engine

import android.util.Log
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.Resolved
import com.fyiplayer.app.core.StreamResolver
import com.fyiplayer.app.core.VideoRef

private const val TAG = "ChainResolver"
private const val CACHE_MAX_ENTRIES = 60
private const val CACHE_TTL_MILLIS = 60L * 60 * 1000 // 60 min

/**
 * Fresh-vs-stale, pulled out of [ChainResolver] so it's a plain JUnit test: no coroutines, no
 * clock mock, just three longs in, one bool out.
 */
internal fun isCacheFresh(insertedAtMillis: Long, nowMillis: Long, ttlMillis: Long = CACHE_TTL_MILLIS): Boolean =
    nowMillis - insertedAtMillis < ttlMillis

private data class CacheEntry(val resolved: Resolved, val insertedAtMillis: Long)

/**
 * What tier0 needs beyond [StreamResolver]: whether it owns a URL at all, so [ChainResolver] can
 * decide to try it before tier1 rather than probe-and-catch. Kept local to this file rather than
 * promoted to `core/Contracts.kt` -- nothing else in the chain needs it, only tier0.
 */
interface UrlScopedResolver : StreamResolver {
    fun handles(url: String): Boolean
}

/**
 * The full resolution chain: PipePipeExtractor (tier0) OWNS every URL it handles (YouTube
 * watch/shorts) -- any tier0 failure is final for that URL. yt-dlp (tier1) and the hidden
 * WebView (tier2) serve only URLs tier0 does not handle. The old tier0->tier1 fallback was
 * removed deliberately: yt-dlp is anonymous and 3-15s slower, its formats hit the same walls,
 * and the signed-in-AccessChallenge retry was confirmed insufficient (the engine ignores a bare
 * cookie header) -- the fork's own token login in tier0 is the real signed-in path.
 *
 * tier1 -> tier2: only [ExtractionError.AccessChallenge] is a hard stop there (a WebView load
 * would face the exact same wall); every other tier1 [ExtractionError] -- including
 * [ExtractionError.ContentUnavailable] -- falls through to tier2.
 */
class ChainResolver(
    private val tier1: StreamResolver,
    private val tier2: StreamResolver,
    private val tier0: UrlScopedResolver? = null,
) : StreamResolver {

    // Cache of successful resolves only, keyed by the canonical page URL -- signed formats/captions
    // stay in memory here, same rule as core/Contracts.kt. LinkedHashMap(accessOrder=true) +
    // removeEldestEntry gives LRU eviction for free (same pattern as ui/RefCache.kt); @Synchronized
    // methods make it safe under concurrent resolve() calls (player prefetch + downloads).
    private val cache = object : LinkedHashMap<String, CacheEntry>(CACHE_MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>) =
            size > CACHE_MAX_ENTRIES
    }

    @Synchronized
    private fun cacheGet(pageUrl: String): CacheEntry? = cache[pageUrl]

    @Synchronized
    private fun cachePut(pageUrl: String, entry: CacheEntry) {
        cache[pageUrl] = entry
    }

    // Load-bearing: the player calls this when a served URL turns out expired (403) and re-resolves
    // right after. Without dropping the entry here, the next resolve() would just hand back the same
    // dead URL and playback would hard-fail instead of recovering.
    @Synchronized
    override fun invalidate(pageUrl: String) {
        cache.remove(pageUrl)
    }

    override suspend fun resolve(ref: VideoRef): Resolved {
        cacheGet(ref.pageUrl)?.let { entry ->
            if (isCacheFresh(entry.insertedAtMillis, System.currentTimeMillis())) return entry.resolved
        }
        return resolveLive(ref).also { cachePut(ref.pageUrl, CacheEntry(it, System.currentTimeMillis())) }
    }

    // Unchanged tier0 -> tier1 -> tier2 chain, just renamed so resolve() can wrap it with the cache.
    // Only a successful return reaches here -- failures throw and are never cached.
    private suspend fun resolveLive(ref: VideoRef): Resolved {
        if (tier0 != null && tier0.handles(ref.pageUrl)) {
            try {
                return tier0.resolve(ref).also { logTier("tier0") }
            } catch (e: ExtractionError) {
                // tier0 owns its URLs outright: yt-dlp is anonymous AND 3-15s slower, and its
                // formats hit the same walls -- falling through only ever traded a fast honest
                // error for a slow duplicate one. yt-dlp keeps non-YouTube URLs (below) and the
                // channel Courses listing delegate; it is no longer a YouTube resolve fallback.
                logHardStop("tier0", e)
                throw e
            }
        }
        return try {
            tier1.resolve(ref).also { logTier("tier1") }
        } catch (e: ExtractionError.AccessChallenge) {
            logHardStop("tier1", e)
            throw e
        } catch (e: ExtractionError) {
            logFallthrough("tier1", e, "trying webview")
            tier2.resolve(ref).also { logTier("tier2") }
        }
    }
}

// Tag and tier name only -- never the message, which can echo the page URL. Swallow logging
// failures: android.util.Log is unmocked under plain JUnit (no Robolectric here).
private fun logTier(tier: String) {
    try {
        Log.d(TAG, "resolved by $tier")
    } catch (logError: Throwable) {
        // no-op
    }
}

private fun logFallthrough(tier: String, e: ExtractionError, next: String) {
    try {
        Log.d(TAG, "$tier failed (${e::class.simpleName}), $next")
    } catch (logError: Throwable) {
        // no-op
    }
}

// Hard stops rethrow silently otherwise -- invisible in logcat, which makes them brutal to debug.
private fun logHardStop(tier: String, e: ExtractionError) {
    try {
        Log.d(TAG, "$tier hard stop (${e::class.simpleName})")
    } catch (logError: Throwable) {
        // no-op
    }
}
