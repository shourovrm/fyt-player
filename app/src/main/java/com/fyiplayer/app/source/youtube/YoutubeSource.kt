package com.fyiplayer.app.source.youtube

import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.SearchPage
import com.fyiplayer.app.core.SeekThumbnails
import com.fyiplayer.app.core.VideoDetail
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.core.VideoSource
import com.fyiplayer.app.engine.runEngine
import java.net.URI

private const val PAGE_SIZE = 20
private val HOSTS = setOf("youtube.com", "m.youtube.com", "www.youtube.com", "music.youtube.com", "youtu.be")
private const val TRENDING_URL = "https://www.youtube.com/feed/trending"

/**
 * YouTube adapter. Everything -- search, listings, detail, storyboards -- comes out of the
 * engine's own JSON; there is no markup parsing anywhere in this class.
 */
class YoutubeSource : VideoSource {
    override val id = "youtube"
    override val displayName = "YouTube"

    override fun matches(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return false
        return host in HOSTS
    }

    override suspend fun search(query: String, page: String?): SearchPage {
        val offset = page?.toIntOrNull() ?: 0
        val count = offset + PAGE_SIZE
        val out = runEngine("ytsearch$count:$query", "--flat-playlist", "-J", "--no-warnings")
        val all = parseFlatPlaylistJson(out)
        val slice = all.drop(offset)
        val nextPage = if (all.size >= count) count.toString() else null
        return SearchPage(items = slice, nextPage = nextPage)
    }

    override suspend fun homepage(page: String?): SearchPage = flatPlaylistPage(TRENDING_URL, page)

    // providesShorts stays false (default): the engine has no reliable generic shorts feed URL to
    // browse without live-site verification this task cannot perform. Honest gap over a fake feed.

    override suspend fun listing(listing: Listing, page: String?): SearchPage =
        flatPlaylistPage(listing.key, page)

    override suspend fun detail(ref: VideoRef): VideoDetail {
        val out = runEngine(ref.pageUrl, "-J", "--no-playlist", "--no-warnings")
        return parseDetailJson(out, ref)
    }

    override suspend fun seekThumbnails(ref: VideoRef): SeekThumbnails? {
        val out = runEngine(ref.pageUrl, "-J", "--no-playlist", "--no-warnings")
        return parseStoryboardsJson(out, ref.durationSeconds)
    }

    // MUST NOT fetch (per VideoSource contract): a listing row alone carries nothing a preview
    // strip could be built from, and this runs on the scroll path.
    override fun previewThumbnails(ref: VideoRef): SeekThumbnails? = null

    /** `--playlist-start`/`--playlist-end` page a real playlist/feed URL (1-indexed, inclusive) --
     *  unlike search's `ytsearchN:` prefix, these don't need a cumulative refetch per page. */
    private suspend fun flatPlaylistPage(url: String, page: String?): SearchPage {
        val offset = page?.toIntOrNull() ?: 0
        val out = runEngine(
            url,
            "--flat-playlist",
            "-J",
            "--no-warnings",
            "--playlist-start", (offset + 1).toString(),
            "--playlist-end", (offset + PAGE_SIZE).toString(),
        )
        val items = parseFlatPlaylistJson(out)
        val nextPage = if (items.size >= PAGE_SIZE) (offset + PAGE_SIZE).toString() else null
        return SearchPage(items = items, nextPage = nextPage)
    }
}
