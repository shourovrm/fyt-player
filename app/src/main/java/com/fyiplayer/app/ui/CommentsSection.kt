package com.fyiplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import com.fyiplayer.app.core.Comment
import com.fyiplayer.app.core.ExtractionError

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
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        when {
            loading && comments.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            // Access wall -> honest unavailable state; no retry affordance (never implies a workaround).
            error != null -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(error.userMessage(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (retryEnabled) {
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
            comments.isEmpty() -> Text(
                "No comments.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            else -> {
                val topLevel = comments.filter { it.parentId == null }
                val repliesByParent = comments.filter { it.parentId != null }.groupBy { it.parentId }
                topLevel.forEach { c -> CommentThread(c, repliesByParent[c.id].orEmpty()) }
            }
        }
    }
}

@Composable
private fun CommentThread(comment: Comment, replies: List<Comment>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        CommentRow(comment)
        if (replies.isNotEmpty()) {
            var expanded by remember(comment.id) { mutableStateOf(false) }
            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(vertical = 0.dp)) {
                Text(if (expanded) "Hide replies" else "${replies.size} replies")
            }
            if (expanded) {
                Column(Modifier.padding(start = 24.dp)) {
                    replies.forEach { r -> CommentRow(r) }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment) {
    var expanded by remember(comment.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(comment.author, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            if (comment.isUploader) {
                Text(
                    "Creator",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            if (comment.timeText != null) {
                Text(
                    comment.timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        Text(
            comment.text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp).clickable { expanded = !expanded },
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            if (comment.likeCount != null && comment.likeCount > 0) {
                Text(
                    "${comment.likeCount} likes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (comment.isHearted) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Liked by creator",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 6.dp).size(12.dp),
                )
            }
        }
    }
}
