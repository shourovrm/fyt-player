package com.fyiplayer.app.player

import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.Protocol
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

    private fun sourceFor(format: MediaFormat): MediaSource {
        val item = MediaItem.fromUri(format.url)
        val dsFactory = dataSourceFactory(format.headers)
        return when (format.protocol) {
            Protocol.HLS -> HlsMediaSource.Factory(dsFactory).createMediaSource(item)
            Protocol.PROGRESSIVE -> ProgressiveMediaSource.Factory(dsFactory).createMediaSource(item)
            // ponytail: media3-exoplayer-dash isn't on this app's classpath and no source emits
            // Protocol.DASH yet either; add the dependency + a DashMediaSource branch if one does.
            Protocol.DASH -> throw UnsupportedOperationException("DASH playback not wired up yet")
        }
    }

    /** [FormatSelection.Paired] becomes one [MergingMediaSource] so both play as a single item. */
    fun create(selection: FormatSelection): MediaSource = when (selection) {
        is FormatSelection.Single -> sourceFor(selection.format)
        is FormatSelection.Paired -> MergingMediaSource(
            /* adjustPeriodTimeOffsets = */ true,
            /* clipDurations = */ true,
            sourceFor(selection.video),
            sourceFor(selection.audio),
        )
    }
}
