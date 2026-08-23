package com.fyiplayer.app.data.backup

import kotlinx.serialization.json.Json

/**
 * Renders a [BackupDocument] as one HTML file -- a readable list plus the machine copy embedded
 * as JSON in a `<script type="application/json">` block -- and parses that file back. Pure: no
 * Android, no I/O, JVM-testable without a device (CLAUDE.md verification rule). SAF plumbing and
 * repository access live in BackupIo.kt, not here.
 */

// encodeDefaults=true: kotlinx.serialization otherwise omits any field sitting on its default,
// which would silently drop `version` from every export -- the newer-format gate in
// [parseBackupHtml] could then never fire. ignoreUnknownKeys=true so a file written by a future
// version with an extra field still imports on this one.
private val backupJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private const val DATA_OPEN = "<script id=\"fyi-player-backup\" type=\"application/json\">"
private const val DATA_CLOSE = "</script>"

/** Import refused the file. Message is user-facing; never contains a path or a URL. */
class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

fun renderBackupHtml(doc: BackupDocument): String {
    val payload = escapeForScript(backupJson.encodeToString(BackupDocument.serializer(), doc))
    return buildString {
        append("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
        append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n")
        append("<title>FYT Player library backup</title>\n")
        append("<style>").append(STYLE).append("</style>\n</head>\n<body>\n")
        append("<h1>FYT Player library backup</h1>\n")
        append("<p class=\"meta\">")
        append(doc.playlists.size).append(" playlists &middot; ")
        append(doc.playlists.sumOf { it.items.size }).append(" playlist videos &middot; ")
        append(doc.liked.size).append(" liked &middot; ")
        append(doc.history.size).append(" watch history</p>\n")
        append("<p class=\"note\">Import this file back into FYT Player (Settings &rarr; Backup) to restore it. ")
        append("No cookies, passcodes or thumbnail links are stored in this file.</p>\n")

        for (playlist in doc.playlists) {
            append("<h2>").append(escapeHtml(playlist.name)).append(" <span class=\"count\">")
            append(playlist.items.size).append("</span></h2>\n")
            appendVideos(playlist.items)
        }
        if (doc.liked.isNotEmpty()) {
            append("<h2>Liked <span class=\"count\">").append(doc.liked.size).append("</span></h2>\n")
            appendVideos(doc.liked)
        }
        if (doc.history.isNotEmpty()) {
            append("<h2>Watch history <span class=\"count\">").append(doc.history.size).append("</span></h2>\n")
            appendVideos(doc.history)
        }

        // Payload goes last so a large library doesn't push the readable part below the fold.
        append(DATA_OPEN).append('\n').append(payload).append('\n').append(DATA_CLOSE)
        append("\n</body>\n</html>\n")
    }
}

/**
 * Pulls the document back out of a rendered file, reading only the embedded payload -- the
 * visible HTML is for humans, editing it changes nothing on import. Throws [BackupFormatException]
 * on anything it cannot honour rather than half-importing.
 */
fun parseBackupHtml(html: String): BackupDocument {
    val start = html.indexOf(DATA_OPEN)
    if (start < 0) throw BackupFormatException("This isn't a FYT Player backup file.")
    val from = start + DATA_OPEN.length
    val end = html.indexOf(DATA_CLOSE, from)
    if (end < 0) throw BackupFormatException("This backup file is truncated.")
    val doc = try {
        backupJson.decodeFromString(BackupDocument.serializer(), html.substring(from, end))
    } catch (e: Exception) {
        throw BackupFormatException("This backup file is damaged.", e)
    }
    if (doc.version > BACKUP_VERSION) {
        throw BackupFormatException("This backup was made by a newer version of FYT Player.")
    }
    return doc
}

private fun StringBuilder.appendVideos(videos: List<BackupVideo>) {
    if (videos.isEmpty()) {
        append("<p class=\"empty\">Empty.</p>\n")
        return
    }
    append("<ol>\n")
    for (video in videos) {
        append("<li><a href=\"").append(escapeHtml(video.pageUrl)).append("\">")
        append(escapeHtml(video.title)).append("</a> <span class=\"meta\">")
        append(escapeHtml(video.sourceId))
        video.durationSeconds?.let {
            append(" &middot; ").append(it / 60).append(':').append((it % 60).toString().padStart(2, '0'))
        }
        video.uploader?.let { append(" &middot; ").append(escapeHtml(it)) }
        append("</span></li>\n")
    }
    append("</ol>\n")
}

/** Minimal HTML escaping, used for both text and attribute (href) positions. */
private fun escapeHtml(text: String): String = buildString(text.length) {
    for (c in text) when (c) {
        '&' -> append("&amp;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '"' -> append("&quot;")
        '\'' -> append("&#39;")
        else -> append(c)
    }
}

/**
 * A JSON string containing a literal `</script>` would close the block early, corrupting the
 * file (and, in a browser, escaping into markup). `<` is the only character that can start that
 * sequence, so a JSON unicode escape on it is enough to defuse it -- and the JSON is still valid,
 * so a reader that skips the un-escape step parses the same value anyway.
 */
private fun escapeForScript(payload: String): String = payload.replace("<", "\\u003C")

private const val STYLE = """
body{font:15px/1.5 system-ui,sans-serif;margin:0 auto;padding:24px;max-width:52rem;color:#16191c;background:#fff}
h1{font-size:1.5rem;margin:0 0 4px}
h2{font-size:1.05rem;margin:28px 0 6px;border-bottom:1px solid #e3e6e8;padding-bottom:4px}
.count{color:#8a9198;font-weight:400}
.meta{color:#6b7278;font-size:.85rem}
.note{color:#6b7278;font-size:.85rem;margin:0 0 8px}
.empty{color:#8a9198;font-style:italic}
ol{margin:0;padding-left:1.6rem}
li{margin:2px 0}
a{color:#1668c1;text-decoration:none}
a:hover{text-decoration:underline}
@media(prefers-color-scheme:dark){
body{background:#14171a;color:#e6e9ec}
h2{border-color:#2a2f34}
a{color:#6fb3ff}
}
"""
