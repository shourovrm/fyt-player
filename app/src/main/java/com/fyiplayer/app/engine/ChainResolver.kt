package com.fyiplayer.app.engine

import android.util.Log
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.Resolved
import com.fyiplayer.app.core.StreamResolver
import com.fyiplayer.app.core.VideoRef

private const val TAG = "ChainResolver"

/**
 * The full resolution chain: engine JSON (tier1) then hidden-WebView capture (tier2). An
 * [ExtractionError.AccessChallenge] from tier1 is a hard stop -- never retried on tier2, since a
 * WebView load would face the exact same wall. Any other tier1 [ExtractionError] falls through.
 */
class ChainResolver(
    private val tier1: StreamResolver,
    private val tier2: StreamResolver,
) : StreamResolver {

    override suspend fun resolve(ref: VideoRef): Resolved = try {
        tier1.resolve(ref)
    } catch (e: ExtractionError.AccessChallenge) {
        throw e
    } catch (e: ExtractionError) {
        // Error class name only -- never the message, which can echo the page URL. Swallow logging
        // failures: android.util.Log is unmocked under plain JUnit (no Robolectric here).
        try {
            Log.d(TAG, "tier1 failed (${e::class.simpleName}), trying webview")
        } catch (logError: Throwable) {
            // no-op
        }
        tier2.resolve(ref)
    }
}
