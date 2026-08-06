package com.fyiplayer.app.ui

import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.VideoRef
import org.junit.Assert.assertEquals
import org.junit.Test

private fun ref(url: String) = VideoRef(sourceId = "yt", pageUrl = url, remoteId = url, title = url)

private fun listing(key: String) = Listing(sourceId = "yt", kind = Listing.Kind.PLAYLIST, key = key, title = key)

class LibrarySelectionTest {

    @Test fun `toggle adds then removes`() {
        val a = setOf("a")
        assertEquals(setOf("a", "b"), a.toggled("b"))
        assertEquals(emptySet<String>(), a.toggled("a"))
    }

    @Test fun `select all selects every item, again clears`() {
        val items = listOf(ref("a"), ref("b"), ref("c"))
        val all = selectAllOrNone(items, emptySet())
        assertEquals(setOf("a", "b", "c"), all)
        assertEquals(emptySet<String>(), selectAllOrNone(items, all))
    }

    @Test fun `select all with an empty list stays empty`() {
        assertEquals(emptySet<String>(), selectAllOrNone(emptyList(), emptySet()))
    }

    @Test fun `selected videos come back in display order, not selection order`() {
        val items = listOf(ref("a"), ref("b"), ref("c"))
        val selection = setOf("c", "a")
        assertEquals(listOf(ref("a"), ref("c")), selectedInOrder(items, selection))
    }

    @Test fun `move up shifts one slot earlier`() {
        val items = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), moved(items, from = 1, delta = -1))
    }

    @Test fun `move down shifts one slot later`() {
        val items = listOf("a", "b", "c")
        assertEquals(listOf("a", "c", "b"), moved(items, from = 1, delta = 1))
    }

    @Test fun `move past either edge is a no-op`() {
        val items = listOf("a", "b", "c")
        assertEquals(items, moved(items, from = 0, delta = -1))
        assertEquals(items, moved(items, from = 2, delta = 1))
    }

    @Test fun `merged playlist rows put locals before followed`() {
        val locals = listOf(PlaylistCard(1, "Local", 3, null))
        val followed = listOf(listing("https://example.com/playlist"))
        val rows = mergePlaylistRows(locals, followed)
        assertEquals(
            listOf(PlaylistRow.Local(locals[0]), PlaylistRow.Followed(followed[0])),
            rows,
        )
    }

    @Test fun `merged playlist rows key locals and followed in separate id spaces`() {
        val locals = listOf(PlaylistCard(1, "Local", 0, null))
        val followed = listOf(listing("1")) // deliberately colliding with the local id
        val rows = mergePlaylistRows(locals, followed)
        assertEquals(setOf("local:1", "followed:1"), rows.map { it.key }.toSet())
    }

    @Test fun `empty locals and followed merge to an empty list`() {
        assertEquals(emptyList<PlaylistRow>(), mergePlaylistRows(emptyList(), emptyList()))
    }
}
