package com.fyiplayer.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fyiplayer.app.player.asActivity

/**
 * The one shared shell: owns the [NavHostController] (so the bottom nav and the [NavHost][
 * androidx.navigation.compose.NavHost] built in [AppShell] act on the same controller), the
 * system-bar insets (consumed exactly once, here), and the bottom nav's scroll auto-hide via a
 * single hoisted [NestedScrollConnection] — every scrolling screen drives it for free just by
 * being a scrollable child of [content].
 *
 * [miniPlayer] and [queueBar] are TODO seams: empty by default until the player phase fills them.
 * [miniPlayer] is rendered outside the nav bar's [AnimatedVisibility] on purpose — when auto-hide
 * slides the nav away, playback chrome must never be what disappears with it.
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    queueBar: @Composable (NavHostController) -> Unit = {},
    miniPlayer: @Composable (NavHostController) -> Unit = {},
    content: @Composable (NavHostController) -> Unit,
) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val selected = selectedTab(route)

    var barVisible by remember { mutableStateOf(true) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            // onPreScroll (not onPostScroll): seen even when the child list consumes all of it.
            // Nothing is consumed here — this only observes scroll direction.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -NAV_HIDE_SLOP_PX) barVisible = false
                else if (available.y > 0f) barVisible = true // any upward scroll brings it back
                return Offset.Zero
            }
        }
    }
    // A tab switch must never leave the bar stranded off-screen on a destination that never scrolls.
    LaunchedEffect(route) { barVisible = true }

    // Status-bar icon contrast: the app theme normally, but forced light over a full-bleed video
    // (Detail's own fullscreen zoom or the Shorts pager, both flagged by FullscreenChrome) where
    // the content underneath is unpredictable and may not contrast with the theme's own icons.
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val view = LocalView.current
    SideEffect {
        val window = view.context.asActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
            !FullscreenChrome.active && !isDarkTheme
    }

    Scaffold(
        // Full-bleed always: an outer inset-padding modifier here would push the Scaffold's own
        // background (and everything in it) down below the status bar, leaving the raw window
        // background showing through as a dead strip above the app. contentWindowInsets below
        // insets the *content* instead, so the background paints behind the status bar and the
        // icons drawn over it while the app surface still starts in the right place.
        modifier = modifier,
        contentWindowInsets = if (FullscreenChrome.active) WindowInsets(0) else WindowInsets.systemBars,
        bottomBar = {
            // One Column: queue bar + mini player sit above the nav bar and are part of the
            // Scaffold's bottom inset, so content is never hidden under them.
            Column {
                // Detail hosts the full player, and there is exactly one shared video surface —
                // mounting the mini bar there would steal it mid-playback.
                if (!isFullPlayerRoute(route)) {
                    queueBar(navController)
                    miniPlayer(navController)
                }
                AnimatedVisibility(
                    visible = barVisible && !FullscreenChrome.active,
                    enter = slideInVertically(tween(NAV_ANIM_MILLIS)) { it },
                    exit = slideOutVertically(tween(NAV_ANIM_MILLIS)) { it },
                ) {
                    NavigationBar {
                        NAV_TABS.forEach { tab ->
                            NavigationBarItem(
                                selected = tab.route == selected,
                                onClick = { navigateToTab(navController, tab.route, route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { inner ->
        // consumeWindowInsets: without it, screens with their own nested Scaffold/TopAppBar apply
        // the status-bar inset a second time — a status-bar-height dead gap above their app bar.
        Box(
            Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)
                .nestedScroll(nestedScrollConnection),
        ) {
            content(navController)
        }
    }
}

private class NavTab(val route: String, val label: String, val icon: ImageVector)

private val NAV_TABS = listOf(
    NavTab(Routes.HOME, "Home", Icons.Filled.Home),
    NavTab(Routes.SHORTS, "Shorts", Icons.Filled.PlayArrow),
    NavTab(Routes.LIBRARY, "Library", Icons.Filled.Favorite),
    NavTab(Routes.DOWNLOADS, "Downloads", Icons.AutoMirrored.Filled.List),
    NavTab(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

private const val NAV_ANIM_MILLIS = 180
/** A few px of downward travel before hiding, so a jittery finger doesn't flicker the bar. */
private const val NAV_HIDE_SLOP_PX = 3f

/**
 * Routes that own the shared video surface themselves. There is exactly one `PlayerView` and the
 * last composable to attach it wins, so mounting the mini bar over one of these would steal the
 * surface out from under the screen currently playing into it.
 *
 * Detail always qualifies — its pinned header is the surface for as long as the route is on
 * screen, fullscreen-zoomed or not. Shorts is one route for both the thumbnail grid and the
 * full-screen pager (`ShortsScreen.kt`'s own `showPlayer` toggle, not a nav route), so only
 * [FullscreenChrome.active] — which the pager sets true for exactly its own lifetime — tells them
 * apart here; the grid wants the mini player and queue bar back, same as any other listing route.
 */
private fun isFullPlayerRoute(route: String?): Boolean =
    route?.startsWith("detail/") == true ||
        route == Routes.SHORTS_PLAYER ||
        (route == Routes.SHORTS && FullscreenChrome.active)

/**
 * Is a full-bleed video surface on screen right now? Set by `DetailScreen`'s own fullscreen zoom
 * and by the Shorts pager (`ShortsScreen.kt`) — both same package, so no `player/` import is
 * needed there — and read here to drop the nav bar and the app-wide system-bar padding for
 * exactly as long as that lasts. A narrow, explicitly-named seam rather than inferring it from
 * inset visibility, which DESIGN.md's pitfalls call out as always getting it wrong.
 */
internal object FullscreenChrome {
    var active by mutableStateOf(false)
}

/** Which nav item is lit. detail/listing light nothing — they're reachable from more than one tab. */
private fun selectedTab(route: String?): String? = when (route) {
    Routes.HOME -> Routes.HOME
    Routes.SHORTS -> Routes.SHORTS
    Routes.LIBRARY, Routes.PLAYLIST -> Routes.LIBRARY
    Routes.DOWNLOADS -> Routes.DOWNLOADS
    Routes.SETTINGS -> Routes.SETTINGS
    else -> null
}

/**
 * Tap a tab: return to it if already on the back stack, else push it. Never `popUpTo` — that is
 * what lets back-from-detail restore the previous tab's scroll position, so a bottom-nav tap must
 * preserve the same property rather than growing the stack unbounded.
 */
private fun navigateToTab(navController: NavHostController, target: String, current: String?) {
    if (current == target) return
    if (!navController.popBackStack(target, inclusive = false)) navController.navigate(target)
}
