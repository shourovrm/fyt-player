package com.fyiplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fyiplayer.app.R
import com.fyiplayer.app.core.ResultKind
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.repo.youtubeThumbnailFor
import com.fyiplayer.app.ui.theme.NumeralStyle
import com.fyiplayer.app.ui.theme.Radius
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import com.fyiplayer.app.ui.theme.fyiExtras
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One Home grid cell: 16:9 thumbnail, title and meta line. Tap opens the video; long-press opens
 * the shared action sheet. The long-press label is what TalkBack announces as the action.
 */
@Composable
internal fun VideoGridCard(ref: VideoRef, onClick: () -> Unit, onLongPress: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClickLabel = stringResource(R.string.action_more),
            onLongClick = onLongPress,
        ),
    ) {
        VideoGridThumbnail(ref)
        Column(Modifier.fillMaxWidth().padding(top = Spacing.s8)) {
            // A Library row can still be mid-enrichment; "Untitled" reads as loading, an empty line as broken.
            Text(
                ref.title.ifBlank { stringResource(R.string.state_untitled) },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            val meta = gridMetaText(ref)
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.fyiExtras.ink3,
                )
            }
        }
    }
}

@Composable
private fun VideoGridThumbnail(ref: VideoRef) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(Radius.m))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        // Library rows can carry only a bare pageUrl; fall back to the un-expiring hqdefault.
        val thumb = ref.thumbnailUrl ?: youtubeThumbnailFor(ref.pageUrl)
        if (thumb != null) {
            AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // LIVE and UPCOMING take the corner over a duration: neither is a fixed-length clip yet.
        when {
            ref.isLive -> VideoGridBadge(
                stringResource(R.string.state_live),
                background = MaterialTheme.colorScheme.error,
                color = MaterialTheme.colorScheme.onError,
                style = MaterialTheme.typography.labelSmall,
            )
            ref.isUpcoming -> VideoGridBadge(
                stringResource(R.string.state_upcoming),
                background = MaterialTheme.fyiExtras.videoScrim,
                color = MaterialTheme.fyiExtras.onVideo,
                style = MaterialTheme.typography.labelSmall,
            )
            ref.durationSeconds != null -> VideoGridBadge(
                formatDuration(ref.durationSeconds),
                background = MaterialTheme.fyiExtras.videoScrim,
                color = MaterialTheme.fyiExtras.onVideo,
                style = MaterialTheme.typography.labelSmall.merge(NumeralStyle),
            )
        }
        val position = LocalPlaybackPositions.current[ref.pageUrl]
        if (position != null && position.positionMs > 0 && position.durationMs > 0) {
            LinearProgressIndicator(
                progress = { (position.positionMs.toFloat() / position.durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(Sizes.progressTrack),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.fyiExtras.videoScrimLight,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun BoxScope.VideoGridBadge(text: String, background: Color, color: Color, style: TextStyle) {
    Text(
        text,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(6.dp)
            .clip(RoundedCornerShape(Radius.xs))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = color,
        style = style,
    )
}

/**
 * Channel rows show subscribers; videos show channel, views and age, non-null parts only.
 * Copied from ResultsList.kt's private resultMetaText and compactCount: make those internal and
 * delete this copy.
 */
@Composable
private fun gridMetaText(ref: VideoRef): String {
    if (ref.kind == ResultKind.CHANNEL) {
        val subs = ref.subscriberCount
        if (subs != null && subs >= 0) return stringResource(R.string.results_subscribers, compactCount(subs))
    }
    return listOfNotNull(ref.uploader, ref.viewCountText, shortAge(ref.uploadedText)).joinToString(" · ")
}

private fun compactCount(count: Long): String {
    if (count < 1000) return count.toString()
    val (divisor, suffix) = when {
        count < 1_000_000 -> 1_000.0 to "K"
        count < 1_000_000_000 -> 1_000_000.0 to "M"
        else -> 1_000_000_000.0 to "B"
    }
    val tenths = (count / divisor * 10).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}$suffix" else "${"%.1f".format(Locale.US, tenths / 10.0)}$suffix"
}

/** First-load placeholder with the same footprint as [VideoGridCard]: flat shapes, no shimmer. */
@Composable
internal fun VideoGridSkeletonCard() {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(Radius.m))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            Modifier
                .padding(top = Spacing.s8)
                .fillMaxWidth(0.8f)
                .height(Spacing.s16)
                .clip(RoundedCornerShape(Radius.xs))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(
            Modifier
                .padding(top = Spacing.s8)
                .fillMaxWidth(0.4f)
                .height(Spacing.s8)
                .clip(RoundedCornerShape(Radius.xs))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}
