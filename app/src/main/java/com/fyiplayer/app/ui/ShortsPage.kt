package com.fyiplayer.app.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import com.fyiplayer.app.core.SeekThumbnails
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.repo.LikesRepository
import com.fyiplayer.app.data.repo.PlaylistRepository
import com.fyiplayer.app.player.PauseGlyph
import com.fyiplayer.app.player.PlaybackSession
import com.fyiplayer.app.player.PlayerState
import com.fyiplayer.app.player.QualitySheet
import com.fyiplayer.app.player.SeekThumbnailPreview
import com.fyiplayer.app.player.SharedVideoSurface
import com.fyiplayer.app.player.SpeedSheet
import com.fyiplayer.app.player.fetchSeekThumbnails
import com.fyiplayer.app.player.imageFor
import com.fyiplayer.app.player.trimSpeed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One page of the shorts pager. Only the active page mounts [SharedVideoSurface] -- there is
 * exactly one shared surface for the whole process (CLAUDE.md), so an off-screen page must never
 * try to bind it too. Inactive pages fall back to their own listing thumbnail.
 *
 * Like/share/add-to-playlist act on this page's own [ref], independent of which page is actually
 * playing: the pager can briefly compose an outgoing and an incoming page together mid-swipe, and
 * each must show its own correct liked state, not the active page's.
 */
@Composable
internal fun ShortsPage(
    ref: VideoRef,
    isActive: Boolean,
    playerState: PlayerState,
    onOpenDetail: () -> Unit,
) {
    val app = rememberFyiApp()
    val context = LocalContext.current
    val likes = remember { LikesRepository(app.database.likeDao()) }
    val playlists = remember { PlaylistRepository(app.database.playlistDao(), app.database.playlistItemDao()) }
    val liked by remember(ref.pageUrl) { likes.observeIsLiked(ref.pageUrl) }.collectAsState(initial = false)
    var showSaveSheet by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }

    // Tap toggles play/pause. The glyph shown is which way THIS tap just requested, not the
    // player's own isPlaying (onIsPlayingChanged lands a beat later) -- and it fades on its own
    // rather than sticking around, per the brief-confirmation UX this is asked to match.
    var pulseVisible by remember(ref.pageUrl) { mutableStateOf(false) }
    var pulseToPlaying by remember(ref.pageUrl) { mutableStateOf(true) }
    LaunchedEffect(pulseVisible) {
        if (pulseVisible) {
            delay(500)
            pulseVisible = false
        }
    }

    // Surfaced by ShortsSeekBar's own drag handling so the floating preview below can render
    // above the bar without owning the drag itself.
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableStateOf(0L) }
    // Storyboard fetch is LAZY: it costs a full extractor call, and most shorts get swiped past,
    // never scrubbed -- so it fires on the first drag, not on page activation. Keyed on isActive
    // too: the pager composes outgoing + incoming pages together mid-swipe (see class doc), and
    // a page going inactive drops its sheet.
    var seekThumbs by remember { mutableStateOf<SeekThumbnails?>(null) }
    LaunchedEffect(isActive, ref.pageUrl, scrubbing) {
        if (!isActive) { seekThumbs = null; return@LaunchedEffect }
        if (scrubbing && seekThumbs == null) seekThumbs = fetchSeekThumbnails(ref)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        if (isActive) {
            SharedVideoSurface(
                player = PlaybackSession.exoPlayer,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        pulseToPlaying = !playerState.isPlaying
                        pulseVisible = true
                        PlaybackSession.togglePlayPause()
                    },
            )
            if (playerState.isBuffering) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
            }
            AnimatedVisibility(
                visible = pulseVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                // Pause glyph is missing from material-icons-core (CLAUDE.md gotcha) -- reuse the
                // player package's own hand-drawn one rather than a second copy of it.
                if (pulseToPlaying) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(64.dp),
                    )
                } else {
                    PauseGlyph(tint = Color.White.copy(alpha = 0.85f), size = 56.dp)
                }
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
                // bottom must clear the WHOLE seekbar band (24dp padding + 40dp touch height =
                // 64dp above the nav inset, see ShortsSeekBar below) plus a visible gap -- the
                // bar deliberately doesn't move, so this padding is what makes room instead.
                // 48dp cleared the title but left the Details pill (last child) on the bar line.
                .padding(start = 16.dp, end = 74.dp, top = 48.dp, bottom = 76.dp),
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
                    .clickable {
                        // Sheet over the still-mounted pager now, not a nav world-switch --
                        // onOpenDetail (curried to this ref by ShortsPager) goes unused here but
                        // stays wired per-param, matching the rest of this rail. Playback keeps
                        // running behind the sheet; nothing here touches it.
                        showDetailsSheet = true
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        ShortsActionRail(
            liked = liked,
            onLike = {
                // Process scope, not rememberCoroutineScope: this page can leave composition
                // (swipe past it) right after the tap, which would cancel the write before it lands.
                app.appScope.launch { if (liked) likes.unlike(ref.pageUrl) else likes.like(ref) }
            },
            onShare = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    // Canonical page URL only -- never a signed media URL (project rule).
                    putExtra(Intent.EXTRA_TEXT, ref.pageUrl)
                    putExtra(Intent.EXTRA_TITLE, ref.title)
                }
                context.startActivity(Intent.createChooser(send, "Share link"))
            },
            onSave = { showSaveSheet = true },
            // Quality/speed act on PlaybackSession's current item -- only meaningful while this
            // page is the one actually loaded, so the buttons don't show on inactive pages.
            onQuality = if (isActive) { { showQualitySheet = true } } else null,
            speed = playerState.speed,
            onSpeed = if (isActive) { { showSpeedSheet = true } } else null,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 104.dp),
        )

        if (isActive) {
            ShortsSeekBar(
                onScrub = { active, ms -> scrubbing = active; scrubMs = ms },
                // FullscreenChrome makes this Box full-bleed (ShortsScreen.kt) -- nothing else
                // consumes the nav-bar inset, so without windowInsetsPadding here the bar sat
                // inside the gesture nav zone and drags got stolen by the system. 24dp on top of
                // the inset is the buffer, not a duplicate of it.
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp),
            )
        }

        if (isActive && scrubbing) {
            // x follows the thumb (bar spans maxWidth minus its own 2dp*2 padding above),
            // clamped so the card never runs off either screen edge. y is a fixed clearance,
            // tuned like the meta Column's own bottom=76dp above -- generous enough to clear
            // both the seekbar band and a 2-line title + uploader + Details pill, same
            // eyeballed-padding approach the rest of this page already uses.
            val barWidth = maxWidth - 4.dp
            // .value, not collectAsState: a non-reactive snapshot read, so this whole page (not
            // just the seek bar) doesn't recompose on every 500ms tick just to redraw the preview
            // card's x-position, which only matters while the finger is already down.
            val fraction = shortsProgressFraction(scrubMs, PlaybackSession.progress.value.durationMs)
            val thumbX = 2.dp + barWidth * fraction
            val previewX = (thumbX - ShortsSeekPreviewWidth / 2).coerceIn(0.dp, maxWidth - ShortsSeekPreviewWidth)
            SeekThumbnailPreview(
                image = seekThumbs?.imageFor(scrubMs / 1000.0),
                timestampText = shortsTimestamp(scrubMs),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 220.dp)
                    .offset(x = previewX),
            )
        }
    }

    if (showSaveSheet) {
        SavePlaylistSheet(refs = listOf(ref), playlists = playlists, onDismiss = { showSaveSheet = false })
    }
    if (showDetailsSheet) {
        // Closing (swipe/scrim/back) just dismisses -- playback was never paused for the sheet,
        // so there's nothing to resume here.
        ShortsDetailsSheet(ref = ref, onDismiss = { showDetailsSheet = false })
    }
    // Same sheets, same PlaybackSession calls PlayerScreen's overflow menu uses -- one shared
    // seam for both surfaces instead of a second quality/speed path just for shorts.
    if (showQualitySheet) {
        QualitySheet(
            heights = playerState.availableHeights,
            selectedHeight = playerState.selectedHeight,
            onSelect = { PlaybackSession.selectQuality(it); showQualitySheet = false },
            onDismiss = { showQualitySheet = false },
        )
    }
    if (showSpeedSheet) {
        SpeedSheet(
            current = playerState.speed,
            onSelect = { PlaybackSession.setSpeed(it); showSpeedSheet = false },
            onDismiss = { showSpeedSheet = false },
        )
    }
}

@Composable
private fun ShortsActionRail(
    liked: Boolean,
    onLike: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onQuality: (() -> Unit)?,
    speed: Float,
    onSpeed: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RailButton(onClick = onLike) {
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (liked) "Unlike" else "Like",
                tint = if (liked) Color(0xFFE0245E) else Color.White,
            )
        }
        RailButton(onClick = onSave) {
            Icon(Icons.Filled.Add, contentDescription = "Add to playlist", tint = Color.White)
        }
        RailButton(onClick = onShare) {
            Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
        }
        // No quality glyph anywhere in this repo (PlayerScreen's own overflow menu is a text
        // DropdownMenuItem, not an icon) -- a text label matches what already exists instead of
        // inventing a new hand-drawn glyph.
        if (onQuality != null) {
            RailButton(onClick = onQuality) {
                Text("HD", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (onSpeed != null) {
            RailButton(onClick = onSpeed) {
                Text("${trimSpeed(speed)}x", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun RailButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * A real seekbar, not decoration: 3dp track, thumb + halo drawn only while a finger is actually
 * down (mockup ui-fixes §2) so the bar reads as a thin progress line the rest of the time. The
 * modifier's own height is the touch target (>=40dp) even though the drawn track is 3dp -- same
 * "big grab area, thin visual" split [player.PlayerChrome.ControlBar] uses for the full player.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortsSeekBar(
    onScrub: (isScrubbing: Boolean, positionMs: Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    // Collected HERE, not passed down from ShortsPage/ShortsPager -- only this bar (the active
    // page's own) needs the 500ms tick; keeping it local means the tick never touches ShortsPage
    // or the pager (CLAUDE.md perf task).
    val progress by PlaybackSession.progress.collectAsState()
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubValueMs by remember { mutableStateOf(0L) }
    val shownMs = if (isScrubbing) scrubValueMs else progress.positionMs
    val fraction = shortsProgressFraction(shownMs, progress.durationMs)

    Slider(
        value = shownMs.toFloat(),
        valueRange = 0f..progress.durationMs.coerceAtLeast(1L).toFloat(),
        onValueChange = { value ->
            isScrubbing = true
            scrubValueMs = value.toLong()
            onScrub(true, scrubValueMs)
        },
        onValueChangeFinished = {
            PlaybackSession.seekTo(scrubValueMs)
            isScrubbing = false
            onScrub(false, scrubValueMs)
        },
        // Drag hit-testing is the Slider's own layout bounds, not the drawn track/thumb --
        // 20dp was inside the margin above and let fingers slip off. 40dp gives a real grab
        // area while the track/thumb art stays exactly as thin as the mockup calls for.
        modifier = modifier.height(40.dp),
        thumb = {
            if (isScrubbing) {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(13.dp).clip(CircleShape).background(Color.White))
                }
            }
        },
        track = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.28f)),
            ) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Color.White))
            }
        },
    )
}

// Mirrors player.SEEK_PREVIEW_WIDTH (internal to that package, so not reusable here) -- keep
// in sync if that card's width ever changes.
private val ShortsSeekPreviewWidth = 140.dp

// player.formatPosition/mmss are internal to the player package; shorts only ever needs the
// bare current time (no "/ duration"), so this is the whole one-line replacement.
private fun shortsTimestamp(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
