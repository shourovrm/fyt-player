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
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fyiplayer.app.R
import com.fyiplayer.app.core.SeekThumbnails
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.repo.LikesRepository
import com.fyiplayer.app.ui.theme.Radius
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import com.fyiplayer.app.ui.theme.fyiExtras
import com.fyiplayer.app.data.repo.PlaylistRepository
import com.fyiplayer.app.player.CaptionSheet
import com.fyiplayer.app.player.CaptionsGlyph
import com.fyiplayer.app.player.PauseGlyph
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.player.PlaybackSession
import com.fyiplayer.app.settings.WallSignInButton
import com.fyiplayer.app.player.PlayerState
import com.fyiplayer.app.player.QualitySheet
import com.fyiplayer.app.player.SeekThumbnailPreview
import com.fyiplayer.app.player.SpeedSheet
import com.fyiplayer.app.player.fetchSeekThumbnails
import com.fyiplayer.app.player.imageFor
import com.fyiplayer.app.player.trimSpeed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One page of the shorts pager, drawn OVER the one shared surface that [ShortsPager] mounts under
 * the pager. The active page is transparent (video shows through); inactive pages show their own
 * listing thumbnail on black.
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
    /** Null when the listing carried no channel URL -- the name then renders as plain text. */
    onOpenChannel: (() -> Unit)?,
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
    var showCaptionSheet by remember { mutableStateOf(false) }
    val shareChooserTitle = stringResource(R.string.action_share)
    val extras = MaterialTheme.fyiExtras

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

    // No surface here -- the one shared PlayerView sits under the pager (ShortsPager). The active
    // page is a transparent overlay (video shows through); an inactive page paints black + its
    // thumbnail so it hides the video while sliding over it.
    BoxWithConstraints(Modifier.fillMaxSize().background(if (isActive) Color.Transparent else MaterialTheme.colorScheme.scrim)) {
        if (isActive) {
            // Between clips the player's shutter is black until the new first frame decodes
            // (~0.3-0.5s measured; isBuffering never flips for a prefetched slot, so it can't be
            // the signal). Keep this clip's own thumbnail up until onRenderedFirstFrame, so a
            // swipe lands on a picture, never on black.
            if (!playerState.firstFrameRendered && ref.thumbnailUrl != null) {
                AsyncImage(model = ref.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier
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
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = extras.onVideo)
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
                        tint = extras.onVideoDim,
                        modifier = Modifier.size(Sizes.transportButton),
                    )
                } else {
                    PauseGlyph(tint = extras.onVideoDim, size = Spacing.s56)
                }
            }
            playerState.error?.let { err ->
                Column(
                    Modifier.align(Alignment.Center).padding(Spacing.s32),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(err.userMessage(), color = extras.onVideo, style = MaterialTheme.typography.bodyMedium)
                    if (err is ExtractionError.AccessChallenge) {
                        WallSignInButton(
                            onSignedIn = { PlaybackSession.retryCurrent() },
                            modifier = Modifier.padding(top = Spacing.s16),
                        )
                    }
                }
            }
        } else if (ref.thumbnailUrl != null) {
            AsyncImage(model = ref.thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, extras.videoScrim)))
                // bottom must clear the WHOLE seekbar band (24 dp padding + 48 dp touch height =
                // 72 dp above the nav inset, see ShortsSeekBar below) plus a visible gap -- the
                // bar deliberately doesn't move, so this padding is what makes room instead.
                // end clears the 48 dp action rail plus its 16 dp margin.
                .padding(start = Spacing.s16, end = Spacing.s64 + Spacing.s16, top = Spacing.s48, bottom = Spacing.s64 + Spacing.s24),
        ) {
            Text(ref.title, color = extras.onVideo, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            ref.uploader?.let {
                Text(
                    if (onOpenChannel != null) "$it  ›" else it,
                    color = extras.onVideoDim,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .then(if (onOpenChannel != null) Modifier.clickable(onClick = onOpenChannel) else Modifier)
                        .padding(vertical = 4.dp),
                )
            }
            Text(
                stringResource(R.string.shorts_details),
                color = extras.onVideo,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .padding(top = 10.dp)
                    // Clickable sits outside the minimum-size wrapper so the 48 dp touch area is
                    // the tap target while the pill is drawn at its own smaller size.
                    .clickable {
                        // Sheet over the still-mounted pager now, not a nav world-switch --
                        // onOpenDetail (curried to this ref by ShortsPager) goes unused here but
                        // stays wired per-param, matching the rest of this rail. Playback keeps
                        // running behind the sheet; nothing here touches it.
                        showDetailsSheet = true
                    }
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(Radius.l))
                    .background(extras.onVideo.copy(alpha = 0.18f))
                    .padding(horizontal = Spacing.s16, vertical = 6.dp),
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
                context.startActivity(Intent.createChooser(send, shareChooserTitle))
            },
            onSave = { showSaveSheet = true },
            // Same active-page gate as quality/speed: the session's captions belong to the clip loaded now.
            onCaptions = if (isActive) { { showCaptionSheet = true } } else null,
            captionsAvailable = playerState.availableCaptions.isNotEmpty(),
            captionsSelected = playerState.selectedCaptionLanguage != null,
            // Quality/speed act on PlaybackSession's current item -- only meaningful while this
            // page is the one actually loaded, so the buttons don't show on inactive pages.
            onQuality = if (isActive) { { showQualitySheet = true } } else null,
            speed = playerState.speed,
            onSpeed = if (isActive) { { showSpeedSheet = true } } else null,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = Spacing.s16, bottom = Spacing.s64 + Spacing.s40),
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
                    .padding(bottom = Spacing.s24),
            )
        }

        if (isActive && scrubbing) {
            // x follows the thumb (bar spans maxWidth minus its own 2 dp * 2 padding above),
            // clamped so the card never runs off either screen edge. y is a fixed clearance,
            // tuned like the meta Column's own bottom padding above -- generous enough to clear
            // both the seekbar band and a 2-line title + uploader + Details pill, same
            // eyeballed-padding approach the rest of this page already uses.
            val barWidth = maxWidth - 4.dp
            // .value, not collectAsState: a non-reactive snapshot read, so this whole page (not
            // just the seek bar) doesn't recompose on every 500ms tick just to redraw the preview
            // card's x-position, which only matters while the finger is already down.
            val fraction = shortsProgressFraction(scrubMs, PlaybackSession.progress.value.durationMs)
            val thumbX = 2.dp + barWidth * fraction
            val previewX = (thumbX - Sizes.seekPreviewWidth / 2).coerceIn(0.dp, maxWidth - Sizes.seekPreviewWidth)
            SeekThumbnailPreview(
                image = seekThumbs?.imageFor(scrubMs / 1000.0),
                timestampText = shortsTimestamp(scrubMs),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = Spacing.s64 * 3 + Spacing.s24)
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
    if (showCaptionSheet) {
        CaptionSheet(
            tracks = playerState.availableCaptions,
            selectedLanguage = playerState.selectedCaptionLanguage,
            onSelect = { PlaybackSession.selectCaption(it); showCaptionSheet = false },
            onDismiss = { showCaptionSheet = false },
        )
    }
}

@Composable
private fun ShortsActionRail(
    liked: Boolean,
    onLike: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onCaptions: (() -> Unit)?,
    captionsAvailable: Boolean,
    captionsSelected: Boolean,
    onQuality: (() -> Unit)?,
    speed: Float,
    onSpeed: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val extras = MaterialTheme.fyiExtras
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.s16), horizontalAlignment = Alignment.CenterHorizontally) {
        RailButton(onClick = onLike) {
            Icon(
                if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = stringResource(if (liked) R.string.action_unlike else R.string.action_like),
                tint = if (liked) extras.likedOnVideo else extras.onVideo,
            )
        }
        RailButton(onClick = onSave) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_to_playlist), tint = extras.onVideo)
        }
        RailButton(onClick = onShare) {
            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share), tint = extras.onVideo)
        }
        // Stays visible but dimmed when the clip has no captions: a missing button would read as a
        // missing feature, not as "this clip has none".
        if (onCaptions != null) {
            RailButton(
                onClick = onCaptions,
                enabled = captionsAvailable,
                description = stringResource(R.string.player_menu_captions),
            ) {
                CaptionsGlyph(tint = extras.onVideo, size = Sizes.icon, filled = captionsSelected, dim = !captionsAvailable)
            }
        }
        // No quality glyph anywhere in this repo (PlayerScreen's own overflow menu is a text
        // DropdownMenuItem, not an icon) -- a text label matches what already exists instead of
        // inventing a new hand-drawn glyph.
        if (onQuality != null) {
            RailButton(onClick = onQuality) {
                Text(stringResource(R.string.shorts_quality_label), color = extras.onVideo, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (onSpeed != null) {
            RailButton(onClick = onSpeed) {
                Text("${trimSpeed(speed)}x", color = extras.onVideo, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun RailButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    /** Set only where the glyph itself carries no label (CaptionsGlyph has no contentDescription). */
    description: String? = null,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(Sizes.touchTarget)
            .clip(CircleShape)
            .background(MaterialTheme.fyiExtras.videoScrimFaint)
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * A real seekbar, not decoration: 4 dp track, thickened to 6 dp while a finger is down. The
 * modifier's own height is the 48 dp touch target even though the drawn track is 4 dp -- same
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
        // A short bar was inside the margin above and let fingers slip off. 48 dp gives a real
        // grab area while the track/thumb art stays thin.
        modifier = modifier.height(Sizes.touchTarget),
        thumb = {
            // Always a dot (YouTube Shorts' red dot): a visible handle is what makes "grab and
            // drag" discoverable; idle 10 dp, halo + 16 dp while the finger is down.
            val extras = MaterialTheme.fyiExtras
            Box(
                Modifier
                    .size(Sizes.iconLarge)
                    .clip(CircleShape)
                    .background(if (isScrubbing) extras.onVideo.copy(alpha = 0.18f) else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(if (isScrubbing) Sizes.iconSmall else 10.dp).clip(CircleShape).background(extras.onVideo))
            }
        },
        track = {
            val extras = MaterialTheme.fyiExtras
            Box(
                Modifier
                    .fillMaxWidth()
                    // Thickens under the finger so the whole bar reads as grabbed, not just the dot.
                    .height(if (isScrubbing) 6.dp else Sizes.progressTrack)
                    .clip(RoundedCornerShape(2.dp))
                    .background(extras.onVideo.copy(alpha = 0.28f)),
            ) {
                Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(extras.onVideo))
            }
        },
    )
}

// player.formatPosition/mmss are internal to the player package; shorts only ever needs the
// bare current time (no "/ duration"), so this is the whole one-line replacement.
private fun shortsTimestamp(ms: Long): String {
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
