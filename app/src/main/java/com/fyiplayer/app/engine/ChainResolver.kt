package com.fyiplayer.app.engine

import android.util.Log
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.Resolved
import com.fyiplayer.app.core.StreamResolver
import com.fyiplayer.app.core.VideoRef

private const val TAG = "ChainResolver"

/**
 * What tier0 needs beyond [StreamResolver]: whether it owns a URL at all, so [ChainResolver] can
 * decide to try it before tier1 rather than probe-and-catch. Kept local to this file rather than
 * promoted to `core/Contracts.kt` -- nothing else in the chain needs it, only tier0.
 */
interface UrlScopedResolver : StreamResolver {
    fun handles(url: String): Boolean
}

/**
 * The full resolution chain: NewPipeExtractor (tier0, YouTube watch/shorts pages only) first when
 * supplied, then engine JSON (tier1), then hidden-WebView capture (tier2).
 *
 * tier0 falls through to tier1 on [ExtractionError.Unsupported] or [ExtractionError.Network] --
 * one attempt, no retry. [ExtractionError.AccessChallenge] and [ExtractionError.ContentUnavailable]
 * from tier0 are honest facts about the content, not a tier-specific failure, so they are NOT
 * retried on tier1/tier2.
 *
 * tier1 -> tier2 behaviour is unchanged from before tier0 existed: only
 * [ExtractionError.AccessChallenge] is a hard stop there (never retried on tier2, since a WebView
 * load would face the exact same wall); every other tier1 [ExtractionError] -- including
 * [ExtractionError.ContentUnavailable] -- falls through to tier2.
 */
class ChainResolver(
    private val tier1: StreamResolver,
    private val tier2: StreamResolver,
    private val tier0: UrlScopedResolver? = null,
) : StreamResolver {

    override suspend fun resolve(ref: VideoRef): Resolved {
        if (tier0 != null && tier0.handles(ref.pageUrl)) {
            try {
                return tier0.resolve(ref).also { logTier("tier0") }
            } catch (e: ExtractionError.AccessChallenge) {
                throw e
            } catch (e: ExtractionError.ContentUnavailable) {
                throw e
            } catch (e: ExtractionError) {
                logFallthrough("tier0", e, "trying tier1")
                // falls through to the tier1 -> tier2 chain below
            }
        }
        return try {
            tier1.resolve(ref).also { logTier("tier1") }
        } catch (e: ExtractionError.AccessChallenge) {
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
