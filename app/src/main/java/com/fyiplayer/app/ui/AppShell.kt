package com.fyiplayer.app.ui

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.repo.PositionsRepository
import com.fyiplayer.app.player.CaptionSize
import com.fyiplayer.app.player.PlayerScreen
import com.fyiplayer.app.player.PlaybackSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Typed route table — no caller hand-builds a route string. */
object Routes {
    const val HOME = "home"
    const val SHORTS = "shorts"
    // Full-screen swipe pager over a shorts listing that isn't the Shorts tab's own feed
    // (channel Shorts tab); its item list rides in ShortsPlayerRequest, not the route.
    const val SHORTS_PLAYER = "shortsPlayer"
    const val LIBRARY = "library"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val SETTINGS_PAGE = "settings/{page}"
    const val DETAIL = "detail/{pageUrl}"
    // key is a full channel/playlist URL (path segment); sourceId/kind/title ride as query args so
    // ListingScreen can call the right VideoSource.listing() without a second lookup.
    const val LISTING = "listing/{key}?sourceId={sourceId}&kind={kind}&title={title}"
    const val PLAYLIST = "playlist/{id}"
}

/** Raw-URL overload for screens that only have a page URL (Shorts/Library/Playlist placeholders,
 *  still unbuilt). Detail then starts blank until its own resolve lands -- honest, not a crash. */
fun NavController.openDetail(pageUrl: String) = navigate("detail/${Uri.encode(pageUrl)}")

/** [ref] is stashed in [RefCache] (see its doc) so Detail paints its header from it immediately —
 *  a nav route can only carry the page URL itself. Preferred whenever a full ref is on hand. */
fun NavController.openDetail(ref: VideoRef) {
    RefCache.put(ref)
    openDetail(ref.pageUrl)
}

/** X and ⌄ on the Detail player: pops every Detail page on top of the stack, so the user lands on the
 *  screen the first video was opened from. Stops at the first non-Detail entry, so Home stays the
 *  floor when no other screen sits under the chain. */
fun NavController.closeDetailChain() {
    while (currentBackStackEntry?.destination?.route == Routes.DETAIL && popBackStack()) {
    }
}

/** Route of the back stack entry directly under [entry], or null at the bottom of the stack. Reads
 *  the observable stack list rather than previousBackStackEntry, so the Previous button recomposes. */
private fun entryBelow(stack: List<NavBackStackEntry>, entry: NavBackStackEntry): NavBackStackEntry? {
    val index = stack.indexOfFirst { it.id == entry.id }
    return stack.getOrNull(index - 1)
}

private fun routeBelow(stack: List<NavBackStackEntry>, entry: NavBackStackEntry): String? =
    entryBelow(stack, entry)?.destination?.route

fun NavController.openListing(listing: Listing) = navigate(
    "listing/${Uri.encode(listing.key)}" +
        "?sourceId=${Uri.encode(listing.sourceId)}&kind=${listing.kind.name}&title=${Uri.encode(listing.title)}",
)

fun NavController.openPlaylist(id: String) = navigate("playlist/${Uri.encode(id)}")

fun NavController.openSettingsPage(page: String) = navigate("settings/$page")

private val YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com")
private val VERTICAL_CLIP_HOSTS = setOf("tiktok.com", "www.tiktok.com", "m.tiktok.com", "vm.tiktok.com", "vt.tiktok.com")

/** A shared link whose platform is vertical-clip-only (TikTok): it belongs in the shorts pager,
 *  not the landscape Detail player. Host match only -- no network (yt-dlp resolves it later). */
internal fun isVerticalClipUrl(url: String): Boolean {
    // java.net.URI, not android.net.Uri: keeps this JVM-testable.
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    if (host in VERTICAL_CLIP_HOSTS) return true
    // YouTube is mixed: only a /shorts/ path is a vertical clip, a /watch link is Detail.
    return host in YOUTUBE_HOSTS && uri.path.orEmpty().startsWith("/shorts/")
}

private val LIST_ID = Regex("[?&]list=([^&]+)")

/** `list=` param on a shared YouTube URL (`.../playlist?list=PL...` or `watch?v=X&list=PL...`) ->
 *  the canonical `playlist?list=` page URL, or null when there's no list param or it's a mix/radio
 *  id (`RD*` -- the extractor rejects those, see DECISIONS.md Tried/rejected). Mirrors
 *  `NewPipeYoutubeSource.toPlaylistVideoRef`'s id extraction so both land on the same URL shape. */
internal fun youtubePlaylistUrl(url: String): String? {
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    if (host !in YOUTUBE_HOSTS) return null
    val listId = LIST_ID.find(url)?.groupValues?.get(1) ?: return null
    if (listId.startsWith("RD")) return null
    return "https://www.youtube.com/playlist?list=$listId"
}

/** Share/open-with entry: one seam for every shared URL. A `list=` param -- bare playlist link or
 *  `watch?v=X&list=PL..` -- opens the remote playlist listing (user can still tap into the video
 *  from there); vertical-clip hosts open the pager as a single-item list; everything else is Detail. */
fun NavController.openSharedUrl(url: String) {
    youtubePlaylistUrl(url)?.let { playlistUrl ->
        return openListing(Listing(sourceId = "youtube", kind = Listing.Kind.PLAYLIST, key = playlistUrl, title = ""))
    }
    if (!isVerticalClipUrl(url)) return openDetail(url)
    val ref = VideoRef(
        sourceId = SourceRegistry.forUrl(url)?.id ?: "",
        pageUrl = url, remoteId = url, title = "", isShort = true,
    )
    openShortsPlayer(listOf(ref), 0)
}

/** Opens the swipe pager over [items] starting at [index] — see [ShortsPlayerRequest]. */
fun NavController.openShortsPlayer(items: List<VideoRef>, index: Int) {
    ShortsPlayerRequest.items = items
    ShortsPlayerRequest.index = index
    navigate(Routes.SHORTS_PLAYER)
}

/**
 * The NavHost, hosted inside [AppScaffold]'s content slot so it shares one [NavHostController]
 * with the bottom nav. All four transitions are [EnterTransition.None]/[ExitTransition.None] —
 * Navigation-Compose's default ~700ms cross-fade tears an outgoing screen with video still
 * drawing over the incoming list (DESIGN.md §8).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun AppShell(navController: NavHostController) {
    // One flow for the whole app (rule 6: never a per-row position query) -- off entirely when the
    // "Remember playback position" preference is off, so no row anywhere shows a resume bar.
    val app = rememberFyiApp()
    val positionsRepo = remember(app) { PositionsRepository(app.database.playbackPositionDao()) }
    val positions by remember(app, positionsRepo) {
        app.prefs.savePlayPosition.flatMapLatest { enabled -> if (enabled) positionsRepo.observeAll() else flowOf(emptyMap()) }
    }.collectAsState(initial = emptyMap())

    // Watch trail for single-video Detail playback. A depth change observed here is how any video
    // opened by another route (list, mini player, autoplay) ends forward history.
    val navBackStack by navController.currentBackStack.collectAsState()
    var watchTrail by remember { mutableStateOf(WatchTrail()) }
    LaunchedEffect(navBackStack.size) { watchTrail = watchTrail.onBackStackDepthChanged(navBackStack.size) }

    // One level back: the Detail below replays its own video through its entry guard, the same
    // as system Back. The video being left is remembered first so Next can return to it.
    val goPrevious: () -> Unit = {
        val session = PlaybackSession.state.value
        session.queue.getOrNull(session.index)?.let { watchTrail = watchTrail.recordLeft(it) }
        navController.popBackStack()
    }
    // The notification, lockscreen and headset Previous/Next walk the same trail as the in-app
    // buttons, and exist only while a Detail page is on top, as those buttons do.
    val topEntry = navBackStack.lastOrNull()
    val detailOnTop = topEntry != null && topEntry.destination.route == Routes.DETAIL
    val trailPrevious = topEntry != null && detailOnTop &&
        WatchTrail.canGoPrevious(routeBelow(navBackStack, topEntry))
    val trailForward = watchTrail.nextForward().takeIf { detailOnTop }
    // They also start the video themselves: a hidden app does not recompose, so the Detail page
    // that normally starts playback would not run until the user comes back.
    val pageBelow = topEntry?.let { entryBelow(navBackStack, it) }?.arguments?.getString("pageUrl")?.let(Uri::decode)
    LaunchedEffect(trailPrevious, trailForward, pageBelow) {
        PlaybackSession.setTrailSkips(
            previous = pageBelow?.takeIf { trailPrevious }?.let { below ->
                {
                    goPrevious()
                    PlaybackSession.play(listOf(RefCache.getOrBare(below)), 0)
                }
            },
            next = trailForward?.let { target ->
                {
                    watchTrail = watchTrail.popForward()
                    navController.openDetail(target)
                    PlaybackSession.play(listOf(target), 0)
                }
            },
        )
    }

    CompositionLocalProvider(LocalPlaybackPositions provides positions) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenDetail = { navController.openDetail(it) },
                onOpenListing = { navController.openListing(it) },
                onOpenShorts = { items, index -> navController.openShortsPlayer(items, index) },
            )
        }
        composable(Routes.SHORTS) {
            ShortsScreen(
                onOpenDetail = { navController.openDetail(it) },
                onOpenChannel = { navController.openListing(it) },
            )
        }
        composable(Routes.SHORTS_PLAYER) {
            ShortsPlayerScreen(
                onOpenDetail = { navController.openDetail(it) },
                onOpenChannel = { navController.openListing(it) },
                onClose = { navController.popBackStack() },
            )
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpenDetail = { navController.openDetail(it) },
                onOpenPlaylist = { navController.openPlaylist(it) },
                onOpenListing = { navController.openListing(it) },
            )
        }
        composable(Routes.DOWNLOADS) {
            DownloadsScreen()
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onOpenPage = { navController.openSettingsPage(it) })
        }
        composable(
            Routes.SETTINGS_PAGE,
            arguments = listOf(navArgument("page") { type = NavType.StringType }),
        ) { entry ->
            val page = entry.arguments?.getString("page").orEmpty()
            SettingsPageScreen(page, onBack = { navController.popBackStack() })
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("pageUrl") { type = NavType.StringType }),
        ) { entry ->
            val pageUrl = entry.arguments?.getString("pageUrl")?.let(Uri::decode).orEmpty()
            val app = rememberFyiApp()
            val prefs = app.prefs
            val brightnessGesture by prefs.gestureBrightness.collectAsState(initial = true)
            val volumeGesture by prefs.gestureVolume.collectAsState(initial = true)
            val captionSize by prefs.captionSizeVideo.collectAsState(initial = CaptionSize.DEFAULT)
            val autoplayNext by prefs.autoplayNext.collectAsState(initial = false)
            val canGoPrevious = WatchTrail.canGoPrevious(routeBelow(navBackStack, entry))
            DetailScreen(
                pageUrl = pageUrl,
                onOpenDetail = { navController.openDetail(it) },
                onOpenListing = { navController.openListing(it) },
                onOpenShorts = { items, index -> navController.openShortsPlayer(items, index) },
                onBack = { navController.popBackStack() },
                playerSurface = { surfaceRef, fullscreen, onToggleFullscreen, similarSuggestion ->
                    // The forward list wins over the Similar suggestion: Next walks back down a trail
                    // the user already went up, and only then, with autoplay on, offers something new.
                    val forwardTarget = watchTrail.nextForward()
                    val nextTarget = watchTrail.nextTarget(similarSuggestion, autoplayNext)
                    PlayerScreen(
                        fullscreen = fullscreen,
                        onToggleFullscreen = onToggleFullscreen,
                        gestureBrightness = brightnessGesture,
                        gestureVolume = volumeGesture,
                        captionSize = captionSize,
                        onCaptionSizeSelect = { size -> app.appScope.launch { prefs.setCaptionSizeVideo(size) } },
                        pageRef = surfaceRef,
                        // Both unwind every Detail page to the screen the first video came from
                        // (playlist, channel, library). X also stops playback; ⌄ keeps it running
                        // in the mini player. A one-level pop would land on the previous Detail,
                        // whose entry guard restarts that video over the one still playing.
                        onClose = {
                            PlaybackSession.clear()
                            watchTrail = watchTrail.cleared()
                            navController.closeDetailChain()
                        },
                        onMinimize = {
                            watchTrail = watchTrail.cleared()
                            navController.closeDetailChain()
                        },
                        watchTrailEnabled = true,
                        onTrailPrevious = if (canGoPrevious) goPrevious else null,
                        onTrailNext = nextTarget?.let { target ->
                            {
                                if (forwardTarget != null) watchTrail = watchTrail.popForward()
                                navController.openDetail(target)
                            }
                        },
                        autoplayNext = autoplayNext,
                        onToggleAutoplayNext = { app.appScope.launch { prefs.setAutoplayNext(!autoplayNext) } },
                    )
                },
            )
        }
        composable(
            Routes.LISTING,
            arguments = listOf(
                navArgument("key") { type = NavType.StringType },
                navArgument("sourceId") { type = NavType.StringType; defaultValue = "" },
                navArgument("kind") { type = NavType.StringType; defaultValue = Listing.Kind.CHANNEL.name },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val args = entry.arguments
            val key = args?.getString("key")?.let(Uri::decode).orEmpty()
            val sourceId = args?.getString("sourceId").orEmpty()
            val kind = runCatching { Listing.Kind.valueOf(args?.getString("kind").orEmpty()) }
                .getOrDefault(Listing.Kind.CHANNEL)
            val title = args?.getString("title")?.let(Uri::decode).orEmpty()
            val listing = Listing(sourceId = sourceId, kind = kind, key = key, title = title)
            // A channel has five independent tabs (videos/shorts/playlists/courses/live) plus
            // in-channel search; a playlist is genuinely one flat list. Same route, different
            // screen per Listing.Kind rather than one screen straddling both shapes.
            if (kind == Listing.Kind.CHANNEL) {
                ChannelScreen(
                    listing = listing,
                    onOpenDetail = { navController.openDetail(it) },
                    onOpenListing = { navController.openListing(it) },
                    onOpenShorts = { items, index -> navController.openShortsPlayer(items, index) },
                    onBack = { navController.popBackStack() },
                )
            } else {
                ListingScreen(
                    listing = listing,
                    onOpenDetail = { navController.openDetail(it) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            Routes.PLAYLIST,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id")?.let(Uri::decode).orEmpty()
            PlaylistDetailScreen(
                id = id,
                onOpenDetail = { navController.openDetail(it) },
                onDeleted = { navController.popBackStack() },
            )
        }
    }
    }
}
