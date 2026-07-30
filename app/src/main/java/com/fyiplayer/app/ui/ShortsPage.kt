package com.fyiplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.player.PlaybackSession
import com.fyiplayer.app.player.PlayerState
import com.fyiplayer.app.player.SharedVideoSurface

/**
 * One page of the shorts pager. Only the active page mounts [SharedVideoSurface] -- there is
 * exactly one shared surface for the whole process (CLAUDE.md), so an off-screen page must never
 * try to bind it too. Inactive pages fall back to their own listing thumbnail.
 */
@Composable
internal fun ShortsPage(
    ref: VideoRef,
    isActive: Boolean,
    playerState: PlayerState,
    onOpenDetail: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (isActive) {
            SharedVideoSurface(
                player = PlaybackSession.exoPlayer,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { PlaybackSession.togglePlayPause() },
            )
            if (playerState.isBuffering) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
            } else if (!playerState.isPlaying) {
                // Pause glyph is missing from material-icons-core (CLAUDE.md gotcha) -- PlayArrow
                // doubles as the paused affordance, shown only while paused, same as a tap-to-play hint.
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Paused, tap to play",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.Center).size(64.dp),
                )
            }
            playerState.error?.let { err ->
                Text(
                    err.userMessage(),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            }
        } else if (ref.thumbnailUrl != null) {
            AsyncImage(model = ref.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                .padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 24.dp),
        ) {
            Text(ref.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            ref.uploader?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                "Details",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable(onClick = onOpenDetail)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}
