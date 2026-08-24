package com.fyiplayer.app.data.backup

/** What importing a document would add, given what's already saved. Shown to the user before
 *  anything is written (DESIGN.md §7). Pure -- see BackupIo.kt for the DB reads that build the
 *  arguments to [planImport]. */
data class BackupPlan(
    val newPlaylists: Int,
    val newPlaylistItems: Int,
    val newLiked: Int,
    val newChannels: Int,
) {
    val isEmpty: Boolean get() = newPlaylists == 0 && newPlaylistItems == 0 && newLiked == 0 && newChannels == 0
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
    var newPlaylists = 0
    var newItems = 0
    for (playlist in doc.playlists) {
        val have = existingPlaylistItems[playlist.name]
        if (have == null) newPlaylists++
        newItems += playlist.items.count { it.pageUrl !in (have ?: emptySet()) }
    }
    return BackupPlan(
        newPlaylists = newPlaylists,
        newPlaylistItems = newItems,
        newLiked = doc.liked.count { it.pageUrl !in existingLiked },
        newChannels = doc.channels.count { it.channelUrl !in existingChannels },
    )
}
