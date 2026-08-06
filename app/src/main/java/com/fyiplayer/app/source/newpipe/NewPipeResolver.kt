package com.fyiplayer.app.source.newpipe

import com.fyiplayer.app.core.CaptionTrack
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.Protocol
import com.fyiplayer.app.core.Resolved
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.engine.UrlScopedResolver
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.MediaFormat as NpMediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

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
        val info = try {
            StreamInfo.getInfo(ServiceList.YouTube, ref.pageUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw mapNewPipeError(e)
        }
        toResolved(ref, info)
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
        info.audioStreams.orEmpty()
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .forEach { add(audioOnlyFormat(it)) }
        // DASH and torrent are deliberately skipped: media3-exoplayer-dash isn't a dependency and
        // Protocol.DASH throws downstream (see MediaItemFactory), and nothing here plays torrent.
        val hls = info.hlsUrl
        if (!hls.isNullOrBlank()) add(hlsFormat(hls))
    }
    val captions = info.subtitles.orEmpty().mapNotNull { it.toCaptionTrack() }
    return Resolved(ref = ref, formats = formats, resolvedAtMillis = System.currentTimeMillis(), captions = captions)
}

private fun genericCodec(codec: String?, fallback: String) = if (codec.isNullOrBlank()) fallback else codec

private fun videoOnlyFormat(s: VideoStream) = MediaFormat(
    formatId = s.id,
    url = s.content,
    container = s.format?.suffix ?: "",
    protocol = Protocol.PROGRESSIVE,
    height = resolutionToHeight(s.resolution),
    videoCodec = genericCodec(s.codec, "avc1"),
    audioCodec = null,
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
)

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

/**
 * One when-chain, most specific first: several of these types extend [ContentNotAvailableException]
 * (itself a [ParsingException]), so order -- not type hierarchy -- decides the bucket.
 */
internal fun mapNewPipeError(e: Exception): ExtractionError = when (e) {
    is ReCaptchaException,
    is AgeRestrictedContentException,
    is PaidContentException,
    is GeographicRestrictionException,
    is SignInConfirmNotBotException,
    -> ExtractionError.AccessChallenge("access challenge")

    is PrivateContentException,
    is ContentNotAvailableException,
    -> ExtractionError.ContentUnavailable("content unavailable")

    is IOException -> ExtractionError.Network("network error", e)

    is ParsingException,
    is ExtractionException,
    -> ExtractionError.Unsupported("platform changed", e)

    else -> ExtractionError.Unsupported("unknown newpipe failure", e)
}
