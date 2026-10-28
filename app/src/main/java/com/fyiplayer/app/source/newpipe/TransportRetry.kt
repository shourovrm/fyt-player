package com.fyiplayer.app.source.newpipe

import com.fyiplayer.app.DiagLog
import com.fyiplayer.app.core.ExtractionError
import kotlinx.coroutines.delay

/** Waits before each retry: three attempts in total. A DNS or socket blip usually clears within
 *  seconds, and a dead network still reaches the "No connection" banner in about 3 s. */
internal val TRANSPORT_RETRY_DELAYS_MILLIS: List<Long> = listOf(1_000L, 2_000L)

/**
 * Re-runs [block] after each [ExtractionError.Network] until the delays run out.
 *
 * Only Network is retried. [guarded] produces Network from [isTransportFailure] alone, so walls
 * (AccessChallenge), unavailable content and parse failures never reach this loop: retrying a
 * wall would be a retry through it. A CancellationException is not caught here, so a cancelled
 * caller stops inside [delayBefore] instead of running the next attempt.
 *
 * Inline so [block] may call the non-suspend [guarded] and the suspend StreamInfoCache alike.
 */
internal suspend inline fun <T> withTransportRetry(block: () -> T): T =
    withTransportRetry(TRANSPORT_RETRY_DELAYS_MILLIS, { delay(it) }, block)

// Kotlin forbids a default value on a suspend lambda parameter of an inline function, hence the
// two overloads: this one lets tests inject the delays and the waiting.
internal suspend inline fun <T> withTransportRetry(
    delaysMillis: List<Long>,
    delayBefore: suspend (Long) -> Unit,
    block: () -> T,
): T {
    var retriesUsed = 0
    while (true) {
        try {
            return block()
        } catch (failure: ExtractionError.Network) {
            if (retriesUsed >= delaysMillis.size) throw failure
            DiagLog.log("TransportRetry", "retry ${retriesUsed + 1} after ${failure.cause?.let { it::class.simpleName }}")
            delayBefore(delaysMillis[retriesUsed])
            retriesUsed++
        }
    }
}

/** [guarded] with the transport retry applied: the call sites that can run suspend code use this
 *  so the mapping and the retry stay one expression. */
internal suspend inline fun <T> guardedWithRetry(block: () -> T): T =
    withTransportRetry { guarded(block) }
