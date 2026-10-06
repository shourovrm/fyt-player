package com.fyiplayer.app.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

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
 * query-style URLs only -- the FIRST real window's own `Content-Range` response header. There is
 * no separate probe request: visionos progressive URLs often carry no `clen` (see DECISIONS
 * Gotchas), so that first window would be sent either way -- reading the total off its response
 * instead of a throwaway `range=0-0` saves one full round trip before any real byte arrives. If
 * that response has no usable `Content-Range` (server ignored the range param), chunking is
 * abandoned and the request is replayed once as an open-ended passthrough -- never a wrong length.
 *
 * HLS SEGMENT URLs must never be chunked: googlevideo encodes their params in the PATH (no query
 * string), and rejects an appended `range=` query param with HTTP 400 (seen live) -- hence the
 * query-string gate below, checked before any window is opened.
 *
 * Non-/videoplayback URLs (subtitles, other hosts) pass through untouched.
 */
internal class ChunkedRangeDataSource(private val upstream: DataSource) : DataSource {

    private var spec: DataSpec? = null
    // Non-null only while a chunked read is open; null means plain passthrough.
    private var windowChain: WindowChain? = null

    override fun addTransferListener(transferListener: TransferListener) =
        upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        // Reset first: this instance is reused, and a stale chain from a previous chunked open
        // would otherwise capture reads of a plain passthrough one.
        windowChain = null
        if (!isChunkable(dataSpec.uri.encodedPath, dataSpec.uri.query)) return upstream.open(dataSpec)

        spec = dataSpec
        val position = dataSpec.position
        val total = explicitTotal(dataSpec.position, dataSpec.length, dataSpec.uri.getQueryParameter("clen"))

        if (total != C.LENGTH_UNSET.toLong()) {
            windowChain = newWindowChain(position, total)
            openChunk(position, total)
            return total - position
        }

        // Total unknown: the first window itself doubles as the probe -- open it and read the
        // total off its own Content-Range, no separate range=0-0 request first. Some URL shapes
        // 400 on a `range=` query (seen live); that failure must fall through to passthrough
        // exactly like the old probe did, never escape as a player error.
        val opened = try {
            upstream.open(rangeSpec(dataSpec, position, position + CHUNK_BYTES - 1))
            true
        } catch (_: Throwable) {
            false
        }
        val probedTotal = if (opened) contentRangeTotal() else null
        if (probedTotal == null) {
            // Server refused or ignored the range param -- abandon chunking, replay once as a
            // plain open-ended passthrough rather than ever report a wrong length.
            if (opened) try { upstream.close() } catch (_: Throwable) { }
            return upstream.open(dataSpec)
        }
        windowChain = newWindowChain(position, probedTotal)
        return probedTotal - position
    }

    private fun newWindowChain(startPosition: Long, endExclusive: Long) = WindowChain(
        startPosition = startPosition,
        endExclusive = endExclusive,
        readWindow = upstream::read,
        openNextWindow = { nextPosition ->
            upstream.close()
            openChunk(nextPosition, endExclusive)
        },
    )

    private fun contentRangeTotal(): Long? = upstream.responseHeaders.entries
        .firstOrNull { it.key.equals("content-range", ignoreCase = true) }
        ?.value?.firstOrNull()
        ?.let(::parseContentRange)

    /** Opens the next bounded window at [position] via the `range=` param. */
    private fun openChunk(position: Long, endExclusive: Long): Long {
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
        val chain = windowChain ?: return upstream.read(buffer, offset, length)
        return chain.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = upstream.uri ?: spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        spec = null
        windowChain = null
        upstream.close()
    }
}

/** HLS segment URLs are path-encoded (no query string) -- only query-style /videoplayback URLs
 *  are ever eligible for range-window chunking; an appended `range=` query 400s on the others. */
internal fun isChunkable(encodedPath: String?, query: String?): Boolean =
    encodedPath?.startsWith("/videoplayback") == true && !query.isNullOrEmpty()

/** ExoPlayer's own requested span wins over the URL's `clen` -- both describe the same total,
 *  and a caller-supplied length is the more authoritative of the two. */
internal fun explicitTotal(position: Long, length: Long, clenParam: String?): Long =
    if (length != C.LENGTH_UNSET.toLong()) position + length
    else clenParam?.toLongOrNull() ?: C.LENGTH_UNSET.toLong()

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

/**
 * Read side of the chunked source, free of Android types so the window-chaining rules are
 * unit-testable: reads through [readWindow] and, when a window ends before [endExclusive], asks
 * [openNextWindow] for the next one starting at the current position.
 */
internal class WindowChain(
    startPosition: Long,
    private val endExclusive: Long,
    private val readWindow: (ByteArray, Int, Int) -> Int,
    private val openNextWindow: (position: Long) -> Unit,
) {
    private var position = startPosition // absolute offset of the next byte to read

    fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        var reopenedWithoutProgress = false
        while (true) {
            val read = readWindow(buffer, offset, length)
            if (read != C.RESULT_END_OF_INPUT) {
                if (read > 0) position += read
                return read
            }
            if (position >= endExclusive) return C.RESULT_END_OF_INPUT
            // A reopened window that is empty again means the server will not serve this range.
            // Failing loudly lets the player's error path decide; retrying here would be a request
            // storm (and, recursively, a StackOverflowError).
            if (reopenedWithoutProgress) throw IOException("range window returned no data")
            reopenedWithoutProgress = true
            openNextWindow(position)
        }
    }
}
