package com.fyiplayer.app.source.youtube

import com.fyiplayer.app.core.ChannelTab
import com.fyiplayer.app.core.Comment
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.ListingPage
import com.fyiplayer.app.core.SearchPage
import com.fyiplayer.app.core.SeekThumbnails
import com.fyiplayer.app.core.TAB_UNAVAILABLE_PREFIX
import com.fyiplayer.app.core.VideoDetail
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.core.VideoSource
import com.fyiplayer.app.engine.runEngine
import java.net.URI
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException

private const val PAGE_SIZE = 20
private val HOSTS = setOf("youtube.com", "m.youtube.com", "www.youtube.com", "music.youtube.com", "youtu.be")
private const val RELATED_LIMIT = 12
// yt-dlp extractor-args shape: max_comments=<total>,<max-parents>,<max-replies>,<replies-per-thread>.
private const val REPLIES_PER_THREAD = 20

// Path segments this adapter itself appends -- stripped off first so re-tabbing a URL that
// already carries one (e.g. detail()'s "$channelUrl/videos" for related videos) doesn't double up.
private val CHANNEL_TAB_SEGMENTS = setOf("videos", "shorts", "playlists", "courses", "streams", "search")

private fun ChannelTab.pathSegment(): String = when (this) {
    ChannelTab.VIDEOS -> "videos"
    ChannelTab.SHORTS -> "shorts"
    ChannelTab.PLAYLISTS -> "playlists"
    ChannelTab.COURSES -> "courses"
    ChannelTab.LIVE -> "streams" // verified live: /streams is the URL, "streams tab" is the error text
}

/** Strips a trailing slash and any tab segment this adapter already appended. */
internal fun channelBaseUrl(channelUrl: String): String {
    val trimmed = channelUrl.trimEnd('/')
    val last = trimmed.substringAfterLast('/')
    return if (last in CHANNEL_TAB_SEGMENTS) trimmed.substringBeforeLast('/') else trimmed
}

internal fun channelTabUrl(channelUrl: String, tab: ChannelTab): String =
    "${channelBaseUrl(channelUrl)}/${tab.pathSegment()}"

internal fun channelSearchUrl(channelUrl: String, query: String): String =
    "${channelBaseUrl(channelUrl)}/search?query=${URLEncoder.encode(query, "UTF-8")}"

// Engine text is "This channel does not have a <tab> tab" (verified live for courses/streams).
// mapEngineError funnels anything it doesn't otherwise classify into Unsupported and keeps the
// original message on the cause -- that's what this matches against, never the redacted message.
private val NO_SUCH_TAB = Regex("""does not have a .+ tab""")

internal fun isTabUnavailable(e: ExtractionError): Boolean {
    val raw = (e as? ExtractionError.Unsupported)?.cause?.message ?: return false
    return NO_SUCH_TAB.containsMatchIn(raw.lowercase())
}

/** Own message, never the engine's raw text -- that text can echo the channel URL. */
internal fun tabUnavailableError(tab: ChannelTab): ExtractionError.Unsupported =
    ExtractionError.Unsupported("$TAB_UNAVAILABLE_PREFIX this channel has no ${tab.pathSegment()} tab")

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

    // YouTube pulled its public trending/explore feeds -- both now redirect to the home page and
    // the engine reports the playlist as gone. There is no browsable page-level feed left to
    // request, so this is an honest gap, not a probe-and-catch: Home composes its own feed from
    // watched channels' listings instead (see HomeViewModel), which is composition, not extraction.
    override suspend fun homepage(page: String?): SearchPage =
        throw ExtractionError.Unsupported("YouTube no longer publishes a public trending feed")

    // providesShorts stays false (default) and shorts() stays Unsupported (default): YouTube
    // publishes no global shorts feed to back a page-level shorts() call (verified live -- a
    // per-channel shorts *tab* works, see channelTab(url, ChannelTab.SHORTS) below, but that's a
    // different contract with a channel to scope it). The Shorts screen composes its feed from
    // channelTab(SHORTS) across subscribed channels instead (ui/ShortsViewModel.kt), the same way
    // Home composes its feed from watch history rather than a source-level homepage() call.

    override suspend fun listing(listing: Listing, page: String?): SearchPage =
        flatPlaylistPage(listing.key, page)

    override suspend fun channelTab(channelUrl: String, tab: ChannelTab, page: String?): SearchPage =
        try {
            flatPlaylistPage(channelTabUrl(channelUrl, tab), page)
        } catch (e: ExtractionError.Unsupported) {
            throw if (isTabUnavailable(e)) tabUnavailableError(tab) else e
        }

    override suspend fun channelContainers(channelUrl: String, tab: ChannelTab, page: String?): ListingPage {
        val offset = page?.toIntOrNull() ?: 0
        val out = try {
            runEngine(
                channelTabUrl(channelUrl, tab),
                "--flat-playlist", "-J", "--no-warnings",
                "--playlist-start", (offset + 1).toString(),
                "--playlist-end", (offset + PAGE_SIZE).toString(),
            )
        } catch (e: ExtractionError.Unsupported) {
            throw if (isTabUnavailable(e)) tabUnavailableError(tab) else e
        }
        val items = parseChannelPlaylistsJson(out, id)
        val nextPage = if (items.size >= PAGE_SIZE) (offset + PAGE_SIZE).toString() else null
        return ListingPage(items = items, nextPage = nextPage)
    }

    override suspend fun searchChannel(channelUrl: String, query: String, page: String?): SearchPage =
        flatPlaylistPage(channelSearchUrl(channelUrl, query), page)

    override suspend fun detail(ref: VideoRef): VideoDetail {
        val out = runEngine(ref.pageUrl, "-J", "--no-playlist", "--no-warnings")
        val info = parseDetailJson(out, ref)
        val channelUrl = info.uploader?.key ?: return info
        // Related videos aren't available from the engine (see parseDetailJson's doc); "more from
        // this channel" is real data instead, one extra engine call, never a fabricated list.
        val related = try {
            val uploadsOut = runEngine(
                "${channelUrl.trimEnd('/')}/videos",
                "--flat-playlist", "-J", "--no-warnings",
                "--playlist-end", (RELATED_LIMIT + 1).toString(),
            )
            parseChannelUploadsForRelated(uploadsOut, info.ref.pageUrl, RELATED_LIMIT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        return info.copy(related = related)
    }

    override val providesComments = true

    override suspend fun comments(ref: VideoRef, max: Int): List<Comment> {
        val out = runEngine(
            ref.pageUrl,
            "-J", "--write-comments", "--no-playlist", "--no-warnings",
            "--extractor-args", "youtube:comment_sort=top;max_comments=$max,all,all,$REPLIES_PER_THREAD",
        )
        return parseCommentsJson(out)
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
