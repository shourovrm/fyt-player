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
        chunked = dataSpec.uri.encodedPath?.startsWith("/videoplayback") == true
        if (!chunked) return upstream.open(dataSpec)

        spec = dataSpec
        position = dataSpec.position
        endExclusive =
            if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.position + dataSpec.length
            else C.LENGTH_UNSET.toLong()
        val firstChunk = openChunk()
        if (endExclusive == C.LENGTH_UNSET.toLong()) {
            // Total size comes from the first window's Content-Range ("bytes X-Y/TOTAL"). A server
            // that ignored Range sent the whole rest of the body instead -- fall back to exactly
            // that (no further windows), which is just the old open-ended behaviour.
            val total = upstream.responseHeaders["Content-Range"]?.firstOrNull()
                ?.substringAfterLast('/')?.toLongOrNull()
            endExclusive = total ?: (position + firstChunk)
        }
        return endExclusive - position
    }

    /** Opens the next bounded window at [position]. Returns the bytes the window will serve. */
    private fun openChunk(): Long {
        val remaining =
            if (endExclusive == C.LENGTH_UNSET.toLong()) CHUNK_BYTES
            else minOf(CHUNK_BYTES, endExclusive - position)
        return upstream.open(
            spec!!.buildUpon().setPosition(position).setLength(remaining).build(),
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
