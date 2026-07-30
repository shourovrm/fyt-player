package com.fyiplayer.app.ui

import android.net.Uri
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/** Typed route table — no caller hand-builds a route string. */
object Routes {
    const val HOME = "home"
    const val SHORTS = "shorts"
    const val LIBRARY = "library"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{pageUrl}"
    const val LISTING = "listing/{key}"
    const val PLAYLIST = "playlist/{id}"
}

/** [pageUrl] and [key] are full URLs; [id] just rides the same encode/decode for safety. */
fun NavController.openDetail(pageUrl: String) = navigate("detail/${Uri.encode(pageUrl)}")
fun NavController.openListing(key: String) = navigate("listing/${Uri.encode(key)}")
fun NavController.openPlaylist(id: String) = navigate("playlist/${Uri.encode(id)}")

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
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpenDetail = { navController.openDetail(it) },
                onOpenPlaylist = { navController.openPlaylist(it) },
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
            DetailScreen(pageUrl = pageUrl, onOpenDetail = { navController.openDetail(it) })
        }
        composable(
            Routes.LISTING,
            arguments = listOf(navArgument("key") { type = NavType.StringType }),
        ) { entry ->
            val key = entry.arguments?.getString("key")?.let(Uri::decode).orEmpty()
            ListingScreen(key = key, onOpenDetail = { navController.openDetail(it) })
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
