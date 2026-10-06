package com.fyiplayer.app.data.backup

import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.VideoRef
import java.net.URI

/**
 * [VideoRef] <-> [BackupVideo]. Pure, no Android -- this is the one place export drops the fields
 * a backup must never carry (thumbnailUrl, uploaderUrl, viewCountText, remoteId), so the rest of
 * the pipeline (rendering, parsing) never even sees them.
 */
fun VideoRef.toBackupVideo(): BackupVideo = BackupVideo(
    sourceId = sourceId,
    pageUrl = pageUrl,
    title = title,
    durationSeconds = durationSeconds,
    uploader = uploader,
)

// remoteId isn't a persisted column anywhere in this app either -- every repo mapper reconstructs
// it as pageUrl (see data/repo), and playback always re-resolves from pageUrl regardless.
fun BackupVideo.toVideoRef(): VideoRef = VideoRef(
    sourceId = sourceId,
    pageUrl = pageUrl,
    remoteId = pageUrl,
    title = title,
    durationSeconds = durationSeconds,
    uploader = uploader,
)

// key is the channel's canonical page URL for a CHANNEL-kind Listing (see Listing docs in
// core/Contracts.kt) -- the only kind SubscriptionRepository ever produces here.
fun Listing.toBackupChannel(): BackupChannel = BackupChannel(sourceId, key, title)

/** Assembles the document from already-fetched lists. Pure -- callers do the DB reads. */
fun buildBackupDocument(
    liked: List<VideoRef>,
    playlists: List<Pair<String, List<VideoRef>>>,
    channels: List<Listing>,
    exportedAtMillis: Long,
): BackupDocument = BackupDocument(
    exportedAtMillis = exportedAtMillis,
    playlists = playlists.map { (name, items) -> BackupPlaylist(name, items.map { it.toBackupVideo() }) },
    liked = liked.map { it.toBackupVideo() },
    channels = channels.map { it.toBackupChannel() },
)

// What AppShell/DetailScreen give a shared-in URL that no registered source owns.
private const val UNOWNED_SOURCE_ID = ""

class SanitizedBackup(val document: BackupDocument, val droppedEntries: Int)

/**
 * A backup file is untrusted input: its URLs are later resolved, handed to the engine and loaded
 * in a JavaScript-enabled WebView. Videos must be https with a host -- the same bar a shared-in
 * link has to clear -- which keeps `javascript:`, `file:`, plain http and option-shaped strings
 * out. A video no registered source owns is kept with an empty source id, exactly what a shared
 * Facebook/TikTok/X link gets; requiring an owner would silently drop those on every import.
 * Channels only exist for registered sources, so an unowned channel is dropped. The source id
 * always comes from the owner, never from the file. [ownerSourceId] is
 * `SourceRegistry.forUrl(url)?.id` in the app; it is a parameter so this stays JVM-testable.
 */
fun sanitizeBackupDocument(doc: BackupDocument, ownerSourceId: (String) -> String?): SanitizedBackup {
    var dropped = 0

    fun isHttpsWithHost(url: String): Boolean {
        val parsed = runCatching { URI(url) }.getOrNull()
        return parsed?.scheme.equals("https", ignoreCase = true) && parsed?.host != null
    }

    fun acceptedVideos(videos: List<BackupVideo>): List<BackupVideo> = videos.mapNotNull { video ->
        if (!isHttpsWithHost(video.pageUrl)) {
            dropped++
            return@mapNotNull null
        }
        video.copy(sourceId = ownerSourceId(video.pageUrl) ?: UNOWNED_SOURCE_ID)
    }

    val playlists = doc.playlists.map { it.copy(items = acceptedVideos(it.items)) }
    val liked = acceptedVideos(doc.liked)
    val channels = doc.channels.mapNotNull { channel ->
        val owner = if (isHttpsWithHost(channel.channelUrl)) ownerSourceId(channel.channelUrl) else null
        if (owner == null) dropped++
        owner?.let { channel.copy(sourceId = it) }
    }
    return SanitizedBackup(doc.copy(playlists = playlists, liked = liked, channels = channels), dropped)
}
