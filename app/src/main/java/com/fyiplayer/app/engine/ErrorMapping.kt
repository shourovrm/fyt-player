package com.fyiplayer.app.engine

import com.fyiplayer.app.core.ExtractionError

// yt-dlp's own error text can echo the page URL (e.g. "Unsupported URL: https://..."). Strip URLs
// before any marker match, or a URL slug like ".../premium-mix-2024" trips a wall marker that was
// never a real wall -- this is the whole reason the scrub happens before matching, not after.
private val URL_TOKEN = Regex("""https?://\S+""")

// Genuine interactive-wall wording, checked first so it always wins even if the same message also
// happens to match a later bucket (e.g. a login wall whose text also contains "404").
private val ACCESS_CHALLENGE_MARKERS = listOf(
    "captcha", "login", "log in", "sign in", "age verif", "age-verif",
    "private video", "members only", "members-only", "premium",
    "geo restrict", "geo-restrict", "not available in your country", "paywall",
)

/**
 * Maps the engine's free-text failure output to a typed [ExtractionError]. The exception message
 * is the only thing this function may read. The returned error never carries the original message
 * forward -- only a fixed, safe-to-display string per case -- since that message can itself echo
 * the page URL the engine was invoked with, and callers must never surface that raw to the UI.
 */
internal fun mapEngineError(e: Exception): ExtractionError {
    val m = (e.message ?: "").replace(URL_TOKEN, " ").lowercase()
    return when {
        ACCESS_CHALLENGE_MARKERS.any { it in m } ->
            ExtractionError.AccessChallenge("access challenge")
        listOf("expired", "410").any { it in m } ->
            ExtractionError.Expired("link expired")
        listOf("unsupported url", "unable to extract").any { it in m } ->
            ExtractionError.Unsupported("unsupported url", e)
        listOf("timeout", "timed out", "dns", "connection reset", "unable to resolve host").any { it in m } ->
            ExtractionError.Network("network error", e)
        listOf("404", "video unavailable", "has been removed").any { it in m } ->
            ExtractionError.ContentUnavailable("content unavailable")
        else -> ExtractionError.Unsupported("unknown engine failure", e)
    }
}
