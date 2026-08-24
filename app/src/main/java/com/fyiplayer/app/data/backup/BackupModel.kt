package com.fyiplayer.app.data.backup

import kotlinx.serialization.Serializable

/** Bumped only on a breaking change to this shape; [parseBackupHtml] refuses anything newer.
 *  v2 dropped watch history (never belonged in a file a user mails to themselves) and added
 *  subscribed channels; `ignoreUnknownKeys`/default-`channels` on the Json config let a v1 file
 *  still parse -- its `history` array is silently ignored, see BackupCodec.kt. */
const val BACKUP_VERSION = 2

/**
 * The whole exported/imported document. Deliberately not a database dump: playlists, likes and
 * subscribed channels are the only tables involved -- cookies, playback positions and watch
 * history never flow through this package at all.
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
    val channels: List<BackupChannel> = emptyList(),
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

/** A subscribed channel stripped to what a backup may hold: canonical channel page URL and
 *  display text -- no avatar URL, which (like a thumbnail) can be signed. */
@Serializable
data class BackupChannel(
    val sourceId: String,
    val channelUrl: String,
    val title: String,
)
