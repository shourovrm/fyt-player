package com.fyiplayer.app.player

import androidx.media3.common.PlaybackException

/** What PlaybackSession does about one player error. Every action except [SHOW_ERROR] spends a
 *  budget in [PlayerErrorBudget], so no sequence of errors can loop. */
internal enum class PlayerErrorAction {
    /** Live window fell behind: seek to the default position, prepare again. No re-resolve. */
    SEEK_TO_DEFAULT_POSITION,
    /** The decoder died because its output surface was released: prepare again in place. */
    REPREPARE_AFTER_SURFACE_LOSS,
    /** A signed URL aged out (HTTP 401/403/410): drop the cached resolve and fetch a fresh one. */
    RERESOLVE_EXPIRED_URL,
    /** The network dropped under the player: re-resolve, the cached formats stay valid. */
    RERESOLVE_AFTER_TRANSPORT_FAILURE,
    /** The device decoder refused this rendition: play the next lower one. */
    FALL_BACK_TO_LOWER_RENDITION,
    /** Nothing left to try: put an honest error on screen. */
    SHOW_ERROR,
}

/** Facts read off a [PlaybackException]; the classifier sees only these, never the exception. */
internal data class PlayerErrorFacts(
    val errorCode: Int,
    val httpStatus: Int?,
    val isTransportFailure: Boolean,
    val isSurfaceReleased: Boolean,
)

/** What the session has already spent on the current item, plus what is possible right now. */
internal data class PlayerErrorBudget(
    val reResolveSpent: Boolean,
    val defaultPositionSeeksSpent: Int,
    val lowerRenditionSpent: Boolean,
    val lowerRenditionAvailable: Boolean,
    /** Milliseconds since the last surface-loss recovery, null if there has not been one. */
    val msSinceSurfaceRecovery: Long?,
)

internal const val MAX_DEFAULT_POSITION_SEEKS = 3

/** A genuinely broken surface must reach the error screen instead of re-preparing forever;
 *  PipePipe uses the same 10 s. */
internal const val SURFACE_RECOVERY_COOLDOWN_MS = 10_000L

private val DECODER_ERROR_CODES = setOf(
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
)

/**
 * Chooses the recovery for one error. Order matters: an expired or refused URL is decided first,
 * so an HTTP 403 on a FRESH url (re-resolve already spent) goes straight to [SHOW_ERROR] and is
 * never retried -- that is the platform refusing this client, and nothing here works around it.
 */
internal fun classifyPlayerError(facts: PlayerErrorFacts, budget: PlayerErrorBudget): PlayerErrorAction {
    if (facts.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
        return if (budget.defaultPositionSeeksSpent < MAX_DEFAULT_POSITION_SEEKS) {
            PlayerErrorAction.SEEK_TO_DEFAULT_POSITION
        } else {
            PlayerErrorAction.SHOW_ERROR
        }
    }

    val expired = facts.httpStatus in EXPIRED_HTTP_CODES
    if (expired || facts.isTransportFailure) {
        if (budget.reResolveSpent) return PlayerErrorAction.SHOW_ERROR
        return if (expired) PlayerErrorAction.RERESOLVE_EXPIRED_URL else PlayerErrorAction.RERESOLVE_AFTER_TRANSPORT_FAILURE
    }

    if (facts.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED && facts.isSurfaceReleased) {
        val cooledDown = budget.msSinceSurfaceRecovery == null || budget.msSinceSurfaceRecovery >= SURFACE_RECOVERY_COOLDOWN_MS
        return if (cooledDown) PlayerErrorAction.REPREPARE_AFTER_SURFACE_LOSS else PlayerErrorAction.SHOW_ERROR
    }

    if (facts.errorCode in DECODER_ERROR_CODES && budget.lowerRenditionAvailable && !budget.lowerRenditionSpent) {
        return PlayerErrorAction.FALL_BACK_TO_LOWER_RENDITION
    }
    return PlayerErrorAction.SHOW_ERROR
}

/** The height ceiling that selects the next rendition below [selectedHeight], or null when there
 *  is none (or the current height is unknown, so "lower" has no meaning). */
internal fun lowerRenditionCeiling(availableHeights: List<Int>, selectedHeight: Int?): Int? {
    if (selectedHeight == null) return null
    return availableHeights.filter { it < selectedHeight }.maxOrNull()
}

/**
 * True when the failure came from the codec's output surface being released (screen off, the
 * shared PlayerView being reparented), not from a missing or unsupported decoder. Media3 wraps
 * the platform's IllegalArgumentException("... surface ...") inside the init failure. The message
 * is only inspected here, never logged -- it can carry a URL.
 */
internal fun isSurfaceReleasedFailure(error: Throwable?): Boolean {
    var cause: Throwable? = error
    var depth = 0
    while (cause != null && depth++ < 8) {
        val message = cause.message
        if (cause is IllegalArgumentException && message != null && message.contains("surface", ignoreCase = true)) {
            return true
        }
        cause = cause.cause.takeIf { it !== cause }
    }
    return false
}
