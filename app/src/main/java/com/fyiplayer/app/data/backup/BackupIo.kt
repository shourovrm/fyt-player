package com.fyiplayer.app.data.backup

import android.content.Context
import android.net.Uri
import com.fyiplayer.app.data.repo.HistoryRepository
import com.fyiplayer.app.data.repo.LikesRepository
import com.fyiplayer.app.data.repo.PlaylistRepository
import kotlinx.coroutines.flow.first

/**
 * Everything that touches Android or Room for backup: reading the current library into a
 * [BackupDocument], writing/reading the file through a SAF [Uri], and applying an imported
 * document back into the repositories. The model, HTML rendering/parsing and plan maths are pure
 * (BackupModel/Mapping/Codec/Plan.kt) and stay JVM-testable without this file.
 */
object BackupIo {

    suspend fun export(
        context: Context,
        uri: Uri,
        likes: LikesRepository,
        playlists: PlaylistRepository,
        history: HistoryRepository,
    ) {
        val playlistPairs = playlists.observePlaylists().first()
            .map { it.name to playlists.observeItems(it.id).first() }
        val doc = buildBackupDocument(
            liked = likes.observe().first(),
            playlists = playlistPairs,
            history = history.observe().first(),
            exportedAtMillis = System.currentTimeMillis(),
        )
        val html = renderBackupHtml(doc)
        context.contentResolver.openOutputStream(uri)?.use { it.write(html.toByteArray(Charsets.UTF_8)) }
            ?: throw BackupFormatException("Could not open the chosen file for writing.")
    }

    /** Reads and parses only; writes nothing. Caller shows the [BackupPlan] before calling [apply]. */
    suspend fun preview(
        context: Context,
        uri: Uri,
        likes: LikesRepository,
        playlists: PlaylistRepository,
        history: HistoryRepository,
    ): Pair<BackupDocument, BackupPlan> {
        val html = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw BackupFormatException("Could not open the chosen file for reading.")
        val doc = parseBackupHtml(html)
        val existing = readExisting(doc, likes, playlists, history)
        val plan = planImport(doc, existing.playlistItems, existing.liked, existing.history)
        return doc to plan
    }

    /** Performs the writes [preview] planned. Never deletes; skips anything already present, which
     *  is what makes re-applying the same document a no-op the second time. */
    suspend fun apply(
        doc: BackupDocument,
        likes: LikesRepository,
        playlists: PlaylistRepository,
        history: HistoryRepository,
    ): BackupPlan {
        val existing = readExisting(doc, likes, playlists, history)

        var newPlaylists = 0
        var newItems = 0
        for (playlist in doc.playlists) {
            val id = existing.playlistIdByName[playlist.name] ?: playlists.create(playlist.name).also { newPlaylists++ }
            val have = existing.playlistItems[playlist.name] ?: emptySet()
            for (item in playlist.items) {
                if (item.pageUrl !in have) {
                    playlists.addItem(id, item.toVideoRef())
                    newItems++
                }
            }
        }

        var newLiked = 0
        for (item in doc.liked) if (item.pageUrl !in existing.liked) { likes.like(item.toVideoRef()); newLiked++ }

        var newHistory = 0
        for (item in doc.history) if (item.pageUrl !in existing.history) { history.record(item.toVideoRef()); newHistory++ }

        return BackupPlan(newPlaylists, newItems, newLiked, newHistory)
    }

    private class Existing(
        val playlistIdByName: Map<String, Long>,
        val playlistItems: Map<String, Set<String>>,
        val liked: Set<String>,
        val history: Set<String>,
    )

    private suspend fun readExisting(
        doc: BackupDocument,
        likes: LikesRepository,
        playlists: PlaylistRepository,
        history: HistoryRepository,
    ): Existing {
        val idByName = playlists.observePlaylists().first().associate { it.name to it.id }
        val items = doc.playlists.associate { p ->
            val id = idByName[p.name]
            p.name to (if (id != null) playlists.observeItems(id).first().map { it.pageUrl }.toSet() else emptySet())
        }
        return Existing(
            playlistIdByName = idByName,
            playlistItems = items,
            liked = likes.observe().first().map { it.pageUrl }.toSet(),
            history = history.observe().first().map { it.pageUrl }.toSet(),
        )
    }
}
