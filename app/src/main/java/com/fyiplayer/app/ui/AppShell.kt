package com.fyiplayer.app.ui

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.player.PlayerScreen

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
@Composable
fun AppShell(navController: NavHostController) {
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
                onBack = { navController.popBackStack() },
                playerSurface = { _, fullscreen, onToggleFullscreen ->
                    PlayerScreen(
                        fullscreen = fullscreen,
                        onToggleFullscreen = onToggleFullscreen,
                        gestureBrightness = brightnessGesture,
                        gestureVolume = volumeGesture,
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
