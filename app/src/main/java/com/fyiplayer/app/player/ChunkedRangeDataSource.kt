package com.fyiplayer.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

// Mirrors StreamDownloader's CHUNK_BYTES: the size the web player/yt-dlp read in, proven to
// unlock full transfer speed on this network by the downloader.
private const val CHUNK_BYTES = 10L * 1024 * 1024

/**
 * googlevideo paces one long open `/videoplayback` request down to roughly realtime, which shows
 * up as mid-play stalls on anything above SD. The web player (and PipePipe's synthesized-DASH
 * path, and this app's own downloader) instead read bounded ranges and re-request -- each window
 * arrives at full speed. This wraps the real HTTP source and turns one open-ended read into a
 * chain of [CHUNK_BYTES] windows, invisibly to ExoPlayer: open() still reports the full length,
 * read() re-opens the next window when one ends.
 *
 * Ranges ride as a `range=start-end` QUERY PARAMETER, never an HTTP Range header: official
 * clients use the parameter (PipePipe's DeliveryType doc says exactly this, and its DASH path
 * enables setRangeParameterEnabled), and googlevideo intermittently 403s header-ranged requests
 * from non-web clients (seen live on this device). The inner DataSpec is therefore always
 * position=0/length=UNSET so the upstream HTTP source has no reason to add a Range header.
 *
 * Total size comes from the URL's own `clen` parameter (googlevideo attaches it to progressive
 * URLs) or ExoPlayer's requested span; a /videoplayback URL carrying neither falls back to one
 * open-ended passthrough request -- the old pacing, but never a wrong length.
 *
 * Non-/videoplayback URLs (HLS segments, subtitles, other hosts) pass through untouched.
 */
internal class ChunkedRangeDataSource(private val upstream: DataSource) : DataSource {

    private var spec: DataSpec? = null
    private var chunked = false
    private var position = 0L // absolute offset of the next byte to read
    private var endExclusive = C.LENGTH_UNSET.toLong() // absolute end of the whole requested span

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        val isVideoPlayback = dataSpec.uri.encodedPath?.startsWith("/videoplayback") == true
        val total =
            if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.position + dataSpec.length
            else dataSpec.uri.getQueryParameter("clen")?.toLongOrNull() ?: C.LENGTH_UNSET.toLong()
        chunked = isVideoPlayback && total != C.LENGTH_UNSET.toLong()
        if (!chunked) return upstream.open(dataSpec)

        spec = dataSpec
        position = dataSpec.position
        endExclusive = total
        openChunk()
        return endExclusive - position
    }

    /** Opens the next bounded window at [position] via the `range=` param. */
    private fun openChunk(): Long {
        val windowEnd = minOf(position + CHUNK_BYTES, endExclusive) // exclusive
        val base = spec!!
        val uri = base.uri.buildUpon().clearQuery().apply {
            // rebuild every param except any stale range, then append ours
            base.uri.queryParameterNames.filter { it != "range" }.forEach { name ->
                base.uri.getQueryParameters(name).forEach { appendQueryParameter(name, it) }
            }
            appendQueryParameter("range", "$position-${windowEnd - 1}")
        }.build()
        return upstream.open(
            base.buildUpon().setUri(uri).setPosition(0).setLength(C.LENGTH_UNSET.toLong()).build(),
        )
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = upstream.read(buffer, offset, length)
        if (!chunked) return read
        if (read != C.RESULT_END_OF_INPUT) {
            if (read > 0) position += read
            return read
        }
        // Current window exhausted: chain into the next one, or report the true end.
        if (position >= endExclusive) return C.RESULT_END_OF_INPUT
        upstream.close()
        openChunk()
        return read(buffer, offset, length)
    }

    override fun getUri(): Uri? = upstream.uri ?: spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        spec = null
        upstream.close()
    }
}
