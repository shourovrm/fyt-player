package com.fyiplayer.app.ui

import com.fyiplayer.app.core.AccessChallengeReason
import com.fyiplayer.app.core.ExtractionError

/**
 * One honest sentence per [ExtractionError] case. An [ExtractionError.AccessChallenge] names its
 * cause; only the causes in [retryHelps] say "try again", the app never implies a wall can be
 * worked around.
 */
fun ExtractionError.userMessage(): String = when (this) {
    is ExtractionError.Network -> "No connection. Check your network and try again."
    is ExtractionError.ContentUnavailable -> "This video is gone, private, or never existed."
    is ExtractionError.AccessChallenge -> reason.message()
    is ExtractionError.Expired -> "This link expired. Open the video again to get a new one."
    is ExtractionError.Unsupported -> "Not supported yet."
}

private fun AccessChallengeReason.message(): String = when (this) {
    AccessChallengeReason.BOT_CHECK -> "The site wants to confirm you are not a bot. Signing in usually helps."
    AccessChallengeReason.RATE_LIMIT -> "Too many requests from this network. Try again in a few minutes."
    AccessChallengeReason.AGE_RESTRICTION -> "This video is age-restricted. Sign in with an adult account."
    AccessChallengeReason.LOGIN_REQUIRED -> "This video needs you to be signed in."
    AccessChallengeReason.GEO_BLOCK -> "This video is not available in your region."
    AccessChallengeReason.PAID -> "This video is for members or paid viewers only."
    AccessChallengeReason.UNKNOWN -> "The site blocked this request. Try again."
}

/** Whether signing in can lift this wall; the Sign in button shows only for these. */
fun AccessChallengeReason.signInHelps(): Boolean = when (this) {
    AccessChallengeReason.BOT_CHECK,
    AccessChallengeReason.AGE_RESTRICTION,
    AccessChallengeReason.LOGIN_REQUIRED,
    -> true
    else -> false
}

/** Whether waiting and asking again can lift this wall; the Retry button shows only for these. */
fun AccessChallengeReason.retryHelps(): Boolean =
    this == AccessChallengeReason.RATE_LIMIT || this == AccessChallengeReason.UNKNOWN
