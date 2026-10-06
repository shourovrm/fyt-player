package com.fyiplayer.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure import rules: which entries a document may write (URL validation), which of those are
// new, and the like timestamps. No Android, no Room.
class BackupImportRulesTest {

    private fun video(url: String, title: String = "t", sourceId: String = "youtube") =
        BackupVideo(sourceId, url, title)

    private fun playlist(name: String, vararg urls: String) =
        BackupPlaylist(name, urls.map { video(it) })

    private fun doc(
        playlists: List<BackupPlaylist> = emptyList(),
        liked: List<BackupVideo> = emptyList(),
        channels: List<BackupChannel> = emptyList(),
    ) = BackupDocument(exportedAtMillis = 1L, playlists = playlists, liked = liked, channels = channels)

    // Stand-in for SourceRegistry.forUrl: one source that owns youtube.com.
    private val ownerOf: (String) -> String? = { url ->
        if (url.contains("://www.youtube.com/") || url.contains("://youtu.be/")) "youtube" else null
    }

    // ---- URL validation ----

    @Test
    fun `sanitize keeps https urls a source owns`() {
        val input = doc(
            playlists = listOf(playlist("P", "https://www.youtube.com/watch?v=a")),
            liked = listOf(video("https://youtu.be/b")),
            channels = listOf(BackupChannel("youtube", "https://www.youtube.com/channel/c", "C")),
        )
        val result = sanitizeBackupDocument(input, ownerOf)
        assertEquals(input, result.document)
        assertEquals(0, result.droppedEntries)
    }

    @Test
    fun `sanitize drops http, option-shaped and non-url strings and unowned channels and counts them`() {
        val input = doc(
            playlists = listOf(
                playlist(
                    "P",
                    "http://www.youtube.com/watch?v=a",
                    "-J",
                    "https://www.youtube.com/watch?v=ok",
                ),
            ),
            liked = listOf(video("javascript:alert(1)"), video("file:///etc/passwd")),
            channels = listOf(BackupChannel("youtube", "https://evil.example/c", "C")),
        )
        val result = sanitizeBackupDocument(input, ownerOf)
        assertEquals(listOf("https://www.youtube.com/watch?v=ok"), result.document.playlists.single().items.map { it.pageUrl })
        assertTrue(result.document.liked.isEmpty())
        assertTrue(result.document.channels.isEmpty())
        assertEquals(2 + 2 + 1, result.droppedEntries)
    }

    @Test
    fun `sanitize takes the source id from the owning source not from the file`() {
        val result = sanitizeBackupDocument(
            doc(liked = listOf(video("https://youtu.be/b", sourceId = "../../anything"))),
            ownerOf,
        )
        assertEquals("youtube", result.document.liked.single().sourceId)
    }

    @Test
    fun `sanitize keeps an unowned https video with an empty source id`() {
        val result = sanitizeBackupDocument(
            doc(liked = listOf(video("https://www.facebook.com/watch?v=1", sourceId = "anything"))),
            ownerOf,
        )
        assertEquals("", result.document.liked.single().sourceId)
        assertEquals(0, result.droppedEntries)
    }

    @Test
    fun `sanitize keeps a playlist whose items were all dropped`() {
        val result = sanitizeBackupDocument(doc(playlists = listOf(playlist("P", "http://evil.example/x"))), ownerOf)
        assertEquals("P", result.document.playlists.single().name)
        assertEquals(1, result.droppedEntries)
    }

    // ---- merge of same-name playlists and plan/apply agreement ----

    @Test
    fun `second playlist with the same name merges into the first`() {
        val input = doc(
            playlists = listOf(playlist("Mix", "https://x/a", "https://x/b"), playlist("Mix", "https://x/b", "https://x/c")),
        )
        val writes = planImportWrites(input, emptyMap(), emptySet(), emptySet())

        val mix = writes.playlists.single()
        assertTrue(mix.isNew)
        assertEquals(listOf("https://x/a", "https://x/b", "https://x/c"), mix.items.map { it.pageUrl })
        assertEquals(BackupPlan(newPlaylists = 1, newPlaylistItems = 3, newLiked = 0, newChannels = 0), planImport(input, emptyMap(), emptySet(), emptySet()))
    }

    @Test
    fun `same-name playlists merge into an existing playlist without creating one`() {
        val input = doc(playlists = listOf(playlist("Mix", "https://x/a"), playlist("Mix", "https://x/b")))
        val plan = planImport(input, mapOf("Mix" to setOf("https://x/a")), emptySet(), emptySet())
        assertEquals(BackupPlan(newPlaylists = 0, newPlaylistItems = 1, newLiked = 0, newChannels = 0), plan)
    }

    @Test
    fun `duplicates inside the file are counted once for likes and channels`() {
        val channel = BackupChannel("youtube", "https://x/ch", "C")
        val input = doc(liked = listOf(video("https://x/a"), video("https://x/a")), channels = listOf(channel, channel))
        val plan = planImport(input, emptyMap(), emptySet(), emptySet())
        assertEquals(1, plan.newLiked)
        assertEquals(1, plan.newChannels)
    }

    // ---- like order ----

    @Test
    fun `imported likes keep the exported newest-first order under likedAt DESC`() {
        val exported = listOf("https://x/newest", "https://x/middle", "https://x/oldest")
        val writes = planImportWrites(doc(liked = exported.map { video(it) }), emptyMap(), emptySet(), emptySet())

        val stamped = writes.likes.mapIndexed { index, like -> like.pageUrl to likedAtForImport(index, nowMillis = 1_000_000L) }
        val listedByLikedAtDescending = stamped.sortedByDescending { it.second }.map { it.first }

        assertEquals(exported, listedByLikedAtDescending)
        assertEquals(stamped.size, stamped.map { it.second }.toSet().size) // strictly distinct
    }

    @Test
    fun `re-import writes no likes so existing likedAt values are never touched`() {
        val input = doc(liked = listOf(video("https://x/a"), video("https://x/b")))
        val writes = planImportWrites(input, emptyMap(), setOf("https://x/a", "https://x/b"), emptySet())
        assertTrue(writes.likes.isEmpty())
    }
}
