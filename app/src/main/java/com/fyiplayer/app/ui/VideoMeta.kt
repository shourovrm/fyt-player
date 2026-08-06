package com.fyiplayer.app.ui

/**
 * Pure display transform of a listing's own age text ([com.fyiplayer.app.core.VideoRef.uploadedText])
 * into a compact form for a meta line. Never parses into a date and never computes an age itself --
 * only rewrites a handful of known "<n> <unit>(s) ago" / "Streamed <n> <unit>(s) ago" shapes the
 * platform already spelled out. Anything else (a phrasing we haven't seen, or already-short text)
 * comes back unchanged: honesty over cleverness.
 */
private val AGE_PATTERN = Regex("""^(?:Streamed\s+)?(\d+)\s+(day|week|month|year)s?\s+ago$""", RegexOption.IGNORE_CASE)
private val UNIT_ABBREV = mapOf("day" to "d", "week" to "w", "month" to "mo", "year" to "y")

internal fun shortAge(text: String?): String? {
    if (text == null) return null
    val match = AGE_PATTERN.matchEntire(text) ?: return text
    val (count, unit) = match.destructured
    return "$count${UNIT_ABBREV.getValue(unit.lowercase())}"
}
