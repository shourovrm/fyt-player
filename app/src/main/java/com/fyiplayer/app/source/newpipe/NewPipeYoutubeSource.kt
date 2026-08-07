package com.fyiplayer.app.source.newpipe

import com.fyiplayer.app.core.ChannelTab
import com.fyiplayer.app.core.Comment
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.ListingPage
import com.fyiplayer.app.core.SearchPage
import com.fyiplayer.app.core.SeekThumbnails
import com.fyiplayer.app.core.SpriteSheet
import com.fyiplayer.app.core.TAB_UNAVAILABLE_PREFIX
import com.fyiplayer.app.core.VideoDetail
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.core.VideoSource
import com.fyiplayer.app.source.youtube.YoutubeSource
import java.net.URI
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

private const val SOURCE_ID = "youtube"

// Mirrors YoutubeSource.matches's host set. Wider scope than NewPipeResolver's: this adapter
// claims every YouTube page shape (watch/shorts/channel/playlist/search), not just watch/shorts.
private val HOSTS = setOf("youtube.com", "m.youtube.com", "www.youtube.com", "music.youtube.com", "youtu.be")

/**
 * YouTube [VideoSource] backed by NewPipeExtractor -- replaces the yt-dlp-subprocess-backed
 * [YoutubeSource] for search, channel/playlist listings, detail, comments and seek thumbnails.
 * [id] stays `"youtube"`: subscriptions/history/likes rows and every persisted [VideoRef.pageUrl]
 * carry that sourceId and must keep resolving unchanged.
 *
 * [YoutubeSource] is kept as a private delegate for the one call NewPipeExtractor has no API for
 * -- channel-scoped search ([searchChannel]) -- and remains, independently, the download engine.
 */
class NewPipeYoutubeSource(
    // ponytail: a second OkHttpClient, distinct from FyiApp's NewPipeResolver client. Harmless:
    // NewPipeInit.ensure() is a one-shot global singleton init, so only whichever client calls it
    // FIRST is ever actually used by NewPipeExtractor -- this one goes idle if NewPipeResolver
    // wins the race (or vice versa). Worth wiring through DI only if that duplication ever matters.
    private val client: OkHttpClient = OkHttpClient(),
) : VideoSource {
    override val id = SOURCE_ID
    override val displayName = "YouTube"
    override val providesShorts = true
    override val providesComments = true

    private val delegate = YoutubeSource()

    override fun matches(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return false
        return host in HOSTS
    }

    override suspend fun search(query: String, page: String?): SearchPage = withContext(Dispatchers.IO) {
        NewPipeInit.ensure(client)
        guarded {
            val handler = ServiceList.YouTube.searchQHFactory.fromQuery(query, emptyList(), "")
            if (page == null) {
                val info = SearchInfo.getInfo(ServiceList.YouTube, handler)
                SearchPage(items = info.relatedItems.mapNotNull { it.toSearchVideoRef() }, nextPage = info.nextPage.tokenOrNull())
            } else {
                val more = SearchInfo.getMoreItems(ServiceList.YouTube, handler, page.toPage())
                SearchPage(items = more.items.mapNotNull { it.toSearchVideoRef() }, nextPage = more.nextPage.tokenOrNull())
            }
        }
    }

    override suspend fun channelTab(channelUrl: String, tab: ChannelTab, page: String?): SearchPage =
        withContext(Dispatchers.IO) {
            NewPipeInit.ensure(client)
            val filter = tab.contentFilter() ?: throw tabUnavailableError(tab)
            guarded {
                if (page == null) {
                    val handler = channelTabHandler(channelUrl, filter, tab)
                    val info = ChannelTabInfo.getInfo(ServiceList.YouTube, handler)
                    SearchPage(items = info.relatedItems.mapNotNull { it.toStreamVideoRef() }, nextPage = info.nextPage.tokenOrNull())
                } else {
                    val handler = ServiceList.YouTube.channelTabLHFactory.fromQuery(channelUrl, listOf(filter), "")
                    val more = ChannelTabInfo.getMoreItems(ServiceList.YouTube, handler, page.toPage())
                    SearchPage(items = more.items.mapNotNull { it.toStreamVideoRef() }, nextPage = more.nextPage.tokenOrNull())
                }
            }
        }

    override suspend fun channelContainers(channelUrl: String, tab: ChannelTab, page: String?): ListingPage =
        withContext(Dispatchers.IO) {
            NewPipeInit.ensure(client)
            // COURSES has no NewPipe equivalent (ChannelTabs has no such constant) -- yt-dlp still
            // supports it, so delegate whole. [page] here is only ever a token this same delegate
            // call minted (an integer offset, see YoutubeSource.channelContainers), so it round-trips
            // back into the delegate untouched -- no risk of feeding it a NewPipe Page token instead.
            if (tab == ChannelTab.COURSES) return@withContext delegate.channelContainers(channelUrl, tab, page)
            if (tab != ChannelTab.PLAYLISTS) throw tabUnavailableError(tab)
            guarded {
                if (page == null) {
                    val handler = channelTabHandler(channelUrl, ChannelTabs.PLAYLISTS, tab)
                    val info = ChannelTabInfo.getInfo(ServiceList.YouTube, handler)
                    ListingPage(items = info.relatedItems.filterIsInstance<PlaylistInfoItem>().mapNotNull { it.toPlaylistListing() }, nextPage = info.nextPage.tokenOrNull())
                } else {
                    val handler = ServiceList.YouTube.channelTabLHFactory.fromQuery(channelUrl, listOf(ChannelTabs.PLAYLISTS), "")
                    val more = ChannelTabInfo.getMoreItems(ServiceList.YouTube, handler, page.toPage())
                    ListingPage(items = more.items.filterIsInstance<PlaylistInfoItem>().mapNotNull { it.toPlaylistListing() }, nextPage = more.nextPage.tokenOrNull())
                }
            }
        }

    override suspend fun listing(listing: Listing, page: String?): SearchPage = when (listing.kind) {
        Listing.Kind.CHANNEL -> channelTab(listing.key, ChannelTab.VIDEOS, page)
        Listing.Kind.PLAYLIST -> withContext(Dispatchers.IO) {
            NewPipeInit.ensure(client)
            guarded {
                if (page == null) {
                    val info = PlaylistInfo.getInfo(ServiceList.YouTube, listing.key)
                    SearchPage(items = info.relatedItems.mapNotNull { it.toVideoRef() }, nextPage = info.nextPage.tokenOrNull())
                } else {
                    val more = PlaylistInfo.getMoreItems(ServiceList.YouTube, listing.key, page.toPage())
                    SearchPage(items = more.items.mapNotNull { it.toVideoRef() }, nextPage = more.nextPage.tokenOrNull())
                }
            }
        }
    }

    // NewPipeExtractor has no channel-scoped search API at all -- yt-dlp stays the only path.
    override suspend fun searchChannel(channelUrl: String, query: String, page: String?): SearchPage =
        delegate.searchChannel(channelUrl, query, page)

    override suspend fun detail(ref: VideoRef): VideoDetail = withContext(Dispatchers.IO) {
        NewPipeInit.ensure(client)
        val info = guarded { StreamInfo.getInfo(ServiceList.YouTube, ref.pageUrl) }
        val uploaderListing = info.uploaderUrl?.let {
            Listing(sourceId = SOURCE_ID, kind = Listing.Kind.CHANNEL, key = it, title = info.uploaderName ?: "")
        }
        val resolvedRef = ref.copy(
            title = info.name ?: ref.title,
            thumbnailUrl = info.thumbnails.lastOrNull()?.url ?: ref.thumbnailUrl,
            durationSeconds = info.duration.takeIf { it >= 0 }?.toInt() ?: ref.durationSeconds,
            uploader = info.uploaderName ?: ref.uploader,
            uploaderUrl = info.uploaderUrl ?: ref.uploaderUrl,
            viewCountText = compactCount(info.viewCount)?.let { "$it views" } ?: ref.viewCountText,
            uploadedText = info.textualUploadDate ?: ref.uploadedText,
        )
        VideoDetail(
            ref = resolvedRef,
            related = info.relatedItems.orEmpty().filterIsInstance<StreamInfoItem>().mapNotNull { it.toVideoRef() },
            uploader = uploaderListing,
            description = info.description?.content,
            // MARKDOWN or plain text both render as plain -- only HTML needs a parser, so that's the
            // only case worth flagging (honest degradation for MARKDOWN, not a lie).
            descriptionIsHtml = info.description?.type == Description.HTML,
            uploadDate = info.textualUploadDate,
            likeCount = info.likeCount.takeIf { it >= 0 },
            viewCount = info.viewCount.takeIf { it >= 0 },
        )
    }

    override suspend fun comments(ref: VideoRef, max: Int): List<Comment> = withContext(Dispatchers.IO) {
        NewPipeInit.ensure(client)
        guarded { fetchComments(ref.pageUrl, max) }
    }

    // Threading: top-level comments (parentId null) plus one page of replies per top-level comment
    // that has any -- NewPipe's reply Page is "load one page of replies", there is no single call
    // that returns a whole thread the way yt-dlp's extractor-args (max_comments=...,repliesPerThread)
    // did in one engine invocation. Paginates top-level pages until `max` is reached or the
    // extractor runs out; a reply fetch failure drops that thread's replies rather than the page.
    private fun fetchComments(videoUrl: String, max: Int): List<Comment> {
        val out = mutableListOf<Comment>()
        val info = CommentsInfo.getInfo(ServiceList.YouTube, videoUrl)
        var items: List<CommentsInfoItem> = info.relatedItems.orEmpty()
        var nextTopLevel: Page? = info.nextPage
        while (out.size < max) {
            for (item in items) {
                if (out.size >= max) break
                out += item.toComment(parentId = null)
                val repliesPage = item.replies
                if (repliesPage != null && out.size < max) {
                    val replies = runCatching { CommentsInfo.getMoreItems(ServiceList.YouTube, videoUrl, repliesPage) }.getOrNull()
                    replies?.items.orEmpty().forEach { reply ->
                        if (out.size < max) out += reply.toComment(parentId = item.commentId)
                    }
                }
            }
            val np = nextTopLevel
            if (out.size >= max || np == null || !Page.isValid(np)) break
            val more = CommentsInfo.getMoreItems(ServiceList.YouTube, videoUrl, np)
            items = more.items
            nextTopLevel = more.nextPage
        }
        return out
    }

    override suspend fun seekThumbnails(ref: VideoRef): SeekThumbnails? = withContext(Dispatchers.IO) {
        NewPipeInit.ensure(client)
        val info = guarded { StreamInfo.getInfo(ServiceList.YouTube, ref.pageUrl) }
        val best = info.previewFrames.orEmpty().maxByOrNull { it.totalCount } ?: return@withContext null
        val sprites = best.urls.map { url ->
            SpriteSheet(
                url = url,
                cols = best.framesPerPageX,
                rows = best.framesPerPageY,
                tileWidth = best.frameWidth,
                tileHeight = best.frameHeight,
                count = best.framesPerPageX * best.framesPerPageY,
            )
        }
        SeekThumbnails(intervalSeconds = best.durationPerFrame / 1000.0, sprites = sprites)
    }

    // previewThumbnails(ref): left at the VideoSource default (null, no I/O) -- there is nothing
    // in a VideoRef alone to build a preview strip from, per the interface's no-I/O contract.
    // homepage(): left at the default Unsupported throw -- Home is subscription-built, see DECISIONS.md.

    /** Structural tab-availability check: [ChannelInfo.getTabs] only ever lists tabs the channel
     *  actually has, so an absent [filter] IS "no such tab" -- no text-sniffing an error message. */
    private fun channelTabHandler(channelUrl: String, filter: String, tab: ChannelTab): ListLinkHandler {
        val channelInfo = ChannelInfo.getInfo(ServiceList.YouTube, channelUrl)
        return channelInfo.tabs.find { filter in it.contentFilters } ?: throw tabUnavailableError(tab)
    }
}

private inline fun <T> guarded(block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: ExtractionError) {
    throw e
} catch (e: Exception) {
    throw mapNewPipeError(e)
}

private fun Page?.tokenOrNull(): String? = this?.takeIf { Page.isValid(it) }?.toToken()

private fun ChannelTab.contentFilter(): String? = when (this) {
    ChannelTab.VIDEOS -> ChannelTabs.VIDEOS
    ChannelTab.SHORTS -> ChannelTabs.SHORTS
    ChannelTab.LIVE -> ChannelTabs.LIVESTREAMS
    ChannelTab.PLAYLISTS -> ChannelTabs.PLAYLISTS
    // Guards channelTab() (video entries) only; channelContainers() delegates COURSES to yt-dlp.
    ChannelTab.COURSES -> null
}

/** Own message, never raw platform text (that could echo the channel URL) -- same convention as
 *  YoutubeSource.tabUnavailableError, duplicated here since the two sources are in different
 *  packages and neither owns the other's internals. */
private fun tabUnavailableError(tab: ChannelTab): ExtractionError.Unsupported {
    val name = when (tab) {
        ChannelTab.VIDEOS -> "videos"
        ChannelTab.SHORTS -> "shorts"
        ChannelTab.PLAYLISTS -> "playlists"
        ChannelTab.COURSES -> "courses"
        ChannelTab.LIVE -> "live"
    }
    return ExtractionError.Unsupported("$TAB_UNAVAILABLE_PREFIX this channel has no $name tab")
}

private fun InfoItem.toSearchVideoRef(): VideoRef? = when (this) {
    is StreamInfoItem -> toVideoRef()
    is ChannelInfoItem -> toChannelRef()
    else -> null // PlaylistInfoItem and anything else: no VideoRef shape fits a mixed search hit
}

private fun InfoItem.toStreamVideoRef(): VideoRef? = (this as? StreamInfoItem)?.toVideoRef()

/** [YoutubeSource]'s convention, kept exactly: remoteId is the bare video id, pageUrl is always
 *  the canonical watch URL -- never NewPipe's raw item.url, which can be a /shorts/ path. */
private fun StreamInfoItem.toVideoRef(): VideoRef? {
    val videoId = runCatching { ServiceList.YouTube.streamLHFactory.getId(url) }.getOrNull() ?: return null
    return VideoRef(
        sourceId = SOURCE_ID,
        pageUrl = "https://www.youtube.com/watch?v=$videoId",
        remoteId = videoId,
        title = name,
        thumbnailUrl = thumbnails.lastOrNull()?.url,
        durationSeconds = duration.takeIf { it >= 0 }?.toInt(),
        uploader = uploaderName,
        uploaderUrl = uploaderUrl,
        viewCountText = compactCount(viewCount)?.let { "$it views" },
        uploadedText = textualUploadDate,
    )
}

// A channel has no video id of its own -- pageUrl IS the identity, same remoteId=pageUrl fallback
// already used for entities that were never a video (see Contracts.kt Gotchas in DECISIONS.md).
private fun ChannelInfoItem.toChannelRef(): VideoRef = VideoRef(
    sourceId = SOURCE_ID,
    pageUrl = url,
    remoteId = url,
    title = name,
    thumbnailUrl = thumbnails.lastOrNull()?.url,
    viewCountText = compactCount(subscriberCount)?.let { "$it subscribers" },
)

private fun PlaylistInfoItem.toPlaylistListing(): Listing =
    Listing(sourceId = SOURCE_ID, kind = Listing.Kind.PLAYLIST, key = url, title = name, thumbnailUrl = thumbnails.lastOrNull()?.url)

private fun CommentsInfoItem.toComment(parentId: String?): Comment = Comment(
    id = commentId,
    author = uploaderName ?: "",
    text = commentText?.content ?: "",
    likeCount = likeCount.takeIf { it != CommentsInfoItem.NO_LIKE_COUNT }?.toLong(),
    timeText = textualUploadDate,
    parentId = parentId,
    isUploader = isChannelOwner,
    isHearted = isHeartedByUploader,
    authorAvatarUrl = uploaderAvatars.lastOrNull()?.url,
)

/** 1_234_567 -> "1.2M". Negative (NewPipe's "unknown count" sentinel is -1) -> null, never
 *  invented. No ".0" suffix on a round number, matching YouTube's own compacting. */
internal fun compactCount(count: Long): String? {
    if (count < 0) return null
    if (count < 1000) return count.toString()
    val (divisor, suffix) = when {
        count < 1_000_000 -> 1_000.0 to "K"
        count < 1_000_000_000 -> 1_000_000.0 to "M"
        else -> 1_000_000_000.0 to "B"
    }
    val tenths = (count / divisor * 10).roundToInt()
    val text = if (tenths % 10 == 0) (tenths / 10).toString() else "%.1f".format(Locale.US, tenths / 10.0)
    return "$text$suffix"
}
