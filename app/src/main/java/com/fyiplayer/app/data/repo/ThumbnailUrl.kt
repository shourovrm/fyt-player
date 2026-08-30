package com.fyiplayer.app.data.repo

// Listing thumbnails carry a signature query (`?sqp=...&rs=...`) that expires; only the database
// column is stripped to the bare path here, never the in-memory VideoRef -- the unsigned path
// keeps rendering indefinitely and no signature/token is written to disk.
fun canonicalThumbnailUrl(url: String?): String? = url?.substringBefore('?')

// java.util.regex, not android.net.Uri: keeps this JVM-testable (AppShell.kt convention) and it's
// pure string work, safe to call per visible list item.
private val YT_HOST = Regex("""^https?://[^/]*\b(youtube\.com|youtu\.be)\b""")
private val YT_WATCH_ID = Regex("""[?&]v=([A-Za-z0-9_-]{6,})""")
private val YT_SHORTS_ID = Regex("""/shorts/([A-Za-z0-9_-]{6,})""")
private val YT_YOUTU_BE_ID = Regex("""youtu\.be/([A-Za-z0-9_-]{6,})""")

/** hqdefault never expires (unlike the signed i.ytimg.com URL a feed listing carries), so a
 *  Library row can always render a thumbnail even when only the bare pageUrl was ever stored. */
fun youtubeThumbnailFor(pageUrl: String): String? {
    val host = YT_HOST.find(pageUrl) ?: return null
    val id = when {
        host.groupValues[1] == "youtu.be" -> YT_YOUTU_BE_ID.find(pageUrl)
        pageUrl.contains("/shorts/") -> YT_SHORTS_ID.find(pageUrl)
        else -> YT_WATCH_ID.find(pageUrl)
    }?.groupValues?.get(1) ?: return null
    return "https://i.ytimg.com/vi/$id/hqdefault.jpg"
}
