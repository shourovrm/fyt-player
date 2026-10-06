package com.fyiplayer.app.player

import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.SegmentIndexInfo

/*
 * Why a manifest for files that are not DASH: ExoPlayer treats a plain progressive URL as an
 * open-ended read it may abort, and an aborted HTTP/1.1 response kills the connection (YouTube's
 * video hosts speak nothing newer). A SegmentBase manifest tells ExoPlayer where the init header
 * and segment index live, so every request becomes a bounded byte range read to completion and
 * the connection is reused. Shape follows PipePipe's YoutubeProgressiveDashManifestCreator.
 *
 * The returned string contains signed media URLs: never log it or write it anywhere.
 */

private const val MPD_NAMESPACE = "urn:mpeg:DASH:schema:MPD:2011"
private const val STEREO_CHANNELS = 2

/** A single-Period static MPD with one video and one audio AdaptationSet, or null when either
 *  format lacks a usable [MediaFormat.segmentIndex] or has a container with no known mime type
 *  -- the caller then falls back to plain progressive playback. */
internal fun buildDashManifest(video: MediaFormat, audio: MediaFormat): String? {
    val videoIndex = video.segmentIndex?.takeIf(::isUsable) ?: return null
    val audioIndex = audio.segmentIndex?.takeIf(::isUsable) ?: return null
    val videoMimeType = mimeTypeFor("video", video.container) ?: return null
    val audioMimeType = mimeTypeFor("audio", audio.container) ?: return null
    // A video Representation needs at least one dimension to be selectable by the track selector.
    if (video.height == null && videoIndex.width == null) return null

    val durationMs = maxOf(videoIndex.durationMs, audioIndex.durationMs)
    return buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        append("<MPD xmlns=\"$MPD_NAMESPACE\" type=\"static\" minBufferTime=\"PT1.500S\" ")
        append("profiles=\"urn:mpeg:dash:profile:full:2011\" ")
        append("mediaPresentationDuration=\"${durationAttribute(durationMs)}\">\n")
        append("<Period>\n")
        appendAdaptationSet(
            adaptationSetId = 0,
            mimeType = videoMimeType,
            representation = {
                append("<Representation id=\"${escapeXml(video.formatId)}\" codecs=\"${escapeXml(videoIndex.codecs)}\" ")
                append("startWithSAP=\"1\" bandwidth=\"${videoIndex.bitrate}\"")
                videoIndex.width?.let { append(" width=\"$it\"") }
                video.height?.let { append(" height=\"$it\"") }
                videoIndex.frameRate?.let { append(" frameRate=\"$it\"") }
                append(">\n")
                appendSource(video.url, videoIndex)
            },
        )
        appendAdaptationSet(
            adaptationSetId = 1,
            mimeType = audioMimeType,
            representation = {
                append("<Representation id=\"${escapeXml(audio.formatId)}\" codecs=\"${escapeXml(audioIndex.codecs)}\" ")
                append("startWithSAP=\"1\" bandwidth=\"${audioIndex.bitrate}\"")
                audioIndex.audioSampleRate?.let { append(" audioSamplingRate=\"$it\"") }
                append(">\n")
                append("<AudioChannelConfiguration ")
                append("schemeIdUri=\"urn:mpeg:dash:23003:3:audio_channel_configuration:2011\" ")
                append("value=\"${audioIndex.audioChannels ?: STEREO_CHANNELS}\"/>\n")
                appendSource(audio.url, audioIndex)
            },
        )
        append("</Period>\n</MPD>\n")
    }
}

/** Every value is used in a range or a bandwidth, where zero or negative means the extractor
 *  never found it. */
private fun isUsable(index: SegmentIndexInfo): Boolean =
    index.codecs.isNotBlank() &&
        index.bitrate > 0 &&
        index.durationMs > 0 &&
        index.initStart >= 0 && index.initEnd > index.initStart &&
        index.indexStart > index.initEnd && index.indexEnd > index.indexStart

private fun mimeTypeFor(kind: String, container: String): String? = when (container.lowercase()) {
    "mp4", "m4a" -> "$kind/mp4"
    "webm", "weba" -> "$kind/webm"
    else -> null
}

/** "PT213.500S". Built from integers: String.format would follow the device locale and could
 *  print a decimal comma, which the parser rejects. */
private fun durationAttribute(durationMs: Long): String {
    val seconds = durationMs / 1000
    val milliseconds = (durationMs % 1000).toString().padStart(3, '0')
    return "PT$seconds.${milliseconds}S"
}

private fun StringBuilder.appendAdaptationSet(
    adaptationSetId: Int,
    mimeType: String,
    representation: StringBuilder.() -> Unit,
) {
    append("<AdaptationSet id=\"$adaptationSetId\" mimeType=\"$mimeType\" subsegmentAlignment=\"true\">\n")
    representation()
    append("</Representation>\n</AdaptationSet>\n")
}

private fun StringBuilder.appendSource(url: String, index: SegmentIndexInfo) {
    append("<BaseURL>${escapeXml(url)}</BaseURL>\n")
    append("<SegmentBase indexRange=\"${index.indexStart}-${index.indexEnd}\">\n")
    append("<Initialization range=\"${index.initStart}-${index.initEnd}\"/>\n")
    append("</SegmentBase>\n")
}

// Signed URLs are full of '&'; unescaped, the manifest is not well-formed XML.
private fun escapeXml(text: String): String = buildString(text.length) {
    for (character in text) {
        when (character) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(character)
        }
    }
}
