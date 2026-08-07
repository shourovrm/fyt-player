package com.fyiplayer.app.engine

import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.Resolved
import com.fyiplayer.app.core.StreamResolver
import com.fyiplayer.app.core.VideoRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Fake tiers: no engine subprocess, no NewPipeExtractor, no network.
private class FakeResolver(private val outcome: () -> Resolved) : StreamResolver {
    var calls = 0
    override suspend fun resolve(ref: VideoRef): Resolved {
        calls++
        return outcome()
    }
}

private class FailingResolver(private val error: () -> ExtractionError) : StreamResolver {
    var calls = 0
    override suspend fun resolve(ref: VideoRef): Resolved {
        calls++
        throw error()
    }
}

private class FakeTier0(
    private val ownsUrl: Boolean,
    private val outcome: () -> Resolved,
) : UrlScopedResolver {
    var calls = 0
    override fun handles(url: String) = ownsUrl
    override suspend fun resolve(ref: VideoRef): Resolved {
        calls++
        return outcome()
    }
}

private class FailingTier0(
    private val ownsUrl: Boolean,
    private val error: () -> ExtractionError,
) : UrlScopedResolver {
    var calls = 0
    override fun handles(url: String) = ownsUrl
    override suspend fun resolve(ref: VideoRef): Resolved {
        calls++
        throw error()
    }
}

class ChainResolverTest {
    private val ref = VideoRef(
        sourceId = "youtube",
        pageUrl = "https://www.youtube.com/watch?v=abc12345678",
        remoteId = "abc12345678",
        title = "Sample",
    )
    private fun resolved() = Resolved(ref = ref, formats = emptyList(), resolvedAtMillis = 0L)

    @Test
    fun tier0HandlesAndSucceeds_tier1NeverCalled() = runBlocking {
        val tier0 = FakeTier0(ownsUrl = true) { resolved() }
        val tier1 = FakeResolver { resolved() }
        val chain = ChainResolver(tier1, FakeResolver { resolved() }, tier0)

        chain.resolve(ref)

        assertEquals(1, tier0.calls)
        assertEquals(0, tier1.calls)
    }

    @Test
    fun tier0DoesNotHandleUrl_skipsStraightToTier1() = runBlocking {
        val tier0 = FakeTier0(ownsUrl = false) { resolved() }
        val tier1 = FakeResolver { resolved() }
        val chain = ChainResolver(tier1, FakeResolver { resolved() }, tier0)

        chain.resolve(ref)

        assertEquals(0, tier0.calls)
        assertEquals(1, tier1.calls)
    }

    @Test
    fun tier0UnsupportedFallsThroughToTier1() = runBlocking {
        val tier0 = FailingTier0(ownsUrl = true) { ExtractionError.Unsupported("nope") }
        val tier1 = FakeResolver { resolved() }
        val chain = ChainResolver(tier1, FakeResolver { resolved() }, tier0)

        val result = chain.resolve(ref)

        assertEquals(1, tier0.calls)
        assertEquals(1, tier1.calls)
        assertEquals(resolved(), result)
    }

    @Test
    fun tier0NetworkFailureFallsThroughToTier1() = runBlocking {
        val tier0 = FailingTier0(ownsUrl = true) { ExtractionError.Network("down") }
        val tier1 = FakeResolver { resolved() }
        val chain = ChainResolver(tier1, FakeResolver { resolved() }, tier0)

        chain.resolve(ref)

        assertEquals(1, tier1.calls)
    }

    @Test
    fun tier0AccessChallengeIsHardStop_tier1NeverCalled() = runBlocking {
        val tier0 = FailingTier0(ownsUrl = true) { ExtractionError.AccessChallenge("wall") }
        val tier1 = FakeResolver { resolved() }
        val chain = ChainResolver(tier1, FakeResolver { resolved() }, tier0)

        try {
            chain.resolve(ref)
            assertTrue("expected AccessChallenge to propagate", false)
        } catch (e: ExtractionError.AccessChallenge) {
            // expected
        }
        assertEquals(0, tier1.calls)
    }

    @Test
    fun tier0AccessChallengeSignedIn_cookiedTier1Wins() = runBlocking {
        val tier0 = FailingTier0(ownsUrl = true) { ExtractionError.AccessChallenge("wall") }
        val tier1 = FakeResolver { resolved() }
        val tier2 = FakeResolver { resolved() }
        val chain = ChainResolver(tier1, tier2, tier0, ownSessionOnChallenge = { true })

        chain.resolve(ref)

        assertEquals(1, tier1.calls)
        assertEquals(0, tier2.calls)
    }

    @Test
    fun tier0AccessChallengeSignedIn_tier1FailureFallsToTier2() = runBlocking {
        val tier0 = FailingTier0(ownsUrl = true) { ExtractionError.AccessChallenge("wall") }
        val tier1 = FakeResolver { throw ExtractionError.AccessChallenge("still walled") }
        val tier2 = FakeResolver { resolved() }
        val chain = ChainResolver(tier1, tier2, tier0, ownSessionOnChallenge = { true })

        chain.resolve(ref)

        assertEquals(1, tier1.calls)
        assertEquals(1, tier2.calls)
    }

    @Test
    fun tier0AccessChallengeSignedIn_allFailuresRethrowOriginalWall() = runBlocking {
        val tier0 = FailingTier0(ownsUrl = true) { ExtractionError.AccessChallenge("wall") }
        val tier1 = FakeResolver { throw ExtractionError.Unsupported("engine gone") }
        val tier2 = FakeResolver { throw ExtractionError.Unsupported("no media request observed") }
        val chain = ChainResolver(tier1, tier2, tier0, ownSessionOnChallenge = { true })

        try {
            chain.resolve(ref)
            assertTrue("expected AccessChallenge to propagate", false)
        } catch (e: ExtractionError.AccessChallenge) {
            assertEquals("wall", e.message) // the original wall, not a downstream failure
        }
        assertEquals(1, tier2.calls)
    }

    @Test
    fun tier0ContentUnavailableIsHardStop_tier1NeverCalled() = runBlocking {
        val tier0 = FailingTier0(ownsUrl = true) { ExtractionError.ContentUnavailable("gone") }
        val tier1 = FakeResolver { resolved() }
        val chain = ChainResolver(tier1, FakeResolver { resolved() }, tier0)

        try {
            chain.resolve(ref)
            assertTrue("expected ContentUnavailable to propagate", false)
        } catch (e: ExtractionError.ContentUnavailable) {
            // expected
        }
        assertEquals(0, tier1.calls)
    }

    @Test
    fun noTier0_behavesLikeBeforeTier0Existed() = runBlocking {
        val tier1 = FailingResolver { ExtractionError.Unsupported("nope") }
        val tier2 = FakeResolver { resolved() }
        val chain = ChainResolver(tier1, tier2) // tier0 omitted -- default null

        chain.resolve(ref)

        assertEquals(1, tier1.calls)
        assertEquals(1, tier2.calls)
    }

    @Test
    fun tier1ContentUnavailableStillFallsThroughToTier2_unchangedBehaviour() = runBlocking {
        val tier1 = FailingResolver { ExtractionError.ContentUnavailable("gone") }
        val tier2 = FakeResolver { resolved() }
        val chain = ChainResolver(tier1, tier2)

        chain.resolve(ref)

        assertEquals(1, tier2.calls)
    }
}
