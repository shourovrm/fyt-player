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
 * Total size comes from the URL's own `clen` parameter, ExoPlayer's requested span, or -- for
 * query-style URLs only -- a tiny `range=0-0` probe that reads the total from the
 * `Content-Range` response header. visionos progressive URLs often carry no `clen` (see
 * DECISIONS Gotchas), which is exactly the probe's case. HLS SEGMENT URLs must never be probed:
 * googlevideo encodes their params in the PATH (no query string), rejects an appended `range=`
 * query param with HTTP 400, and a probe there would be a wasted round trip on every single
 * segment fetch (seen live) -- hence the query-string gate. A URL where everything fails falls
 * back to one open-ended passthrough request: the old pacing, but never a wrong length.
 *
 * Non-/videoplayback URLs (subtitles, other hosts) pass through untouched.
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
        val explicitTotal = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.position + dataSpec.length
        } else {
            dataSpec.uri.getQueryParameter("clen")?.toLongOrNull() ?: C.LENGTH_UNSET.toLong()
        }
        val probedTotal = if (isVideoPlayback && explicitTotal == C.LENGTH_UNSET.toLong() &&
            !dataSpec.uri.query.isNullOrEmpty()
        ) probeTotal(dataSpec) else null
        val total = resolveChunkTotal(isVideoPlayback, explicitTotal, probedTotal)
        chunked = total != C.LENGTH_UNSET.toLong()
        if (!chunked) return upstream.open(dataSpec)

        spec = dataSpec
        position = dataSpec.position
        endExclusive = total
        openChunk()
        return endExclusive - position
    }

    private fun probeTotal(dataSpec: DataSpec): Long? {
        val opened = try {
            upstream.open(rangeSpec(dataSpec, 0, 0))
            true
        } catch (_: Throwable) {
            false
        }
        return try {
            if (opened) {
                upstream.responseHeaders.entries
                    .firstOrNull { it.key.equals("content-range", ignoreCase = true) }
                    ?.value?.firstOrNull()
                    ?.let(::parseContentRange)
            } else null
        } finally {
            if (opened) {
                try { upstream.close() } catch (_: Throwable) { }
            }
        }
    }

    /** Opens the next bounded window at [position] via the `range=` param. */
    private fun openChunk(): Long {
        val windowEnd = minOf(position + CHUNK_BYTES, endExclusive) // exclusive
        return upstream.open(rangeSpec(spec!!, position, windowEnd - 1))
    }

    private fun rangeSpec(base: DataSpec, start: Long, endInclusive: Long): DataSpec {
        val uri = base.uri.buildUpon().clearQuery().apply {
            base.uri.queryParameterNames.filter { it != "range" }.forEach { name ->
                base.uri.getQueryParameters(name).forEach { appendQueryParameter(name, it) }
            }
            appendQueryParameter("range", "$start-$endInclusive")
        }.build()
        return base.buildUpon().setUri(uri).setPosition(0).setLength(C.LENGTH_UNSET.toLong()).build()
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

internal fun parseContentRange(value: String?): Long? {
    if (value == null) return null
    val parts = value.split(" ").filter { it.isNotEmpty() }
    if (parts.size < 2 || !parts[0].equals("bytes", ignoreCase = true)) return null
    val rangeSpec = parts[1]
    val slashIndex = rangeSpec.indexOf('/')
    if (slashIndex == -1 || slashIndex == rangeSpec.length - 1) return null
    val totalStr = rangeSpec.substring(slashIndex + 1)
    if (totalStr == "*") return null
    return totalStr.toLongOrNull()
}

internal fun resolveChunkTotal(isVideoPlayback: Boolean, explicitTotal: Long, probedTotal: Long?): Long {
    if (!isVideoPlayback) return C.LENGTH_UNSET.toLong()
    if (explicitTotal != C.LENGTH_UNSET.toLong()) return explicitTotal
    if (probedTotal != null && probedTotal != C.LENGTH_UNSET.toLong()) return probedTotal
    return C.LENGTH_UNSET.toLong()
}
