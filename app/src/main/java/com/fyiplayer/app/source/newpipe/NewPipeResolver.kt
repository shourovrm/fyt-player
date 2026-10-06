package com.fyiplayer.app.source.newpipe

import com.fyiplayer.app.DiagLog
import com.fyiplayer.app.core.CaptionTrack
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.Protocol
import com.fyiplayer.app.core.Resolved
import com.fyiplayer.app.core.SegmentIndexInfo
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.engine.UrlScopedResolver
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.MediaFormat as NpMediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

private const val TAG = "NewPipeResolver"
private val HOSTS = setOf("youtube.com", "m.youtube.com", "www.youtube.com", "music.youtube.com", "youtu.be")

/**
 * Tier 0 of the resolver chain: NewPipeExtractor's own parsing, no engine subprocess. Only
 * handles watch/shorts pages -- [ChainResolver] falls through to the engine for everything else
 * and for anything this tier fails to parse.
 */
class NewPipeResolver(private val client: OkHttpClient) : UrlScopedResolver {

    /** Mirrors [com.fyiplayer.app.source.youtube.YoutubeSource.matches]'s host set, narrowed to
     *  the page shapes this tier actually knows how to extract. */
    override fun handles(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (host !in HOSTS) return false
        if (host == "youtu.be") return true
        val path = uri.path ?: return false
        return path == "/watch" || path.startsWith("/shorts/")
    }

    override suspend fun resolve(ref: VideoRef): Resolved = withContext(Dispatchers.IO) {
        NewPipeInit.ensure(client)
        val fetchStartedAt = System.currentTimeMillis()
        val info = try {
            StreamInfoCache.get(ref.pageUrl).also {
                DiagLog.log(TAG, "stream info ok in ${System.currentTimeMillis() - fetchStartedAt}ms")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val mapped = mapNewPipeError(e)
            val wallReason = (mapped as? ExtractionError.AccessChallenge)?.reason
            DiagLog.log(
                TAG,
                "stream info failed in ${System.currentTimeMillis() - fetchStartedAt}ms: " +
                    "${e::class.simpleName} -> ${mapped::class.simpleName} reason=$wallReason",
            )
            // Age/login wall + signed in: retry once on the TVHTML5 client, whose player responses
            // honour the account's age verification. Any other wall (rate limit, bot check, geo,
            // paid) is not about the account, and a second request with the cookie attached would
            // be a retry through it. Everything else resolves on visionos -- TVHTML5 URLs are
            // ciphered and googlevideo 403s them for popular videos.
            val retryAllowed = wallReason?.signedInRetryMayHelp() == true
            if (retryAllowed && YoutubeAuth.cookieHeader() != null) {
                val retryStartedAt = System.currentTimeMillis()
                try {
                    // force=true: the cached entry (if any) is the anonymous fetch that just hit
                    // the wall -- refetch and replace it so detail()/seekThumbnails() see the
                    // signed-in result too, not the stale walled one.
                    val retried = NewPipeInit.withSignedInPlayerClient {
                        StreamInfoCache.get(ref.pageUrl, force = true)
                    }
                    DiagLog.log(TAG, "signed-in retry ok in ${System.currentTimeMillis() - retryStartedAt}ms")
                    return@withContext toResolved(ref, retried)
                } catch (e2: CancellationException) {
                    throw e2
                } catch (e2: Exception) {
                    DiagLog.log(
                        TAG,
                        "signed-in retry failed in ${System.currentTimeMillis() - retryStartedAt}ms: ${e2::class.simpleName}",
                    )
                    throw mapped // the wall is the honest reason, not the retry's failure
                }
            }
            if (wallReason != null) DiagLog.log(TAG, "signed-in retry skipped: allowed=$retryAllowed")
            throw mapped
        }
        toResolved(ref, info)
    }

    /** Drops the shared StreamInfo entry too -- a stale one would re-feed the same expired URL to
     *  detail()/seekThumbnails() even after the resolve cache above forgot it. */
    override fun invalidate(pageUrl: String) {
        StreamInfoCache.invalidate(pageUrl)
    }
}

private fun toResolved(ref: VideoRef, info: StreamInfo): Resolved {
    val formats = buildList {
        info.videoOnlyStreams.orEmpty()
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .forEach { add(videoOnlyFormat(it)) }
        info.videoStreams.orEmpty()
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .forEach { add(muxedFormat(it)) }
        originalAudioOnly(info.audioStreams.orEmpty())
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .forEach { add(audioOnlyFormat(it)) }
        // The extractor's own DASH/torrent delivery methods are skipped: the progressive streams
        // above carry the init/index byte ranges, and MediaItemFactory builds its own one-stream
        // DASH manifests from those. Nothing here plays torrent.
        val hls = info.hlsUrl
        if (!hls.isNullOrBlank()) add(hlsFormat(hls))
    }
    val captions = info.subtitles.orEmpty().mapNotNull { it.toCaptionTrack() }
    // Param NAMES only, never values -- a signed URL must not reach the log. Diagnoses 403s:
    // an unreplaced SIGNATURE_PLACEHOLDER or a missing sig/pot param names the broken stage.
    formats.firstOrNull { it.url.contains("/videoplayback") }?.let { f ->
        try {
            val names = android.net.Uri.parse(f.url).queryParameterNames.sorted().joinToString(",")
            val placeholder = f.url.contains("SIGNATURE_PLACEHOLDER")
            android.util.Log.d("NewPipeResolver", "media url params=[$names] placeholder=$placeholder")
        } catch (t: Throwable) {
            // diagnostics only
        }
    }
    return Resolved(ref = ref, formats = formats, resolvedAtMillis = System.currentTimeMillis(), captions = captions)
}

private fun genericCodec(codec: String?, fallback: String) = if (codec.isNullOrBlank()) fallback else codec

/** Multi-audio videos ship dubbed tracks, and picking audio by bitrate alone can land on a dub in
 *  the wrong language. Same narrowing PipePipe's own client uses (`ListHelper
 *  .filterAudioStreamsByLanguage`, "original" mode): keep original/default/untagged tracks; fall
 *  back to untracked ids, then to everything rather than to silence. */
internal fun originalAudioOnly(streams: List<AudioStream>): List<AudioStream> {
    if (streams.isEmpty()) return streams
    val original = streams.filter { s ->
        val name = s.audioTrackName?.lowercase()
        name == null || "original" in name || "default" in name
    }
    if (original.isNotEmpty()) return original
    val untagged = streams.filter { it.audioTrackId == null }
    return untagged.ifEmpty { streams }
}

private fun videoOnlyFormat(s: VideoStream) = MediaFormat(
    formatId = s.id,
    url = s.content,
    container = s.format?.suffix ?: "",
    protocol = Protocol.PROGRESSIVE,
    height = resolutionToHeight(s.resolution),
    videoCodec = genericCodec(s.codec, "avc1"),
    audioCodec = null,
    bitrate = s.itagItem?.bitrate?.takeIf { it > 0 }?.toLong(),
    filesizeBytes = s.itagItem?.contentLength?.takeIf { it > 0 },
    segmentIndex = segmentIndexOf(s.itagItem, s.codec, isAudio = false),
)

private fun muxedFormat(s: VideoStream) = MediaFormat(
    formatId = s.id,
    url = s.content,
    container = s.format?.suffix ?: "",
    protocol = Protocol.PROGRESSIVE,
    height = resolutionToHeight(s.resolution),
    videoCodec = genericCodec(s.codec, "avc1"),
    // VideoStream exposes no separate audio-track codec for muxed formats; FormatSelection only
    // checks null-ness (isMuxed), never the actual value, so a generic constant is honest enough.
    audioCodec = "mp4a",
)

private fun audioOnlyFormat(s: AudioStream) = MediaFormat(
    formatId = s.id,
    url = s.content,
    container = s.format?.suffix ?: "",
    protocol = Protocol.PROGRESSIVE,
    videoCodec = null,
    audioCodec = genericCodec(s.codec, "mp4a"),
    bitrate = s.averageBitrate.takeIf { it > 0 }?.toLong(),
    filesizeBytes = s.itagItem?.contentLength?.takeIf { it > 0 },
    segmentIndex = segmentIndexOf(s.itagItem, s.codec, isAudio = true),
)

/** The init/index ranges and stream properties a one-stream DASH manifest needs, or null when
 *  the player response did not carry sane ones (the extractor stores -1 for a missing range, and
 *  0/0 for a stream built without an itag) -- the caller then plays the stream progressively. */
internal fun segmentIndexOf(itag: ItagItem?, codec: String?, isAudio: Boolean): SegmentIndexInfo? {
    if (itag == null || codec.isNullOrBlank()) return null
    val initStart = itag.initStart.toLong()
    val initEnd = itag.initEnd.toLong()
    val indexStart = itag.indexStart.toLong()
    val indexEnd = itag.indexEnd.toLong()
    // The index normally starts right after the init header; overlapping ranges mean a bad parse.
    val rangesAreSane = initStart >= 0 && initEnd > initStart && indexStart > initEnd && indexEnd > indexStart
    if (!rangesAreSane) return null
    // Not averageBitrate: the itag table keeps that in kbit/s, the manifest wants bit/s.
    val bandwidth = itag.bitrate.takeIf { it > 0 }?.toLong() ?: return null
    val durationMs = itag.approxDurationMs.takeIf { it > 0 } ?: return null
    return SegmentIndexInfo(
        initStart = initStart,
        initEnd = initEnd,
        indexStart = indexStart,
        indexEnd = indexEnd,
        codecs = codec,
        bitrate = bandwidth,
        durationMs = durationMs,
        width = if (isAudio) null else itag.width.takeIf { it > 0 },
        frameRate = if (isAudio) null else itag.fps.takeIf { it > 0 },
        audioSampleRate = if (isAudio) itag.sampleRate.takeIf { it > 0 } else null,
        audioChannels = if (isAudio) itag.audioChannels.takeIf { it > 0 } else null,
    )
}

private fun hlsFormat(url: String) = MediaFormat(
    formatId = "hls",
    url = url,
    container = "m3u8",
    protocol = Protocol.HLS,
    videoCodec = "avc1",
    audioCodec = "mp4a",
)

private fun SubtitlesStream.toCaptionTrack(): CaptionTrack? {
    val mime = captionMimeType(format) ?: return null
    val name = displayLanguageName ?: languageTag
    return CaptionTrack(
        url = content,
        mimeType = mime,
        languageCode = languageTag,
        label = if (isAutoGenerated) "$name (auto)" else name,
        autoGenerated = isAutoGenerated,
    )
}

/** "720p60" -> 720. Null resolution (e.g. audio, or [VideoStream.RESOLUTION_UNKNOWN]) -> null. */
internal fun resolutionToHeight(resolution: String?): Int? =
    resolution?.let { Regex("""^(\d+)p""").find(it)?.groupValues?.get(1)?.toIntOrNull() }

/** Only formats media3 has a text-track decoder for; SRV1/2/3 (XML transcripts) are skipped. */
internal fun captionMimeType(format: NpMediaFormat?): String? = when (format) {
    NpMediaFormat.VTT -> "text/vtt"
    NpMediaFormat.TTML -> "application/ttml+xml"
    NpMediaFormat.SRT -> "application/x-subrip"
    else -> null
}
