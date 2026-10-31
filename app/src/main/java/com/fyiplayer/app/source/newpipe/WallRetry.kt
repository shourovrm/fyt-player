package com.fyiplayer.app.source.newpipe

import com.fyiplayer.app.DiagLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.stream.StreamInfo

private const val TAG = "WallRetry"

// A wall on one request is often momentary (shared carrier IPs), so wait before asking again.
private const val WALL_RETRY_DELAY_MS = 1_000L

// resolve() and detail() hit the same wall at the same moment; the lock makes the second caller
// find the first one's recovered entry in StreamInfoCache instead of repeating both requests.
private val wallRetryLock = Mutex()

/**
 * Second chance after a wall another client may clear (see [AccessChallengeReason.otherClientMayHelp]):
 * visionos once more after a pause, then mweb with a PO token -- TubePipe's order. Returns null
 * when both fail; the caller rethrows the first wall, since that is the honest reason. A failed
 * fetch is never cached, so the plain get() below is a fresh visionos request unless a concurrent
 * caller already recovered this page.
 */
internal suspend fun fetchAfterWall(pageUrl: String): StreamInfo? {
    wallRetryLock.withLock {
        delay(WALL_RETRY_DELAY_MS)
        val startedAt = System.currentTimeMillis()
        try {
            return StreamInfoCache.get(pageUrl).also { DiagLog.log(TAG, "visionos retry ok") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagLog.log(TAG, "visionos retry failed in ${System.currentTimeMillis() - startedAt}ms: ${e::class.simpleName}")
        }
        try {
            // force=true: replace any entry so detail() and seekThumbnails() see the mweb result too.
            return NewPipeInit.withPlayerClient("mweb") { StreamInfoCache.get(pageUrl, force = true) }
                .also { DiagLog.log(TAG, "mweb retry ok") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagLog.log(TAG, "mweb retry failed in ${System.currentTimeMillis() - startedAt}ms: ${e::class.simpleName}")
        }
    }
    return null
}
