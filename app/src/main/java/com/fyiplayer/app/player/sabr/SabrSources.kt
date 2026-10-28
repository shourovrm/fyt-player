package com.fyiplayer.app.player.sabr

import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * videoId -> live [StreamInfo] for videos whose formats are SABR. Exists because a
 * [com.fyiplayer.app.core.MediaFormat] carries only a URL string, while the SABR source needs the
 * extractor's YoutubeSabrInfo handle (streaming URL, PO token, formats) hanging off the streams.
 *
 * Memory only: the info holds tokenised state, so it is never persisted or logged. Bounded to the
 * last [MAX_ENTRIES] videos (least recently used goes first).
 */
object SabrSources {
    private const val MAX_ENTRIES = 20
    private const val SCHEME = "sabr://"

    private val lock = Any()
    private val entries = object : LinkedHashMap<String, StreamInfo>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StreamInfo>): Boolean =
            size > MAX_ENTRIES
    }

    fun register(videoId: String, info: StreamInfo) {
        synchronized(lock) { entries[videoId] = info }
    }

    fun get(videoId: String): StreamInfo? = synchronized(lock) { entries[videoId] }

    /** The video id inside a "sabr://<id>" sentinel URL, or null for any other URL. */
    fun videoIdOf(url: String): String? =
        url.removePrefix(SCHEME).takeIf { url.startsWith(SCHEME) && it.isNotEmpty() }
}
