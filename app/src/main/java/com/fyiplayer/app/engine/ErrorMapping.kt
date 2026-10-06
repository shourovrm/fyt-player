package com.fyiplayer.app.engine

import com.fyiplayer.app.core.AccessChallengeReason
import com.fyiplayer.app.core.ExtractionError

// yt-dlp's own error text can echo the page URL (e.g. "Unsupported URL: https://..."). Strip URLs
// before any marker match, or a URL slug like ".../premium-mix-2024" trips a wall marker that was
// never a real wall -- this is the whole reason the scrub happens before matching, not after.
private val URL_TOKEN = Regex("""https?://\S+""")

// Genuine wall wording, checked first so it always wins even if the same message also happens to
// match a later bucket (e.g. a login wall whose text also contains "404"). Rule order matters: the
// first matching rule names the reason, so the specific walls come before the broad "sign in".
// HTTP statuses are matched in yt-dlp's "HTTP Error NNN" shape, never as bare digits -- a bare
// "429" or "403" would fire on any video id that happens to contain those digits.
private class WallRule(val reason: AccessChallengeReason, pattern: String) {
    val regex = Regex(pattern)
}

private val WALL_RULES = listOf(
    // "Sign in to confirm you're not a bot" contains "sign in", so it must precede LOGIN_REQUIRED.
    WallRule(AccessChallengeReason.BOT_CHECK, """captcha|not a bot"""),
    // 429 means the client is being throttled. Falling through to the WebView tier would load the
    // same throttled site again, so this is a stop, not a fallthrough.
    WallRule(AccessChallengeReason.RATE_LIMIT, """http error 429|too many requests|rate.?limit"""),
    WallRule(AccessChallengeReason.AGE_RESTRICTION, """age verif|age-verif|confirm your age"""),
    WallRule(AccessChallengeReason.GEO_BLOCK, """geo restrict|geo-restrict|not available in your country"""),
    WallRule(AccessChallengeReason.PAID, """members only|members-only|premium|paywall"""),
    WallRule(AccessChallengeReason.LOGIN_REQUIRED, """login|log in|sign in|private video"""),
    // extraction.md: TikTok flips from 200 to 403 once the IP is rate limited; the cause is unknown.
    WallRule(AccessChallengeReason.UNKNOWN, """http error 403"""),
)

/**
 * Maps the engine's free-text failure output to a typed [ExtractionError]. The exception message
 * is the only thing this function may read. The returned error never carries the original message
 * forward -- only a fixed, safe-to-display string per case -- since that message can itself echo
 * the page URL the engine was invoked with, and callers must never surface that raw to the UI.
 */
internal fun mapEngineError(e: Exception): ExtractionError {
    val m = (e.message ?: "").replace(URL_TOKEN, " ").lowercase()
    val wall = WALL_RULES.firstOrNull { it.regex.containsMatchIn(m) }
    return when {
        wall != null -> ExtractionError.AccessChallenge("access challenge", wall.reason)
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
