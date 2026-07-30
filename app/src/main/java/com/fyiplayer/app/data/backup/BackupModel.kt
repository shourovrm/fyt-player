package com.fyiplayer.app.data.backup

import kotlinx.serialization.Serializable

/** Bumped only on a breaking change to this shape; [parseBackupHtml] refuses anything newer. */
const val BACKUP_VERSION = 1

/**
 * The whole exported/imported document. Deliberately not a database dump: watch history is here
 * because DESIGN.md §7 asks for it, but likes/playlists/history are the only tables involved --
 * cookies and playback positions never flow through this package at all.
 *
 * [version] defaults to [BACKUP_VERSION] and must still be written on every export -- see the
 * `encodeDefaults` note on the serializer in BackupCodec.kt.
 */
@Serializable
data class BackupDocument(
    val version: Int = BACKUP_VERSION,
    val exportedAtMillis: Long,
    val playlists: List<BackupPlaylist> = emptyList(),
    val liked: List<BackupVideo> = emptyList(),
    val history: List<BackupVideo> = emptyList(),
)

@Serializable
data class BackupPlaylist(
    val name: String,
    val items: List<BackupVideo> = emptyList(),
)

/**
 * A video stripped to what a backup may hold: canonical page URL, display text and duration.
 * No thumbnail URL, no uploader channel URL, no view-count text -- none of those are needed to
 * re-resolve or re-list the video, and a thumbnail URL is often signed (CLAUDE.md).
 */
@Serializable
data class BackupVideo(
    val sourceId: String,
    val pageUrl: String,
    val title: String,
    val durationSeconds: Int? = null,
    val uploader: String? = null,
)
