package com.fyiplayer.app.ui

import com.fyiplayer.app.core.ExtractionError

/**
 * One honest sentence per [ExtractionError] case. [ExtractionError.AccessChallenge] must never
 * read as retryable — the app stops at a wall, it does not imply it can be worked around.
 */
fun ExtractionError.userMessage(): String = when (this) {
    is ExtractionError.Network -> "No connection. Check your network and try again."
    is ExtractionError.ContentUnavailable -> "This video is gone, private, or never existed."
    is ExtractionError.AccessChallenge -> "This page needs a sign-in, a CAPTCHA, or a region the app cannot reach."
    is ExtractionError.Expired -> "This link expired. Open the video again to get a new one."
    is ExtractionError.Unsupported -> "Not supported yet."
}
