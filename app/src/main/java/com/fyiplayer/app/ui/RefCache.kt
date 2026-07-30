package com.fyiplayer.app.ui

import com.fyiplayer.app.core.VideoRef

/**
 * Nav routes carry only a page URL (Navigation-Compose args must be simple/serializable strings),
 * but Detail wants the full [VideoRef] a list row already has -- title, thumbnail, duration -- to
 * paint its header before the network resolve completes. This hands that ref across the
 * `navigate()` call instead of smuggling it through the route string. Bounded LRU so a long
 * session doesn't leak; a miss (cold start, process death) just means Detail starts blank until
 * its own resolve lands, never a crash.
 */
object RefCache {
    private const val MAX_ENTRIES = 64

    private val map = object : LinkedHashMap<String, VideoRef>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VideoRef>) = size > MAX_ENTRIES
    }

    @Synchronized
    fun put(ref: VideoRef) {
        map[ref.pageUrl] = ref
    }

    @Synchronized
    fun get(pageUrl: String): VideoRef? = map[pageUrl]
}
