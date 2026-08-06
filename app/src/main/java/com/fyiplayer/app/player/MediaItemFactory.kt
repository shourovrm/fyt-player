package com.fyiplayer.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
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
    private val httpClient = OkHttpClient()

    private fun dataSourceFactory(headers: Map<String, String>): DataSource.Factory =
        // headers must be applied verbatim or the CDN rejects the request
        OkHttpDataSource.Factory(httpClient).setDefaultRequestProperties(headers)

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
            Protocol.HLS -> HlsMediaSource.Factory(dsFactory).createMediaSource(item)
            Protocol.PROGRESSIVE -> ProgressiveMediaSource.Factory(dsFactory).createMediaSource(item)
            // ponytail: media3-exoplayer-dash isn't on this app's classpath and no source emits
            // Protocol.DASH yet either; add the dependency + a DashMediaSource branch if one does.
            Protocol.DASH -> throw UnsupportedOperationException("DASH playback not wired up yet")
        }
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
            .createMediaSource(config, C.TIME_UNSET)
    }

    /** [FormatSelection.Paired] becomes one [MergingMediaSource] so both play as a single item;
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
            is FormatSelection.Single -> sourceFor(selection.format, metadata)
            is FormatSelection.Paired -> MergingMediaSource(
                /* adjustPeriodTimeOffsets = */ true,
                /* clipDurations = */ true,
                sourceFor(selection.video, metadata),
                sourceFor(selection.audio, metadata),
            )
        }
        if (captions.isEmpty()) return base
        val subtitleSources = captions.map(::subtitleSourceFor).toTypedArray()
        return MergingMediaSource(true, true, base, *subtitleSources)
    }
}
