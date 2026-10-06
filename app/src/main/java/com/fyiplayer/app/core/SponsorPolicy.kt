package com.fyiplayer.app.core

import java.net.URI

/** What to do when playback is inside a segment of a category. */
enum class SponsorMode(val label: String) {
    OFF("Off"),
    AUTO_SKIP("Skip automatically"),
    SHOW_BUTTON("Show skip button"),
}

/**
 * SponsorBlock's categories. [apiName] is the exact string the API uses; [markerArgb] is the
 * colour SponsorBlock's own clients draw that category in, so a marker means the same thing here.
 * [defaultMode] keeps what installs had before categories existed: only sponsors were skipped.
 */
enum class SponsorCategory(
    val apiName: String,
    val label: String,
    val markerArgb: Long,
    val defaultMode: SponsorMode = SponsorMode.OFF,
) {
    SPONSOR("sponsor", "Sponsor", 0xFF00D400, SponsorMode.AUTO_SKIP),
    INTRO("intro", "Intermission / intro animation", 0xFF00FFFF),
    OUTRO("outro", "Endcards / credits", 0xFF0202ED),
    INTERACTION("interaction", "Interaction reminder (subscribe, like)", 0xFFCC00FF),
    SELF_PROMO("selfpromo", "Unpaid / self promotion", 0xFFFFFF00),
    MUSIC_OFFTOPIC("music_offtopic", "Non-music section", 0xFFFF9900),
    PREVIEW("preview", "Preview / recap", 0xFF008FD6),
    FILLER("filler", "Filler tangent", 0xFF7300FF),
    ;

    companion object {
        fun fromApiName(name: String?): SponsorCategory? = entries.firstOrNull { it.apiName == name }
    }
}

/** One SponsorBlock window, in player position milliseconds. */
data class SponsorSegment(val startMs: Long, val endMs: Long, val category: SponsorCategory)

/** Everything the player needs to know about the user's SponsorBlock choices, read synchronously. */
data class SponsorPolicy(
    /** The master switch: off means no lookup at all. */
    val enabled: Boolean = false,
    val modes: Map<SponsorCategory, SponsorMode> = defaultSponsorModes(),
    /** Canonical channel keys ([canonicalChannelKey]) SponsorBlock must leave alone. */
    val whitelistedChannels: Set<String> = emptySet(),
) {
    fun modeOf(category: SponsorCategory): SponsorMode = modes[category] ?: category.defaultMode

    val anyCategoryActive: Boolean get() = SponsorCategory.entries.any { modeOf(it) != SponsorMode.OFF }

    fun isChannelWhitelisted(channelUrl: String?): Boolean {
        val key = channelUrl?.let(::canonicalChannelKey) ?: return false
        return key in whitelistedChannels
    }
}

internal fun defaultSponsorModes(): Map<SponsorCategory, SponsorMode> =
    SponsorCategory.entries.associateWith { it.defaultMode }

/** A channel URL reduced to one comparable form (https, no www/m prefix, no trailing slash, no
 *  query), so the URL a listing carries and the one stored in the whitelist always match. Null
 *  for anything that is not a URL with a path. */
fun canonicalChannelKey(url: String): String? {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
    val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
    val path = uri.path?.trimEnd('/').orEmpty()
    if (path.isEmpty()) return null
    return "https://$host$path"
}

/** The segments the player may act on or draw right now: none when SponsorBlock is off or the
 *  current channel is whitelisted, otherwise those whose category is not Off. */
internal fun usableSponsorSegments(
    segments: List<SponsorSegment>,
    policy: SponsorPolicy,
    channelUrl: String?,
): List<SponsorSegment> {
    if (!policy.enabled || policy.isChannelWhitelisted(channelUrl)) return emptyList()
    return segments.filter { policy.modeOf(it.category) != SponsorMode.OFF }
}

/** [skip] is set when the position just entered an auto-skip segment (seek to its end); [offer] is
 *  the show-button segment the position is inside (null otherwise). */
internal data class SponsorDecision(val skip: SponsorSegment?, val offer: SponsorSegment?)

/**
 * Decides one tick. [lastSkippedStart] (see PlaybackSession.sponsorSkipGuardAfter) stops the seek repeating
 * while the position still reads inside the segment that was just skipped. [segments] must
 * already be filtered through [usableSponsorSegments].
 */
internal fun decideSponsorAction(
    positionMs: Long,
    segments: List<SponsorSegment>,
    policy: SponsorPolicy,
    lastSkippedStart: Long?,
): SponsorDecision {
    val inside = segments.filter { positionMs >= it.startMs && positionMs < it.endMs }
    val autoSkip = inside.firstOrNull { policy.modeOf(it.category) == SponsorMode.AUTO_SKIP }
    if (autoSkip != null && lastSkippedStart != autoSkip.startMs) {
        return SponsorDecision(skip = autoSkip, offer = null)
    }
    val offer = inside.firstOrNull { policy.modeOf(it.category) == SponsorMode.SHOW_BUTTON }
    return SponsorDecision(skip = null, offer = offer)
}
