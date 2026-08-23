package com.fyiplayer.app.player

import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheKeyFactory
import java.security.MessageDigest

/**
 * [SimpleCache][androidx.media3.datasource.cache.SimpleCache] writes its keys into an on-disk
 * index, so the key must never be (or leak) the signed media URL itself -- signed/tokenised URLs
 * are memory-only, never persisted (CLAUDE.md).
 *
 * googlevideo `/videoplayback`: `id`+`itag`+`lmt` describe the same encoded bytes across
 * re-resolves (a fresh sig/n doesn't change `lmt`, the source file's last-modified time), so that
 * triple is a stable key with no signature in it. Everything else (Facebook/TikTok/X CDN URLs,
 * subtitles) gets a one-way SHA-256 hash of the full URL -- a hash is not the URL.
 *
 * Must sit OUTSIDE [ChunkedRangeDataSource] in the chain (see [MediaItemFactory]): this factory
 * has to see the caller's original [DataSpec], not one already rewritten with a `range=` window,
 * or every chunk would key differently and the cache could never assemble one whole file.
 */
internal object FyiCacheKeyFactory : CacheKeyFactory {
    override fun buildCacheKey(dataSpec: DataSpec): String {
        val uri = dataSpec.uri
        return cacheKeyFor(
            uri.encodedPath,
            uri.toString(),
            uri.getQueryParameter("id"),
            uri.getQueryParameter("itag"),
            uri.getQueryParameter("lmt"),
        )
    }
}

// Pulled out of buildCacheKey as a pure function (primitives only, no android.net.Uri) so it's
// unit-testable without Robolectric -- see ChunkedRangeDataSourceTest's note on the same issue.
internal fun cacheKeyFor(encodedPath: String?, fullUrl: String, id: String?, itag: String?, lmt: String?): String {
    if (encodedPath?.startsWith("/videoplayback") == true && id != null && itag != null && lmt != null) {
        return "yt|$id|$itag|$lmt"
    }
    return sha256Hex(fullUrl)
}

// ByteArray.toHexString() (stable since Kotlin 2.0) -- NOT "%02x".format(byte): Java's Formatter
// sign-extends a negative Byte before hexing it, so half the digest bytes would print >2 chars.
private fun sha256Hex(input: String): String =
    MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).toHexString()
