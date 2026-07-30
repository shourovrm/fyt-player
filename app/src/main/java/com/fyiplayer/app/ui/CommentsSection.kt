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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.core.VideoSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface CommentsState {
    data object NotLoaded : CommentsState
    data object Loading : CommentsState
    data class Loaded(val comments: List<Comment>) : CommentsState
    data class Failed(val error: ExtractionError) : CommentsState
}

/**
 * Below the metadata, never with it: comment fetch is its own slow engine call (see
 * [VideoSource.comments]'s doc), so it only starts on tap -- never blocks the rest of the page,
 * and a video with comments disabled is just an empty [CommentsState.Loaded], not an error.
 *
 * Renders nothing when [source] doesn't support comments at all, same "honest gap, no empty
 * header" rule as the related-videos section.
 */
@Composable
internal fun CommentsSection(source: VideoSource?, ref: VideoRef, modifier: Modifier = Modifier) {
    if (source == null || !source.providesComments) return

    var state by remember(ref.pageUrl) { mutableStateOf<CommentsState>(CommentsState.NotLoaded) }
    val scope = rememberCoroutineScope()

    fun load() {
        state = CommentsState.Loading
        scope.launch {
            state = try {
                CommentsState.Loaded(source.comments(ref))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ExtractionError) {
                CommentsState.Failed(e)
            } catch (e: Exception) {
                CommentsState.Failed(ExtractionError.Unsupported("comment loading failed"))
            }
        }
    }

    Column(modifier.fillMaxWidth()) {
        val loaded = state as? CommentsState.Loaded
        val header = if (loaded != null) {
            "Comments (${loaded.comments.count { it.parentId == null }})"
        } else {
            "Comments"
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = state is CommentsState.NotLoaded) { load() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(header, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (state is CommentsState.NotLoaded) {
                Text(
                    "Tap to load",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        when (val s = state) {
            CommentsState.NotLoaded -> Unit
            CommentsState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            is CommentsState.Failed -> Text(
                s.error.userMessage(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            is CommentsState.Loaded -> {
                if (s.comments.isEmpty()) {
                    Text(
                        "No comments.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                } else {
                    val topLevel = s.comments.filter { it.parentId == null }
                    val repliesByParent = s.comments.filter { it.parentId != null }.groupBy { it.parentId }
                    topLevel.forEach { c -> CommentThread(c, repliesByParent[c.id].orEmpty()) }
                }
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
