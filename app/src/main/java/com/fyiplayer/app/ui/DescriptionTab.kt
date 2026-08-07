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
    SelectionContainer(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (detail.descriptionIsHtml) {
            Text(
                AnnotatedString.fromHtml(
                    description,
                    linkStyles = TextLinkStyles(SpanStyle(color = linkColor)),
                    linkInteractionListener = LinkInteractionListener { link ->
                        (link as? LinkAnnotation.Url)?.url?.let { url ->
                            handleDescriptionLink(url, detail.ref, context, onOpenDetail, onOpenListing)
                        }
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
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
