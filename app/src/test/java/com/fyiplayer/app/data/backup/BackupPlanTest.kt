package com.fyiplayer.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure plan maths -- no Android, no Room. Simulates what BackupIo.apply would leave behind by
// folding the document's own pageUrls into the "existing" sets, since matching is by name/pageUrl
// only (never by row identity).
class BackupPlanTest {

    private fun sampleDoc() = BackupDocument(
        exportedAtMillis = 1L,
        playlists = listOf(
            BackupPlaylist(
                "Watch later",
                listOf(
                    BackupVideo("youtube", "https://example.com/v=a", "A"),
                    BackupVideo("youtube", "https://example.com/v=b", "B"),
                ),
            ),
        ),
        liked = listOf(BackupVideo("youtube", "https://example.com/v=c", "C")),
        channels = listOf(BackupChannel("youtube", "https://example.com/channel/d", "D")),
    )

    private fun snapshotAfterApplying(doc: BackupDocument): Triple<Map<String, Set<String>>, Set<String>, Set<String>> =
        Triple(
            doc.playlists.associate { it.name to it.items.map { v -> v.pageUrl }.toSet() },
            doc.liked.map { it.pageUrl }.toSet(),
            doc.channels.map { it.channelUrl }.toSet(),
        )

    @Test
    fun `first import against an empty library plans everything`() {
        val plan = planImport(sampleDoc(), emptyMap(), emptySet(), emptySet())
        assertEquals(BackupPlan(newPlaylists = 1, newPlaylistItems = 2, newLiked = 1, newChannels = 1), plan)
        assertTrue(!plan.isEmpty)
    }

    @Test
    fun `re-importing the same document after it was applied plans nothing`() {
        val doc = sampleDoc()
        val (playlistItems, liked, channels) = snapshotAfterApplying(doc)

        val second = planImport(doc, playlistItems, liked, channels)

        assertEquals(BackupPlan(0, 0, 0, 0), second)
        assertTrue(second.isEmpty)
    }

    @Test
    fun `applying twice yields the same plan as applying once`() {
        val doc = sampleDoc()
        val first = planImport(doc, emptyMap(), emptySet(), emptySet())
        val (playlistItems, liked, channels) = snapshotAfterApplying(doc)
        // "Applying once" already added everything `first` counted; a second run must add nothing
        // more, i.e. the library converges after one import no matter how many times it's replayed.
        val second = planImport(doc, playlistItems, liked, channels)
        val third = planImport(doc, playlistItems, liked, channels)

        assertTrue(!first.isEmpty)
        assertEquals(second, third)
        assertTrue(second.isEmpty)
    }

    @Test
    fun `partial overlap only counts what is actually new`() {
        val doc = sampleDoc()
        val existingPlaylistItems = mapOf("Watch later" to setOf("https://example.com/v=a")) // b is new
        val existingLiked = setOf("https://example.com/v=c") // already liked
        val existingChannels = emptySet<String>() // d is new

        val plan = planImport(doc, existingPlaylistItems, existingLiked, existingChannels)

        assertEquals(BackupPlan(newPlaylists = 0, newPlaylistItems = 1, newLiked = 0, newChannels = 1), plan)
    }
}
