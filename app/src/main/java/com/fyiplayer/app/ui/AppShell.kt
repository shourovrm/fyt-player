package com.fyiplayer.app.ui

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.fyiplayer.app.player.PlayerScreen
import com.fyiplayer.app.player.PlaybackSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

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

fun NavController.openListing(listing: Listing) = navigate(
    "listing/${Uri.encode(listing.key)}" +
        "?sourceId=${Uri.encode(listing.sourceId)}&kind=${listing.kind.name}&title=${Uri.encode(listing.title)}",
)

fun NavController.openPlaylist(id: String) = navigate("playlist/${Uri.encode(id)}")

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
            ShortsScreen(onOpenDetail = { navController.openDetail(it) })
        }
        composable(Routes.SHORTS_PLAYER) {
            ShortsPlayerScreen(
                onOpenDetail = { navController.openDetail(it) },
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
            SettingsScreen()
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("pageUrl") { type = NavType.StringType }),
        ) { entry ->
            val pageUrl = entry.arguments?.getString("pageUrl")?.let(Uri::decode).orEmpty()
            val prefs = rememberFyiApp().prefs
            val brightnessGesture by prefs.gestureBrightness.collectAsState(initial = true)
            val volumeGesture by prefs.gestureVolume.collectAsState(initial = true)
            DetailScreen(
                pageUrl = pageUrl,
                onOpenDetail = { navController.openDetail(it) },
                onOpenListing = { navController.openListing(it) },
                onOpenShorts = { items, index -> navController.openShortsPlayer(items, index) },
                onBack = { navController.popBackStack() },
                playerSurface = { surfaceRef, fullscreen, onToggleFullscreen ->
                    PlayerScreen(
                        fullscreen = fullscreen,
                        onToggleFullscreen = onToggleFullscreen,
                        gestureBrightness = brightnessGesture,
                        gestureVolume = volumeGesture,
                        pageRef = surfaceRef,
                        // ⌄ pops like back does, so a Similar chain still unwinds one video at a
                        // time. X = done watching: stops playback and drops the whole Detail chain
                        // back to the tab underneath (nothing lands in the mini player).
                        onClose = { PlaybackSession.clear(); navController.popBackStack(Routes.HOME, inclusive = false) },
                        onMinimize = { navController.popBackStack() },
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
            PlaylistDetailScreen(id = id, onOpenDetail = { navController.openDetail(it) })
        }
    }
    }
}
