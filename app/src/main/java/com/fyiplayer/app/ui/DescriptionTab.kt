package com.fyiplayer.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.core.VideoDetail
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.player.PlaybackSession

/**
 * The Description tab. No fetch of its own -- [detail] is already in hand by the time a tab can
 * be selected. YouTube descriptions arrive as HTML (raw `<a>`/`<br>`/entities were printed
 * literally before this existed); [VideoDetail.descriptionIsHtml] tells us which renderer to use.
 * Either way, [linkifyGaps] catches URLs/handles/timestamps the renderer left as plain text --
 * `fromHtml` only ever links a run YouTube itself wrapped in `<a>`, and PLAIN_TEXT descriptions
 * (age-restricted videos) never had a chance to link anything at all.
 */
internal fun LazyListScope.descriptionTabSection(
    detail: VideoDetail,
    onOpenDetail: (VideoRef) -> Unit,
    onOpenListing: (Listing) -> Unit,
) {
    item {
        val description = detail.description
        if (description.isNullOrBlank()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No description",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            DescriptionText(detail, description, onOpenDetail, onOpenListing)
        }
    }
}

@Composable
private fun DescriptionText(
    detail: VideoDetail,
    description: String,
    onOpenDetail: (VideoRef) -> Unit,
    onOpenListing: (Listing) -> Unit,
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val linkStyles = TextLinkStyles(SpanStyle(color = linkColor))
    val listener = LinkInteractionListener { link ->
        (link as? LinkAnnotation.Url)?.url?.let { url ->
            handleDescriptionLink(url, detail.ref, context, onOpenDetail, onOpenListing)
        }
    }
    val base = if (detail.descriptionIsHtml) {
        AnnotatedString.fromHtml(description, linkStyles = linkStyles, linkInteractionListener = listener)
    } else {
        AnnotatedString(description)
    }
    SelectionContainer(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(linkifyGaps(base, detail.ref.pageUrl, linkStyles, listener), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Compose-side half of the gap-fill: turns each [findLinkSpans] hit that [base] doesn't already
 *  link into a real [LinkAnnotation.Url], styled/routed exactly like an `<a>` fromHtml parsed. */
private fun linkifyGaps(
    base: AnnotatedString,
    currentPageUrl: String,
    linkStyles: TextLinkStyles,
    listener: LinkInteractionListener,
): AnnotatedString {
    val builder = AnnotatedString.Builder(base)
    for (span in findLinkSpans(base.text, currentPageUrl)) {
        if (base.hasLinkAnnotations(span.start, span.end)) continue
        builder.addLink(LinkAnnotation.Url(span.target, linkStyles, listener), span.start, span.end)
    }
    return builder.toAnnotatedString()
}

/**
 * Routes a tapped link the same way the rest of the app routes any YouTube URL it's handed: a
 * timestamp on the video already playing seeks in place, another video opens Detail, a
 * channel/playlist opens its Listing (same [Listing] shape [isChannelPageUrl] builds in
 * `HomeScreen.kt`) -- everything else goes to whatever app the device has for it. No new nav
 * callback: this reuses exactly what [DetailScreen] already receives.
 */
private fun handleDescriptionLink(
    url: String,
    currentRef: VideoRef,
    context: Context,
    onOpenDetail: (VideoRef) -> Unit,
    onOpenListing: (Listing) -> Unit,
) {
    val videoId = youtubeVideoId(url)
    when {
        videoId != null && videoId == youtubeVideoId(currentRef.pageUrl) -> {
            queryParam(url, "t")?.let(::parseYoutubeTimestamp)?.let { PlaybackSession.seekTo(it * 1000L) }
        }
        // Minimal bare ref from a URL -- same shape DetailScreen falls back to when RefCache
        // misses (`ref` in DetailScreen()), since that's exactly what happens next: openDetail
        // navigates to detail/{pageUrl} and that route rebuilds a ref the same way on a miss.
        videoId != null -> onOpenDetail(
            VideoRef(sourceId = SourceRegistry.forUrl(url)?.id ?: "youtube", pageUrl = url, remoteId = url, title = ""),
        )
        isChannelPageUrl(url) -> onOpenListing(Listing(sourceId = "youtube", kind = Listing.Kind.CHANNEL, key = url, title = ""))
        isYoutubePlaylistUrl(url) -> onOpenListing(Listing(sourceId = "youtube", kind = Listing.Kind.PLAYLIST, key = url, title = ""))
        else -> try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            // No app on the device handles it -- quietly do nothing, never a crash for a
            // description link.
        }
    }
}

/** `youtube.com/watch?v=`, `youtu.be/`, or `/shorts/` -- the bare video id, or null for anything
 *  else (including channel/playlist URLs, matched separately). */
internal fun youtubeVideoId(url: String): String? {
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    val path = uri.path ?: ""
    return when {
        host == "youtu.be" -> path.trim('/').substringBefore('/').takeIf { it.isNotEmpty() }
        host.endsWith("youtube.com") && path == "/watch" -> queryParam(url, "v")
        host.endsWith("youtube.com") && path.startsWith("/shorts/") ->
            path.removePrefix("/shorts/").substringBefore('/').takeIf { it.isNotEmpty() }
        else -> null
    }
}

/** `?list=...` or `/playlist` -- the same playlist shape `NewPipeYoutubeSource.toPlaylistListing`
 *  keys a [Listing] on. */
internal fun isYoutubePlaylistUrl(url: String): Boolean {
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    if (!host.endsWith("youtube.com")) return false
    return uri.path == "/playlist" || uri.query?.split('&')?.any { it.startsWith("list=") } == true
}

private fun queryParam(url: String, name: String): String? {
    val query = runCatching { java.net.URI(url) }.getOrNull()?.query ?: return null
    return query.split('&').firstNotNullOfOrNull { pair ->
        val eq = pair.indexOf('=')
        if (eq < 0 || pair.substring(0, eq) != name) return@firstNotNullOfOrNull null
        val raw = pair.substring(eq + 1)
        runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }
}

/** YouTube's two `t=` shapes: pure seconds (`t=153`) or `1h2m3s` style (any subset of h/m/s, in
 *  order). Null for anything matching neither -- a malformed param is not a seek. */
internal fun parseYoutubeTimestamp(raw: String): Long? {
    if (raw.isEmpty()) return null
    raw.toLongOrNull()?.let { return it }
    val m = Regex("""^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?$""").matchEntire(raw) ?: return null
    val (h, min, s) = m.destructured
    if (h.isBlank() && min.isBlank() && s.isBlank()) return null
    return (h.toLongOrNull() ?: 0L) * 3600 + (min.toLongOrNull() ?: 0L) * 60 + (s.toLongOrNull() ?: 0L)
}

/** `1:02:03` / `2:05` as literally typed in a description -- not [parseYoutubeTimestamp]'s shape
 *  (that one parses a URL's `t=` value: bare seconds or the letter form, never colon-separated). */
internal fun parseColonTimestamp(raw: String): Long? {
    val parts = raw.split(':').map { it.toLongOrNull() ?: return null }
    return when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> null
    }
}

private fun timestampUrl(pageUrl: String, seconds: Long): String =
    pageUrl + (if ('?' in pageUrl) "&" else "?") + "t=${seconds}s"

/** One match candidate from [findLinkSpans]: the [start]/[end] text offsets and the URL a tap on
 *  that span should open -- already in the shape [handleDescriptionLink] expects. */
internal data class LinkSpan(val start: Int, val end: Int, val target: String)

private val TRAILING_PUNCT = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '\'', '"')

// Alternation order matters: the URL branch must win over the bare-domain branch wherever both
// could start matching (an "https://youtube.com/..." run), so it's listed first.
private val LINK_PATTERN = Regex(
    """https?://[^\s<>"']+""" +
        """|\b(?:www\.)?(?:youtube\.com|youtu\.be)/\S+""" +
        """|(?<!\w)@[A-Za-z0-9_.]{3,30}\b""" +
        """|\b(?:[01]?\d:)?[0-5]?\d:[0-5]\d\b""",
)

/** Every bare http(s) URL, scheme-less youtube.com/youtu.be link, `@handle` mention, and
 *  `mm:ss`/`h:mm:ss` timestamp in [text] -- the shapes a description renderer leaves as plain text
 *  when it didn't (or, on the PLAIN_TEXT path, never could) wrap them in a real link. Pure text
 *  scan, no knowledge of what the caller already linked -- that overlap check happens on the
 *  Compose side ([linkifyGaps]) where the existing annotations actually live. A trailing sentence
 *  character (`.`, `)`, ...) is trimmed off a URL match so the link target and the underlined span
 *  don't swallow it. [currentPageUrl] is [VideoRef.pageUrl] of the video this description belongs
 *  to -- a timestamp has no video of its own, so it seeks THIS one, same as a real `<a>` timestamp
 *  already does in [handleDescriptionLink].
 */
internal fun findLinkSpans(text: String, currentPageUrl: String): List<LinkSpan> {
    val spans = mutableListOf<LinkSpan>()
    for (match in LINK_PATTERN.findAll(text)) {
        val start = match.range.first
        // A trailing sentence character never belongs to any of these four shapes -- not even a
        // handle, whose charset otherwise allows '.'. Digits (timestamps) are unaffected.
        val value = match.value.trimEnd(*TRAILING_PUNCT)
        val end = match.range.first + value.length
        val target = when {
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.startsWith("@") -> "https://www.youtube.com/$value"
            value.contains(':') -> parseColonTimestamp(value)?.let { timestampUrl(currentPageUrl, it) }
            else -> "https://$value"
        } ?: continue
        spans += LinkSpan(start, end, target)
    }
    return spans
}
