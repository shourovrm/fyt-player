package com.fyiplayer.app.download

import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.Protocol
import java.io.IOException

/** A non-2xx answer to a download window. Carries the status only: the message of the underlying
 *  failure can echo a signed URL, so nothing else is kept. */
internal class HttpStatusException(val statusCode: Int) : IOException("http $statusCode")

/** googlevideo answers 403 (or 410) once a signed URL ages out mid-file. 401 is left out: it is
 *  an auth refusal, not an expiry. */
private val EXPIRED_URL_STATUS_CODES = setOf(403, 410)

internal fun isExpiredUrlStatus(statusCode: Int): Boolean = statusCode in EXPIRED_URL_STATUS_CODES

/**
 * Whether a refused window is worth ONE re-resolve. Requires that this run's URLs already served
 * bytes: a 403 on the first request of a freshly resolved set is the platform refusing us (a wall),
 * not an expiry, and re-asking would be retrying through it. [alreadyRecovered] caps it at once per
 * run, so a fresh URL that is refused too fails honestly.
 */
internal fun shouldRecoverExpiredUrl(statusCode: Int, urlsServedBytes: Boolean, alreadyRecovered: Boolean): Boolean =
    isExpiredUrlStatus(statusCode) && urlsServedBytes && !alreadyRecovered

/**
 * The same stream in a freshly resolved list, or null if it cannot safely continue the part on
 * disk: matched by format id (the itag), progressive only, and rejected when both sides publish a
 * size and the sizes differ (the file changed, so the bytes already written belong to another one).
 */
internal fun sameFormatIn(fresh: List<MediaFormat>, old: MediaFormat): MediaFormat? {
    val candidate = fresh.firstOrNull { it.formatId == old.formatId && it.protocol == Protocol.PROGRESSIVE }
        ?: return null
    val oldSize = old.filesizeBytes
    val newSize = candidate.filesizeBytes
    if (oldSize != null && newSize != null && oldSize != newSize) return null
    return candidate
}

/**
 * The one URL recovery a stream download run gets. [reResolve] invalidates the resolver cache and
 * resolves again (null when that failed); it is supplied by DownloadQueue so extraction stays
 * behind the resolver seam.
 */
internal class UrlRecovery(private val reResolve: suspend () -> List<MediaFormat>?) {
    var used = false
        private set

    /** Set by the fetch loop once any window of the current URL set returned a success. */
    var urlsServedBytes = false

    private var freshFormats: List<MediaFormat>? = null

    /** The format to fetch from now on: the freshly resolved twin after a recovery, else [format]. */
    fun current(format: MediaFormat): MediaFormat =
        freshFormats?.let { sameFormatIn(it, format) } ?: format

    /** Spends the recovery. Null when the re-resolve failed or the format is gone. */
    suspend fun recover(format: MediaFormat): MediaFormat? {
        used = true
        val formats = reResolve() ?: return null
        freshFormats = formats
        urlsServedBytes = false
        return sameFormatIn(formats, format)
    }
}
