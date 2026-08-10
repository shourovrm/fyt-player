package com.fyiplayer.app.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.Protocol
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Direct-stream download path for sources the extractor (tier0) resolves: video-only + audio-only
 * parts fetched over the app's [OkHttpClient] with Range resume, then merged sample-by-sample with
 * [MediaMuxer] — no engine subprocess, no re-extraction, so it downloads exactly what the signed-in
 * extractor session can see. Signed URLs live only in memory for the duration of the run.
 *
 * Cancellation is cooperative via [CancelSignal]: pause/cancel flips the flag and aborts the live
 * HTTP call; a later resume re-resolves fresh URLs and continues from the `.part` sizes on disk.
 */
internal class StreamDownloader(private val client: OkHttpClient) {

    class CancelSignal {
        @Volatile var cancelled = false
            private set

        @Volatile internal var activeCall: Call? = null

        fun cancel() {
            cancelled = true
            activeCall?.cancel()
        }
    }

    sealed class Outcome {
        data class Done(val file: File) : Outcome()
        object Cancelled : Outcome()
        data class Failed(val message: String) : Outcome()
    }

    /**
     * [selector] is the stored engine-style format choice ("137+140" or a single id) from
     * [deriveDownloadOptions]; it is re-matched against the FRESH [formats] by formatId. For a
     * pair, the audio may be swapped for a mux-compatible one — the selector's audio was picked
     * by bitrate alone, and [MediaMuxer] cannot write e.g. opus into mp4.
     */
    suspend fun download(
        formats: List<MediaFormat>,
        selector: String,
        dir: File,
        baseName: String,
        signal: CancelSignal,
        onProgress: (DownloadProgress) -> Unit,
    ): Outcome = withContext(Dispatchers.IO) {
        try {
            val ids = selector.split('+')
            val first = formats.firstOrNull { it.formatId == ids[0] }
                ?: return@withContext Outcome.Failed("this quality is no longer offered — pick again")
            // A manifest URL is a playlist, not a media file: fetching it "succeeds" instantly
            // and writes an .m3u8/.mpd as the finished video. Only concrete streams download.
            if (first.protocol != Protocol.PROGRESSIVE) {
                return@withContext Outcome.Failed("this quality is no longer offered — pick again")
            }
            if (ids.size == 1) {
                downloadSingle(first, dir, baseName, signal, onProgress)
            } else {
                val audio = muxCompatibleAudio(first, formats, ids[1])
                    ?: return@withContext Outcome.Failed("no audio track can be merged with this video")
                downloadPair(first, audio, dir, baseName, signal, onProgress)
            }
        } catch (e: CancelledDownload) {
            Outcome.Cancelled
        } catch (e: IOException) {
            if (signal.cancelled) Outcome.Cancelled
            else Outcome.Failed("network error while downloading").also { logFailure(e) }
        } catch (e: Exception) {
            // Message never surfaced raw: it can echo a signed CDN URL.
            Outcome.Failed("download failed").also { logFailure(e) }
        }
    }

    // Class + frames only, never messages -- an exception message here can echo a signed CDN URL.
    private fun logFailure(e: Exception) {
        try {
            val frames = e.stackTrace.take(5).joinToString(" | ") {
                "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
            }
            android.util.Log.d("StreamDownloader", "failed: ${e::class.simpleName} @ $frames")
        } catch (logError: Throwable) {
            // unmocked android.util.Log under plain JUnit
        }
    }

    private class CancelledDownload : Exception()

    private fun downloadSingle(
        format: MediaFormat,
        dir: File,
        baseName: String,
        signal: CancelSignal,
        onProgress: (DownloadProgress) -> Unit,
    ): Outcome {
        val part = File(dir, "$baseName.dl.part")
        fetch(format, part, signal) { done, total -> onProgress(progressOf(done, total)) }
        val out = File(dir, "$baseName.${extensionOf(format)}")
        if (!part.renameTo(out)) return Outcome.Failed("could not write the finished file")
        return Outcome.Done(out)
    }

    private fun downloadPair(
        video: MediaFormat,
        audio: MediaFormat,
        dir: File,
        baseName: String,
        signal: CancelSignal,
        onProgress: (DownloadProgress) -> Unit,
    ): Outcome {
        val videoPart = File(dir, "$baseName.video.part")
        val audioPart = File(dir, "$baseName.audio.part")
        val videoBytes = video.filesizeBytes
        val audioBytes = audio.filesizeBytes
        val grandTotal = if (videoBytes != null && audioBytes != null) videoBytes + audioBytes else null

        fetch(video, videoPart, signal) { done, _ -> onProgress(progressOf(done, grandTotal)) }
        val videoDone = videoPart.length()
        fetch(audio, audioPart, signal) { done, _ -> onProgress(progressOf(videoDone + done, grandTotal)) }

        val webm = isWebmFamily(video)
        val out = File(dir, "$baseName.${if (webm) "webm" else "mp4"}")
        mux(videoPart, audioPart, out, webm, signal)
        videoPart.delete()
        audioPart.delete()
        return Outcome.Done(out)
    }

    /** Ranged, resumable fetch in 10 MB windows. googlevideo paces one long open request down to
     *  roughly realtime; the web player (and yt-dlp, via http_chunk_size) reads bounded ranges
     *  instead, and each window rides the client's rn/UA shaping (MediaHttp.kt). A server that
     *  ignores Range (HTTP 200) just streams the whole body through the first window. */
    private fun fetch(
        format: MediaFormat,
        part: File,
        signal: CancelSignal,
        onProgress: (Long, Long?) -> Unit,
    ) {
        val expected = format.filesizeBytes
        var offset = part.length()
        if (expected != null && offset >= expected && offset > 0) return // already finished
        var knownTotal = expected
        var lastTick = 0L

        while (true) {
            if (signal.cancelled) throw CancelledDownload()
            val end = offset + CHUNK_BYTES - 1
            // googlevideo windows ride the range= QUERY PARAM (official-client shape; header-ranged
            // requests from non-web clients intermittently 403 -- same rule as ChunkedRangeDataSource).
            // Other hosts keep the standard Range header.
            val googleRanged = android.net.Uri.parse(format.url).encodedPath
                ?.startsWith("/videoplayback") == true
            val url =
                if (googleRanged) {
                    format.url.toHttpUrl().newBuilder()
                        .removeAllQueryParameters("range")
                        .addQueryParameter("range", "$offset-$end").build().toString()
                } else {
                    format.url
                }
            val builder = Request.Builder().url(url)
            format.headers.forEach { (k, v) -> builder.header(k, v) }
            if (!googleRanged) builder.header("Range", "bytes=$offset-$end")

            val call = client.newCall(builder.build())
            signal.activeCall = call
            val windowStart = offset
            try {
                call.execute().use { resp ->
                    if (signal.cancelled) throw CancelledDownload()
                    if (resp.code == 416 && offset > 0) return // ranged past EOF: part is complete
                    if (!resp.isSuccessful) throw IOException("http ${resp.code}")
                    // range= param windows answer 200, not 206 -- still bounded windows
                    val ranged = resp.code == 206 || googleRanged
                    if (!ranged && offset > 0) {
                        // server ignored Range: the full body follows, start the part over
                        part.delete()
                        offset = 0
                    }
                    if (ranged && knownTotal == null) {
                        // Content-Range: bytes X-Y/TOTAL
                        knownTotal = resp.header("Content-Range")
                            ?.substringAfter('/')?.toLongOrNull()?.takeIf { it > 0 }
                    }
                    val body = resp.body ?: throw IOException("empty body")
                    java.io.FileOutputStream(part, offset > 0).buffered().use { sink ->
                        val source = body.byteStream()
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            if (signal.cancelled) throw CancelledDownload()
                            val n = source.read(buf)
                            if (n < 0) break
                            sink.write(buf, 0, n)
                            offset += n
                            val now = System.currentTimeMillis()
                            if (now - lastTick >= 500) {
                                lastTick = now
                                onProgress(offset, knownTotal)
                            }
                        }
                        sink.flush()
                    }
                    if (!ranged) {
                        onProgress(offset, knownTotal ?: offset)
                        return // whole body already streamed
                    }
                }
            } finally {
                signal.activeCall = null
            }
            onProgress(offset, knownTotal)
            val total = knownTotal
            if (total != null && offset >= total) return
            if (total == null && offset <= end) return // short read with unknown size: EOF
            // range= param past EOF answers 200 with an empty body, never 416 -- an empty window
            // with no known total is the end, not a reason to loop forever
            if (offset == windowStart) return
        }
    }

    /** Compressed-sample copy of two single-track inputs into one container. No re-encode. */
    private fun mux(videoIn: File, audioIn: File, out: File, webm: Boolean, signal: CancelSignal) {
        if (out.exists()) out.delete()
        val outputFormat = if (webm) {
            MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
        } else {
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        }
        val muxer = MediaMuxer(out.absolutePath, outputFormat)
        val extractors = listOf(videoIn, audioIn).map { file ->
            MediaExtractor().apply { setDataSource(file.absolutePath) }
        }
        try {
            val tracks = extractors.map { ex ->
                ex.selectTrack(0) // each input is one elementary stream
                muxer.addTrack(ex.getTrackFormat(0))
            }
            muxer.start()
            val buffer = ByteBuffer.allocateDirect(1 shl 20)
            val info = MediaCodec.BufferInfo()
            extractors.forEachIndexed { i, ex ->
                while (true) {
                    if (signal.cancelled) throw CancelledDownload()
                    val size = ex.readSampleData(buffer, 0)
                    if (size < 0) break
                    val flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    info.set(0, size, ex.sampleTime, flags)
                    muxer.writeSampleData(tracks[i], buffer, info)
                    ex.advance()
                }
            }
            muxer.stop()
        } finally {
            extractors.forEach { runCatching { it.release() } }
            runCatching { muxer.release() }
            if (signal.cancelled) out.delete() // never leave a half-muxed file behind
        }
    }
}

private const val CHUNK_BYTES = 10L * 1024 * 1024

private fun progressOf(done: Long, total: Long?): DownloadProgress {
    val percent = total?.takeIf { it > 0 }?.let { done * 100f / it }
    return DownloadProgress(percent, done, total, etaSeconds = null, speedBytesPerSecond = null)
}

private val MP4_VIDEO = Regex("^(avc|h264|hev|h265|hvc|av01|mp4v).*")
private val WEBM_VIDEO = Regex("^(vp8|vp9|vp09).*")

private fun isWebmFamily(video: MediaFormat): Boolean =
    WEBM_VIDEO.matches(video.videoCodec.orEmpty().lowercase())

/** mp4 output takes aac (mp4a); webm takes opus/vorbis. The selector's own audio wins when it
 *  already fits; otherwise the best-bitrate compatible audio-only stream is substituted. */
internal fun muxCompatibleAudio(
    video: MediaFormat,
    formats: List<MediaFormat>,
    selectorAudioId: String,
): MediaFormat? {
    val webm = isWebmFamily(video)
    fun compatible(f: MediaFormat): Boolean {
        val codec = f.audioCodec.orEmpty().lowercase()
        return if (webm) codec.startsWith("opus") || codec.startsWith("vorbis")
        else codec.startsWith("mp4a") || codec.startsWith("aac")
    }
    val chosen = formats.firstOrNull { it.formatId == selectorAudioId }
    if (chosen != null && chosen.isAudioOnly && compatible(chosen)) return chosen
    return formats.filter { it.isAudioOnly && compatible(it) }.maxByOrNull { it.bitrate ?: 0 }
}

private fun extensionOf(format: MediaFormat): String =
    format.container.ifBlank { if (format.isAudioOnly) "m4a" else "mp4" }
