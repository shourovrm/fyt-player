package com.fyiplayer.app.data.backup

import android.content.Context
import android.net.Uri
import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.data.repo.LikesRepository
import com.fyiplayer.app.data.repo.PlaylistRepository
import com.fyiplayer.app.data.repo.SubscriptionRepository
import kotlinx.coroutines.flow.first

private fun ownerSourceId(url: String): String? = SourceRegistry.forUrl(url)?.id

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
        subscriptions: SubscriptionRepository,
    ) {
        val playlistPairs = playlists.observePlaylists().first()
            .map { it.name to playlists.observeItems(it.id).first() }
        val doc = buildBackupDocument(
            liked = likes.observe().first(),
            playlists = playlistPairs,
            channels = subscriptions.observeAllRows().first().map { it.listing },
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
        subscriptions: SubscriptionRepository,
    ): Pair<BackupDocument, BackupPlan> {
        val html = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw BackupFormatException("Could not open the chosen file for reading.")
        val sanitized = sanitizeBackupDocument(parseBackupHtml(html), ::ownerSourceId)
        val doc = sanitized.document
        val existing = readExisting(doc, likes, playlists, subscriptions)
        val plan = planImport(doc, existing.playlistItems, existing.liked, existing.channels)
            .copy(droppedEntries = sanitized.droppedEntries)
        return doc to plan
    }

    /** Performs the writes [preview] planned. Never deletes; skips anything already present, which
     *  is what makes re-applying the same document a no-op the second time. */
    suspend fun apply(
        doc: BackupDocument,
        likes: LikesRepository,
        playlists: PlaylistRepository,
        subscriptions: SubscriptionRepository,
    ): BackupPlan {
        // Sanitized again: apply is public and must not trust that its caller went through preview.
        val sanitized = sanitizeBackupDocument(doc, ::ownerSourceId)
        val safeDoc = sanitized.document
        val existing = readExisting(safeDoc, likes, playlists, subscriptions)
        val writes = planImportWrites(safeDoc, existing.playlistItems, existing.liked, existing.channels)

        for (playlist in writes.playlists) {
            val id = existing.playlistIdByName[playlist.name] ?: playlists.create(playlist.name)
            for (item in playlist.items) playlists.addItem(id, item.toVideoRef())
        }

        val importedAtMillis = System.currentTimeMillis()
        writes.likes.forEachIndexed { index, item ->
            likes.like(item.toVideoRef(), likedAt = likedAtForImport(index, importedAtMillis))
        }

        for (channel in writes.channels) subscriptions.subscribe(channel.channelUrl, channel.sourceId, channel.title)

        return planImport(safeDoc, existing.playlistItems, existing.liked, existing.channels)
            .copy(droppedEntries = sanitized.droppedEntries)
    }

    private class Existing(
        val playlistIdByName: Map<String, Long>,
        val playlistItems: Map<String, Set<String>>,
        val liked: Set<String>,
        val channels: Set<String>,
    )

    private suspend fun readExisting(
        doc: BackupDocument,
        likes: LikesRepository,
        playlists: PlaylistRepository,
        subscriptions: SubscriptionRepository,
    ): Existing {
        val idByName = playlists.observePlaylists().first().associate { it.name to it.id }
        // Only playlists that exist are keys: planImportWrites reads a missing key as "create it".
        val items = mutableMapOf<String, Set<String>>()
        for (name in doc.playlists.map { it.name }.distinct()) {
            val id = idByName[name] ?: continue
            items[name] = playlists.observeItems(id).first().map { it.pageUrl }.toSet()
        }
        return Existing(
            playlistIdByName = idByName,
            playlistItems = items,
            liked = likes.observe().first().map { it.pageUrl }.toSet(),
            channels = subscriptions.observeAllRows().first().map { it.listing.key }.toSet(),
        )
    }
}
