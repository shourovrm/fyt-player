package com.fyiplayer.app.ui

import com.fyiplayer.app.core.ExtractionError

/**
 * One honest sentence per [ExtractionError] case. [ExtractionError.AccessChallenge] must never
 * read as retryable — the app stops at a wall, it does not imply it can be worked around.
 */
fun ExtractionError.userMessage(): String = when (this) {
    is ExtractionError.Network -> "No connection. Check your network and try again."
    is ExtractionError.ContentUnavailable -> "This video is gone, private, or never existed."
    is ExtractionError.AccessChallenge -> "This page is behind a login, CAPTCHA, or regional wall the app can't cross."
    is ExtractionError.Expired -> "This link expired. Reopen the video to fetch a fresh one."
    is ExtractionError.Unsupported -> "This isn't supported yet."
}
