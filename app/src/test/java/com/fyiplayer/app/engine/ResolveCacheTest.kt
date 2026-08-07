package com.fyiplayer.app.engine

import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.Resolved
import com.fyiplayer.app.core.StreamResolver
import com.fyiplayer.app.core.VideoRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Fake tier: no engine subprocess, no network. Counts calls so cache hits/misses are observable.
private class CountingResolver(private val outcome: () -> Resolved) : StreamResolver {
    var calls = 0
    override suspend fun resolve(ref: VideoRef): Resolved {
        calls++
        return outcome()
    }
}

/**
 * [isCacheFresh] is the pure fresh-vs-stale decision [ChainResolver.resolve] leans on -- covered
 * directly here with plain longs, no coroutines/network. The rest of this file exercises the
 * cache as wired into [ChainResolver] (hit/miss/TTL/invalidate); LRU eviction itself is not
 * hand-rolled logic -- it's LinkedHashMap(accessOrder=true) + removeEldestEntry, the same stdlib
 * mechanism ui/RefCache.kt already uses untested -- so it gets no bespoke test here.
 */
class ResolveCacheTest {

    @Test
    fun freshWhenAgeIsUnderTtl() {
        assertTrue(isCacheFresh(insertedAtMillis = 1_000L, nowMillis = 1_000L + 59, ttlMillis = 60))
    }

    @Test
    fun staleAtOrPastTtl() {
        assertFalse(isCacheFresh(insertedAtMillis = 1_000L, nowMillis = 1_000L + 60, ttlMillis = 60))
        assertFalse(isCacheFresh(insertedAtMillis = 1_000L, nowMillis = 1_000L + 1_000, ttlMillis = 60))
    }

    @Test
    fun clockThatMovedBackwardsIsStillTreatedAsFresh() {
        // now < insertedAt (e.g. device clock adjusted): negative age is still < ttl, so served
        // from cache rather than treated as instantly stale.
        assertTrue(isCacheFresh(insertedAtMillis = 2_000L, nowMillis = 1_000L, ttlMillis = 60))
    }

    private val ref = VideoRef(
        sourceId = "youtube",
        pageUrl = "https://www.youtube.com/watch?v=abc12345678",
        remoteId = "abc12345678",
        title = "Sample",
    )
    private fun resolved() = Resolved(ref = ref, formats = emptyList(), resolvedAtMillis = 0L)

    @Test
    fun secondResolveIsServedFromCache_tier1CalledOnce() = runBlocking {
        val tier1 = CountingResolver { resolved() }
        val chain = ChainResolver(tier1, CountingResolver { resolved() })

        chain.resolve(ref)
        val second = chain.resolve(ref)

        assertEquals(1, tier1.calls)
        assertEquals(resolved(), second)
    }

    @Test
    fun failureIsNeverCached_nextResolveGoesLiveAgain() = runBlocking {
        var attempt = 0
        val tier1 = object : StreamResolver {
            override suspend fun resolve(ref: VideoRef): Resolved {
                attempt++
                if (attempt == 1) throw ExtractionError.ContentUnavailable("gone")
                return resolved()
            }
        }
        // tier1's ContentUnavailable falls through to tier2 (unchanged ChainResolver behaviour);
        // tier2 also fails, so the first resolve() throws and nothing gets cached.
        val tier2 = object : StreamResolver {
            override suspend fun resolve(ref: VideoRef): Resolved =
                throw ExtractionError.Unsupported("no tier2")
        }
        val chain = ChainResolver(tier1, tier2)

        try {
            chain.resolve(ref)
            assertTrue("expected first resolve to fail", false)
        } catch (e: ExtractionError.Unsupported) {
            // expected
        }
        val result = chain.resolve(ref)

        assertEquals(2, attempt)
        assertEquals(resolved(), result)
    }

    @Test
    fun invalidateDropsCachedEntry_nextResolveGoesLive() = runBlocking {
        val tier1 = CountingResolver { resolved() }
        val chain = ChainResolver(tier1, CountingResolver { resolved() })

        chain.resolve(ref)
        chain.invalidate(ref.pageUrl)
        chain.resolve(ref)

        assertEquals(2, tier1.calls)
    }

    @Test
    fun invalidateOfUnrelatedUrlLeavesCacheIntact() = runBlocking {
        val tier1 = CountingResolver { resolved() }
        val chain = ChainResolver(tier1, CountingResolver { resolved() })

        chain.resolve(ref)
        chain.invalidate("https://www.youtube.com/watch?v=someOtherId")
        chain.resolve(ref)

        assertEquals(1, tier1.calls)
    }
}
