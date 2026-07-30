package com.fyiplayer.app.data.backup

import com.fyiplayer.app.core.VideoRef

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

/** Assembles the document from already-fetched lists. Pure -- callers do the DB reads. */
fun buildBackupDocument(
    liked: List<VideoRef>,
    playlists: List<Pair<String, List<VideoRef>>>,
    history: List<VideoRef>,
    exportedAtMillis: Long,
): BackupDocument = BackupDocument(
    exportedAtMillis = exportedAtMillis,
    playlists = playlists.map { (name, items) -> BackupPlaylist(name, items.map { it.toBackupVideo() }) },
    liked = liked.map { it.toBackupVideo() },
    history = history.map { it.toBackupVideo() },
)
