package com.fyiplayer.app.data.backup

import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.VideoRef
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MOST IMPORTANT property of this package (CLAUDE.md): a backup file is something a user mails to
 * themselves, so it must never carry a signed/session URL. [VideoRef] is the only "video" input
 * type this app has, and thumbnailUrl/uploaderUrl are the URL-shaped fields on it that a listing
 * can populate with a signed or session-bearing link -- both are asserted absent from the
 * *rendered text*, not just missing from the model, because a lying field-list is not what ships.
 *
 * Cookies (CookieEntity) and resolved media URLs (MediaFormat.url) have no path into VideoRef or
 * BackupVideo at all -- there is no field to carry them through -- so the same two checks below
 * (thumbnail/session token absent, plus a case-insensitive "cookie" scan as a tripwire) cover the
 * whole surface this package can leak from.
 */
class BackupSecurityTest {

    private val sensitiveRef = VideoRef(
        sourceId = "youtube",
        pageUrl = "https://example.com/watch?v=abc123",
        remoteId = "abc123",
        title = "A normal video title",
        thumbnailUrl = "https://cdn.example.com/thumb.jpg?sig=SECRET_THUMB_TOKEN",
        durationSeconds = 42,
        uploader = "A normal uploader",
        uploaderUrl = "https://example.com/channel/abc?session=SECRET_SESSION_TOKEN",
        viewCountText = "1.2M views (ref=SECRET_VIEWCOUNT_TOKEN)",
    )

    private val sensitiveChannel = Listing(
        sourceId = "youtube",
        kind = Listing.Kind.CHANNEL,
        key = "https://example.com/channel/abc",
        title = "A normal channel title",
        thumbnailUrl = "https://cdn.example.com/avatar.jpg?sig=SECRET_AVATAR_TOKEN",
    )

    @Test
    fun `export drops thumbnail and channel-session urls before they reach the document`() {
        val backupVideo = sensitiveRef.toBackupVideo()

        assertFalse(backupVideo.toString().contains("SECRET_THUMB_TOKEN"))
        assertFalse(backupVideo.toString().contains("SECRET_SESSION_TOKEN"))
        assertFalse(backupVideo.toString().contains("SECRET_VIEWCOUNT_TOKEN"))
    }

    @Test
    fun `rendered export contains none of the sensitive urls from the source data`() {
        val doc = buildBackupDocument(
            liked = listOf(sensitiveRef),
            playlists = listOf("My playlist" to listOf(sensitiveRef)),
            channels = listOf(sensitiveChannel),
            exportedAtMillis = 1L,
        )

        val html = renderBackupHtml(doc)

        assertFalse(html.contains("SECRET_THUMB_TOKEN"))
        assertFalse(html.contains("SECRET_SESSION_TOKEN"))
        assertFalse(html.contains("SECRET_VIEWCOUNT_TOKEN"))
        assertFalse(html.contains("SECRET_AVATAR_TOKEN"))
        assertFalse(html.contains("cdn.example.com"))

        // Tripwire scoped to the machine payload: the page's own prose says in plain English that
        // no cookies are stored, so scanning the whole document for the word would only ever catch
        // that sentence. A real cookie leak would have to land in the data block.
        val payload = html.substringAfter("type=\"application/json\">").substringBefore("</script>")
        assertFalse(payload.lowercase().contains("cookie"))

        // What SHOULD be there: the canonical page/channel URLs and the display text.
        assertTrue(html.contains(sensitiveRef.pageUrl))
        assertTrue(html.contains(sensitiveRef.title))
        assertTrue(html.contains(sensitiveChannel.key))
        assertTrue(html.contains(sensitiveChannel.title))
    }
}
