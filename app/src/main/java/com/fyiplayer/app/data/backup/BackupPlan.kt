package com.fyiplayer.app.data.backup

/** What importing a document would add, given what's already saved. Shown to the user before
 *  anything is written (DESIGN.md §7). Pure -- see BackupIo.kt for the DB reads that build the
 *  arguments to [planImport]. */
data class BackupPlan(
    val newPlaylists: Int,
    val newPlaylistItems: Int,
    val newLiked: Int,
    val newChannels: Int,
    /** Entries the file held that import refuses (URL not https or not owned by a source). */
    val droppedEntries: Int = 0,
) {
    val isEmpty: Boolean get() = newPlaylists == 0 && newPlaylistItems == 0 && newLiked == 0 && newChannels == 0
}

/** One playlist an import writes. Same-name playlists in the file are already merged into one. */
class PlaylistWrite(val name: String, val isNew: Boolean, val items: List<BackupVideo>)

/** Exactly what an import writes -- both [planImport] and BackupIo.apply read this, so the counts
 *  shown to the user cannot drift from what is written. [likes] and [channels] keep file order. */
class ImportWrites(
    val playlists: List<PlaylistWrite>,
    val likes: List<BackupVideo>,
    val channels: List<BackupChannel>,
)

fun planImportWrites(
    doc: BackupDocument,
    existingPlaylistItems: Map<String, Set<String>>, // only playlists that already exist are keys
    existingLiked: Set<String>,
    existingChannels: Set<String>,
): ImportWrites {
    // "Match playlists by name" must hold inside the file too: a second playlist of the same name
    // is more items for the first, not a second playlist.
    val itemsByName = LinkedHashMap<String, MutableList<BackupVideo>>()
    for (playlist in doc.playlists) itemsByName.getOrPut(playlist.name) { mutableListOf() }.addAll(playlist.items)

    val playlistWrites = itemsByName.map { (name, items) ->
        val have = existingPlaylistItems[name]
        PlaylistWrite(
            name = name,
            isNew = have == null,
            items = items.distinctBy { it.pageUrl }.filter { it.pageUrl !in (have ?: emptySet()) },
        )
    }
    return ImportWrites(
        playlists = playlistWrites,
        likes = doc.liked.distinctBy { it.pageUrl }.filter { it.pageUrl !in existingLiked },
        channels = doc.channels.distinctBy { it.channelUrl }.filter { it.channelUrl !in existingChannels },
    )
}

/**
 * Import is additive and idempotent: playlists match by name, videos by pageUrl, channels by
 * channelUrl, and nothing already present is ever counted (or, in BackupIo.apply, written)
 * again. Running [planImport] against the state that a previous import already produced always
 * plans zero.
 */
fun planImport(
    doc: BackupDocument,
    existingPlaylistItems: Map<String, Set<String>>, // playlist name -> pageUrls already in it
    existingLiked: Set<String>,
    existingChannels: Set<String>, // channelUrls already subscribed
): BackupPlan {
    val writes = planImportWrites(doc, existingPlaylistItems, existingLiked, existingChannels)
    return BackupPlan(
        newPlaylists = writes.playlists.count { it.isNew },
        newPlaylistItems = writes.playlists.sumOf { it.items.size },
        newLiked = writes.likes.size,
        newChannels = writes.channels.size,
    )
}

/**
 * Timestamp for the like at [indexNewestFirst] among the likes an import writes. The list is shown
 * by likedAt DESC and the file is exported newest-first, so counting back one millisecond per
 * position keeps the exported order; stamping every like with "now" would reverse it or tie.
 */
fun likedAtForImport(indexNewestFirst: Int, nowMillis: Long): Long = nowMillis - indexNewestFirst
