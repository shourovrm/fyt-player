package com.fyiplayer.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure render/parse round trip -- no Android, runs under testDebugUnitTest.
class BackupCodecTest {

    private fun sampleDoc() = BackupDocument(
        exportedAtMillis = 1_700_000_000_000L,
        playlists = listOf(
            BackupPlaylist(
                name = "Watch later",
                items = listOf(
                    BackupVideo("youtube", "https://example.com/watch?v=a", "Video A", 125, "Uploader A"),
                    BackupVideo("youtube", "https://example.com/watch?v=b", "Video B", null, null),
                ),
            ),
        ),
        liked = listOf(BackupVideo("youtube", "https://example.com/watch?v=c", "Video C", 10, "Uploader C")),
        channels = listOf(BackupChannel("youtube", "https://example.com/channel/d", "Channel D")),
    )

    @Test
    fun `export then parse round trip is lossless`() {
        val doc = sampleDoc()
        val parsed = parseBackupHtml(renderBackupHtml(doc))
        assertEquals(doc, parsed)
    }

    @Test
    fun `version field survives serialization despite sitting on its default`() {
        // Regression guard for the encodeDefaults trap (DESIGN.md §8): if that setting is ever
        // removed from the Json instance, `version` silently vanishes from the payload and this
        // fails both assertions below.
        val doc = sampleDoc() // version left on its default, never set explicitly
        val html = renderBackupHtml(doc)
        assertTrue("payload must carry an explicit version key", html.contains("\"version\":$BACKUP_VERSION"))
        assertEquals(BACKUP_VERSION, parseBackupHtml(html).version)
    }

    @Test
    fun `a file carrying an unknown future field still imports`() {
        val json = """{"version":1,"exportedAtMillis":100,"playlists":[],"liked":[],"history":[],""" +
            """"futureField":{"nested":"nonsense"}}"""
        val html = "<script id=\"fyi-player-backup\" type=\"application/json\">\n$json\n</script>"

        val doc = parseBackupHtml(html)

        assertEquals(1, doc.version)
        assertEquals(100L, doc.exportedAtMillis)
        assertTrue(doc.playlists.isEmpty())
    }

    @Test
    fun `a version-1 file carrying a history array parses with empty channels`() {
        // v1 never had `channels`; v2 dropped `history`. A real v1 export still has a `history`
        // array in it -- ignoreUnknownKeys must eat it rather than fail the whole import.
        val json = """{"version":1,"exportedAtMillis":100,"playlists":[],"liked":[],""" +
            """"history":[{"sourceId":"youtube","pageUrl":"https://example.com/v=z","title":"Z"}]}"""
        val html = "<script id=\"fyi-player-backup\" type=\"application/json\">\n$json\n</script>"

        val doc = parseBackupHtml(html)

        assertEquals(1, doc.version)
        assertTrue(doc.channels.isEmpty())
    }

    @Test
    fun `a title with closing script tag and html metacharacters round-trips`() {
        val nasty = """</script><b>&"'title"""
        val doc = BackupDocument(
            exportedAtMillis = 1L,
            liked = listOf(BackupVideo("youtube", "https://example.com/watch?v=x", nasty, null, nasty)),
        )
        val html = renderBackupHtml(doc)

        // Exactly one real closing </script> -- the embedded title must not have produced another.
        assertEquals(1, Regex("</script>").findAll(html).count())

        val parsed = parseBackupHtml(html)
        assertEquals(nasty, parsed.liked.single().title)
        assertEquals(nasty, parsed.liked.single().uploader)
    }

    @Test
    fun `title containing literal backslash-u003C text round-trips unchanged`() {
        val title = "\\u003C"
        val doc = BackupDocument(
            exportedAtMillis = 1L,
            liked = listOf(BackupVideo("youtube", "https://example.com/watch?v=x", title, null, null)),
        )
        val parsed = parseBackupHtml(renderBackupHtml(doc))
        assertEquals(title, parsed.liked.single().title)
    }

    @Test
    fun `title containing actual less-than character round-trips unchanged`() {
        val title = "a<b"
        val doc = BackupDocument(
            exportedAtMillis = 1L,
            liked = listOf(BackupVideo("youtube", "https://example.com/watch?v=x", title, null, null)),
        )
        val parsed = parseBackupHtml(renderBackupHtml(doc))
        assertEquals(title, parsed.liked.single().title)
    }
}
