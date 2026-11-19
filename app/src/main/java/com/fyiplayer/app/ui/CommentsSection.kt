package com.fyiplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.theme.ControlShape
import com.fyiplayer.app.ui.theme.NumeralStyle
import com.fyiplayer.app.ui.theme.Radius
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import com.fyiplayer.app.ui.theme.fyiExtras
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.fyiplayer.app.core.Comment
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.VideoRef

/**
 * The Comments tab's content. Fetching and error/retry state live in [DetailTabsViewModel] now
 * (fetch-once-per-video, survives tab switches and fullscreen) -- this composable is pure
 * presentation: threading, replies toggle, collapsing long text, honest empty/error states.
 *
 * Loading is driven by the caller selecting this tab, not a tap-to-load header inside it -- the
 * tab click itself is the "load" gesture now.
 */
@Composable
internal fun CommentsSection(
    comments: List<Comment>,
    loading: Boolean,
    error: ExtractionError?,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
    currentRef: VideoRef,
    onOpenDetail: (VideoRef) -> Unit,
    onOpenListing: (Listing) -> Unit,
    modifier: Modifier = Modifier,
) {
    val links = htmlLinkListener(currentRef, onOpenDetail, onOpenListing)
    Column(modifier.fillMaxWidth()) {
        when {
            loading && comments.isEmpty() -> Box(Modifier.fillMaxWidth().padding(Spacing.s24), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(Sizes.icon), strokeWidth = 2.dp)
            }
            // Access wall -> honest unavailable state; no retry affordance (never implies a workaround).
            error != null -> Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8)) {
                Text(error.userMessage(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (retryEnabled) {
                    TextButton(onClick = onRetry, shape = ControlShape) { Text(stringResource(R.string.action_retry)) }
                }
            }
            comments.isEmpty() -> Text(
                stringResource(R.string.detail_no_comments),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
            )
            else -> {
                val topLevel = comments.filter { it.parentId == null }
                val repliesByParent = comments.filter { it.parentId != null }.groupBy { it.parentId }
                topLevel.forEach { c -> CommentThread(c, repliesByParent[c.id].orEmpty(), links) }
            }
        }
    }
}

@Composable
private fun CommentThread(comment: Comment, replies: List<Comment>, links: LinkInteractionListener) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8)) {
        CommentRow(comment, Sizes.commentAvatar, links)
        if (replies.isNotEmpty()) {
            // Replies and their toggle line up with the comment's text, clear of its avatar.
            val textStart = Sizes.commentAvatar + Spacing.s8
            var expanded by remember(comment.id) { mutableStateOf(false) }
            TextButton(
                onClick = { expanded = !expanded },
                shape = ControlShape,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.fyiExtras.link),
                contentPadding = PaddingValues(vertical = 0.dp),
                modifier = Modifier.padding(start = textStart),
            ) {
                Text(
                    if (expanded) {
                        stringResource(R.string.detail_replies_hide)
                    } else {
                        pluralStringResource(R.plurals.detail_replies_show, replies.size, replies.size)
                    },
                )
            }
            if (expanded) {
                Column(Modifier.padding(start = textStart)) {
                    replies.forEach { r -> CommentRow(r, Spacing.s24, links) }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment, avatarSize: Dp, links: LinkInteractionListener) {
    var expanded by remember(comment.id) { mutableStateOf(false) }
    val body = if (comment.isHtml) {
        val linkStyles = TextLinkStyles(SpanStyle(color = MaterialTheme.fyiExtras.link))
        AnnotatedString.fromHtml(comment.text, linkStyles = linkStyles, linkInteractionListener = links)
    } else {
        AnnotatedString(comment.text)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        CommentAvatar(comment, avatarSize)
        CommentText(comment, body, expanded, links, onToggle = { expanded = !expanded })
    }
}

/** Round photo of the commenter. The first letter of the name sits under it, so the row is
 *  complete at once and the photo fills in when it has loaded. */
@Composable
private fun CommentAvatar(comment: Comment, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            comment.author.trimStart('@').take(1).uppercase(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        if (comment.authorAvatarUrl != null) {
            AsyncImage(
                model = comment.authorAvatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CommentText(
    comment: Comment,
    body: AnnotatedString,
    expanded: Boolean,
    links: LinkInteractionListener,
    onToggle: () -> Unit,
) {
    Column(Modifier.padding(start = Spacing.s8)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val uploaderPill = Modifier
                .clip(RoundedCornerShape(Radius.xs))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 4.dp, vertical = 1.dp)
            Text(
                comment.author,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                // The video's own channel: its name sits in a pill.
                modifier = if (comment.isUploader) uploaderPill else Modifier,
            )
            if (comment.timeText != null) {
                Text(
                    comment.timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        CommentBody(body, expanded, links, onToggle)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            if (comment.likeCount != null && comment.likeCount > 0) {
                val likes = pluralStringResource(R.plurals.detail_comment_likes, comment.likeCount.toInt(), comment.likeCount)
                Icon(
                    Icons.Filled.ThumbUp,
                    contentDescription = likes,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Sizes.iconSmall),
                )
                Text(
                    compactCount(comment.likeCount),
                    style = MaterialTheme.typography.labelSmall.merge(NumeralStyle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            if (comment.isHearted) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = stringResource(R.string.detail_comment_liked_by_channel),
                    tint = MaterialTheme.fyiExtras.icons.like,
                    modifier = Modifier.padding(start = 6.dp).size(Sizes.iconSmall),
                )
            }
        }
    }
}

/**
 * The comment text. Compose's built-in link tap area is the link's whole bounding box, which on a
 * wrapped link also covers plain text beside it, and links past the 3-line cut-off stay tappable.
 * So the links are drawn inert (colour only) and one tap handler decides: a tap exactly on a
 * link glyph opens it while expanded, anything else toggles the collapse.
 */
@Composable
private fun CommentBody(body: AnnotatedString, expanded: Boolean, links: LinkInteractionListener, onToggle: () -> Unit) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val linkColor = MaterialTheme.fyiExtras.link
    val shown = remember(body, linkColor) { withInertLinks(body, linkColor) }
    Text(
        shown,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (expanded) Int.MAX_VALUE else 3,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier = Modifier
            .padding(top = 2.dp)
            .pointerInput(body, expanded) {
                detectTapGestures { position ->
                    val link = if (expanded) layout?.let { linkAt(body, it, position) } else null
                    if (link == null) onToggle() else links.onClick(link)
                }
            }
            .semantics { onClick { onToggle(); true } },
    )
}

/** [body] with each link's range coloured and its tap annotation removed. */
private fun withInertLinks(body: AnnotatedString, linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        append(body.text)
        body.spanStyles.forEach { addStyle(it.item, it.start, it.end) }
        body.getLinkAnnotations(0, body.length).forEach { addStyle(SpanStyle(color = linkColor), it.start, it.end) }
    }

/** The link under [position], or null. The nearest-character lookup alone would also match blank
 *  space after a line's last character, so the tap must land inside that character's box. */
private fun linkAt(body: AnnotatedString, layout: TextLayoutResult, position: Offset): LinkAnnotation? {
    val offset = layout.getOffsetForPosition(position)
    if (offset >= body.length || !layout.getBoundingBox(offset).contains(position)) return null
    return body.getLinkAnnotations(offset, offset + 1).firstOrNull()?.item
}
