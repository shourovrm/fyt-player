package com.fyiplayer.app.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.fyiplayer.app.DiagLog
import com.fyiplayer.app.core.CaptionTrack
import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.Protocol
import com.fyiplayer.app.core.VideoRef
import okhttp3.OkHttpClient

/**
 * [FormatSelection] -> a media3 [MediaSource]. One shared [OkHttpClient]: headers vary per
 * request/format, not per client, so they go on the [DataSource.Factory], not the client.
 */
object MediaItemFactory {
    // Request-shaped for googlevideo (rn/UA/TE, see MediaHttp.kt) -- an unshapen client gets
    // paced to roughly realtime and playback stutters on anything above SD.
    private val httpClient = mediaHttpClient()

    // Set by init(): needed for the disk cache dir. Nullable so a missed init() degrades to no
    // caching instead of a crash -- caching is an optimization, playback must not depend on it.
    @Volatile private var appContext: Context? = null

    /** Call once at process startup (before first playback) so media bytes get disk-cached. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // Default policy retries a dead signed URL for ~30-90s (backoff) before onPlayerError ever
    // fires -- that's where PlaybackSession's re-resolve lives. Failing fast on the codes that
    // mean "this URL is dead" (see isExpiredHttpError/EXPIRED_HTTP_CODES) turns that into seconds.
    private val loadErrorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy() {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
            // Transport failures (timeout/DNS/connect) skip the backoff as well: PlaybackSession's
            // onPlayerError re-prepares once from the saved position, PipePipe-style, and one
            // fresh attempt beats three retries on a socket the OS already dropped.
            if (isExpiredHttpError(loadErrorInfo.exception) || isNetworkCause(loadErrorInfo.exception)) C.TIME_UNSET
            else super.getRetryDelayMsFor(loadErrorInfo)
    }

    private fun dataSourceFactory(headers: Map<String, String>): DataSource.Factory {
        // headers must be applied verbatim or the CDN rejects the request
        val http = OkHttpDataSource.Factory(httpClient).setDefaultRequestProperties(headers)
        // googlevideo progressive gets read in bounded windows (see ChunkedRangeDataSource);
        // everything else passes through the wrapper untouched.
        val chunked = DataSource.Factory { ChunkedRangeDataSource(http.createDataSource()) }
        val context = appContext ?: return chunked // init() never called: no cache, still plays
        // Cache sits OUTSIDE ChunkedRangeDataSource: it must see the caller's original DataSpec
        // (position/length), not one already rewritten with a range= window -- otherwise every
        // chunk would key/span differently and the cache could never assemble one whole file.
        return CacheDataSource.Factory()
            .setCache(MediaCache.get(context))
            .setCacheKeyFactory(FyiCacheKeyFactory)
            .setUpstreamDataSourceFactory(chunked)
            // A dead cache entry (partial/corrupt write) must not block playback -- fall through
            // to upstream instead of failing the load.
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    // Feeds the lockscreen/notification (MediaSessionService reads it off the player's current
    // MediaItem). thumbnailUrl is handed to media3 in memory only -- never persisted or logged,
    // same rule as the media URL itself (Contracts.kt).
    private fun metadataOf(ref: VideoRef?): MediaMetadata = MediaMetadata.Builder()
        .setTitle(ref?.title)
        .setArtist(ref?.uploader)
        .setArtworkUri(ref?.thumbnailUrl?.let(Uri::parse))
        .build()

    private fun sourceFor(format: MediaFormat, metadata: MediaMetadata): MediaSource {
        val item = MediaItem.Builder().setUri(format.url).setMediaMetadata(metadata).build()
        val dsFactory = dataSourceFactory(format.headers)
        return when (format.protocol) {
            Protocol.HLS -> HlsMediaSource.Factory(dsFactory)
                .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                .createMediaSource(item)
            Protocol.PROGRESSIVE -> ProgressiveMediaSource.Factory(dsFactory)
                .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                .createMediaSource(item)
            // The dash dependency is now present, but no resolver emits a Protocol.DASH format
            // (the DASH path below is built from PROGRESSIVE pairs), so this stays unsupported.
            Protocol.DASH -> throw UnsupportedOperationException("DASH playback not wired up yet")
        }
    }

    // Absolute BaseURLs in our manifest override this; the parser only wants some base to resolve
    // relative references against, and there are none.
    private val manifestBaseUri = Uri.parse("https://www.youtube.com/")

    /** Why this pair cannot play as DASH, or null when it can. Reasons are fixed words (they go
     *  to the playback log, which never holds URLs). */
    private fun dashFallbackReason(video: MediaFormat, audio: MediaFormat): String? = when {
        video.protocol != Protocol.PROGRESSIVE || audio.protocol != Protocol.PROGRESSIVE -> "notProgressive"
        video.segmentIndex == null || audio.segmentIndex == null -> "noSegmentIndex"
        // One DataSource.Factory serves both streams, so request headers must agree.
        video.headers != audio.headers -> "headersDiffer"
        !isGooglevideoProgressive(video.url) || !isGooglevideoProgressive(audio.url) -> "notGooglevideo"
        else -> null
    }

    // Same discriminator ChunkedRangeDataSource uses: only query-style /videoplayback URLs accept
    // the `range=` parameter that every DASH chunk request is turned into.
    private fun isGooglevideoProgressive(url: String): Boolean {
        val uri = Uri.parse(url)
        return isChunkable(uri.encodedPath, uri.query)
    }

    /** One video + one audio stream as a [DashMediaSource], or null (with the reason) when the
     *  manifest cannot be built or parsed. Every chunk request goes out through [dataSourceFactory]
     *  -- the same cache + ChunkedRangeDataSource + OkHttp stack the progressive path uses. */
    private fun dashSourceFor(video: MediaFormat, audio: MediaFormat, metadata: MediaMetadata): Pair<MediaSource?, String?> {
        val manifestText = buildDashManifest(video, audio) ?: return null to "manifestRejected"
        val manifest = try {
            DashManifestParser().parse(manifestBaseUri, manifestText.byteInputStream())
        } catch (_: Exception) {
            // Any parser failure, not just IOException: this runs outside resolveItem's catch, and
            // the progressive path below still plays the same pair.
            return null to "manifestParseFailed"
        }
        val mediaItem = MediaItem.Builder().setMediaMetadata(metadata).build()
        val source = DashMediaSource.Factory(dataSourceFactory(video.headers))
            .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            .createMediaSource(manifest, mediaItem)
        return source to null
    }

    /** A video+audio pair: DASH with SegmentBase when both sides carry byte ranges (bounded range
     *  requests that finish, so the HTTP/1.1 connection is reused), else two progressive sources. */
    private fun pairedSource(video: MediaFormat, audio: MediaFormat, metadata: MediaMetadata): MediaSource {
        var fallbackReason = dashFallbackReason(video, audio)
        if (fallbackReason == null) {
            val (dashSource, parseFailure) = dashSourceFor(video, audio, metadata)
            if (dashSource != null) {
                DiagLog.log("Playback", "source=dash")
                return dashSource
            }
            fallbackReason = parseFailure
        }
        DiagLog.log("Playback", "source=progressive reason=$fallbackReason")
        return MergingMediaSource(
            /* adjustPeriodTimeOffsets = */ true,
            /* clipDurations = */ true,
            sourceFor(video, metadata),
            sourceFor(audio, metadata),
        )
    }

    // No headers: a caption URL's own signature/query carries what it needs, same as the video
    // formats' -- but subtitles never came with a per-format header map to reuse.
    private val subtitleDataSourceFactory: DataSource.Factory by lazy { dataSourceFactory(emptyMap()) }

    /** One sideloaded text track. [SingleSampleMediaSource] is the media3 mechanism for exactly
     *  this -- a subtitle URL with no container of its own -- merged in alongside audio/video by
     *  [create] below. No selection flag is set: every track starts unselected, which combined
     *  with [PlaybackSession] disabling the text renderer by default is what keeps captions OFF
     *  until the user picks one from [CaptionSheet]. */
    private fun subtitleSourceFor(track: CaptionTrack): MediaSource {
        val config = MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.url))
            .setMimeType(track.mimeType)
            .setLanguage(track.languageCode)
            .setLabel(track.label)
            .build()
        return SingleSampleMediaSource.Factory(subtitleDataSourceFactory)
            .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
            .createMediaSource(config, C.TIME_UNSET)
    }

    /** [FormatSelection.Paired] becomes one item (a [DashMediaSource] or a [MergingMediaSource]);
     *  [captions] (if any) are merged in the same way -- leaf factories like
     *  [ProgressiveMediaSource.Factory]/[HlsMediaSource.Factory] do NOT read
     *  `MediaItem.subtitleConfigurations` themselves (only `DefaultMediaSourceFactory` does, and
     *  this app builds sources directly instead), so sideloaded text tracks only render if merged
     *  in by hand here. [ref] is the queue entry this selection came from -- null only where no
     *  ref exists to describe the source (there is always one in practice); its
     *  title/uploader/thumbnail become the notification and lockscreen metadata. */
    fun create(selection: FormatSelection, ref: VideoRef? = null, captions: List<CaptionTrack> = emptyList()): MediaSource {
        val metadata = metadataOf(ref)
        val base = when (selection) {
            is FormatSelection.Single -> {
                DiagLog.log("Playback", "source=${if (selection.format.protocol == Protocol.HLS) "hls" else "progressive"}")
                sourceFor(selection.format, metadata)
            }
            is FormatSelection.Paired -> pairedSource(selection.video, selection.audio, metadata)
        }
        if (captions.isEmpty()) return base
        val subtitleSources = captions.map(::subtitleSourceFor).toTypedArray()
        return MergingMediaSource(true, true, base, *subtitleSources)
    }
}
