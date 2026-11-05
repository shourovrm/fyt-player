package com.fyiplayer.app.ui

import com.fyiplayer.app.ui.theme.ExtraIcons
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fyiplayer.app.R
import com.fyiplayer.app.player.asActivity
import com.fyiplayer.app.ui.theme.IconColours
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import com.fyiplayer.app.ui.theme.fyiExtras

/**
 * The one shared shell: owns the [NavHostController] (so the bottom nav and the [NavHost][
 * androidx.navigation.compose.NavHost] built in [AppShell] act on the same controller), the
 * system-bar insets (consumed exactly once, here), and the bottom nav's scroll auto-hide via a
 * single hoisted [NestedScrollConnection] — every scrolling screen drives it for free just by
 * being a scrollable child of [content].
 *
 * Landscape swaps the bottom bar for a left rail with the same destinations; the rail never
 * auto-hides, because it is the only way to reach the other tabs.
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
    val onTab: (String) -> Unit = { navigateToTab(navController, it, route) }

    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp

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

    // NOT Compose's WindowInsets.systemBars (its cached holder wedges across the fullscreen
    // hide/show + in-process rotation and keeps serving landscape values to a portrait layout),
    // and NOT a keyed snapshot of getRootWindowInsets either (Android mutates the Configuration
    // instance in place, so remember keys never fire again and a landscape value read during the
    // exit window latches forever -- both device-verified). [SystemBarInsetsState] is fed by
    // MainActivity's decor-view listener on EVERY genuine inset dispatch; the WM round-trip in
    // setFullscreen's exit guarantees one lands after the transition.
    val fullscreenActive = FullscreenChrome.active
    // Exit-fullscreen: overwrite with the latched portrait insets immediately (PipePipe's
    // manual-reset trick) -- the OS may never redeliver, and a late real dispatch still wins.
    LaunchedEffect(fullscreenActive) {
        if (!fullscreenActive) SystemBarInsetsState.restoreLatched()
    }
    val systemBarInsets = SystemBarInsetsState.insets

    Scaffold(
        // Full-bleed always: an outer inset-padding modifier here would push the Scaffold's own
        // background (and everything in it) down below the status bar, leaving the raw window
        // background showing through as a dead strip above the app. contentWindowInsets below
        // insets the *content* instead, so the background paints behind the status bar and the
        // icons drawn over it while the app surface still starts in the right place.
        modifier = modifier,
        contentWindowInsets = if (fullscreenActive) WindowInsets(0) else systemBarInsets,
        bottomBar = {
            // One Column: queue bar + mini player sit above the nav bar and are part of the
            // Scaffold's bottom inset, so content is never hidden under them.
            Column {
                // Queue bar on every route incl. Detail (docked above the nav bar, and it stays
                // when the nav auto-hides); never over a full-bleed surface. The mini player is
                // different: Detail hosts the full player, and there is exactly one shared video
                // surface — mounting the mini bar there would steal it mid-playback.
                if (!FullscreenChrome.active) queueBar(navController)
                if (!isFullPlayerRoute(route)) miniPlayer(navController)
                if (!landscape) {
                    AnimatedVisibility(
                        visible = barVisible && !FullscreenChrome.active,
                        enter = slideInVertically(tween(NAV_ANIM_MILLIS)) { it },
                        exit = slideOutVertically(tween(NAV_ANIM_MILLIS)) { it },
                    ) {
                        BottomNavBar(selected = selected, onSelect = onTab)
                    }
                }
                // The bar carried the gesture-nav inset. Once it slides away, or the landscape rail
                // replaces it, the docked queue bar / mini player would sit under the gesture pill
                // (device-verified).
                if ((landscape || !barVisible) && !FullscreenChrome.active) {
                    Spacer(Modifier.windowInsetsBottomHeight(systemBarInsets))
                }
            }
        },
    ) { inner ->
        // consumeWindowInsets: without it, screens with their own nested Scaffold/TopAppBar apply
        // the status-bar inset a second time — a status-bar-height dead gap above their app bar.
        // The display cutout is consumed too: in landscape the punch-hole becomes a 126px LEFT
        // inset that M3's nested Scaffold/TopAppBar would pad on their own — a grey strip beside
        // the player while the outer chrome spans full width (device-verified, rotation 1).
        // This app is edge-to-edge under the cutout everywhere, so nothing below may re-pad it.
        Row(
            Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner)
                .consumeWindowInsets(WindowInsets.displayCutout)
                .nestedScroll(nestedScrollConnection),
        ) {
            if (landscape && !FullscreenChrome.active) NavRail(selected = selected, onSelect = onTab)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                // Update banner rides above every tab, never over a full-bleed player surface.
                if (!isFullPlayerRoute(route) && !FullscreenChrome.active) UpdateBanner()
                Box(Modifier.weight(1f)) {
                    content(navController)
                }
            }
        }
    }
}

/** [colour] picks the tab's icon colour when selected, from the theme's icon set. */
private class NavTab(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
    val colour: (IconColours) -> Color,
)

private val NAV_TABS = listOf(
    NavTab(
        Routes.HOME, R.string.shell_tab_home, Icons.Filled.Home, Icons.Outlined.Home, IconColours::navHome,
    ),
    NavTab(
        Routes.SHORTS, R.string.shell_tab_shorts, Icons.Filled.PlayArrow, Icons.Outlined.PlayArrow,
        IconColours::navShorts,
    ),
    NavTab(
        Routes.LIBRARY, R.string.shell_tab_library, Icons.Filled.Favorite, Icons.Filled.FavoriteBorder,
        IconColours::navLibrary,
    ),
    // material-icons-core has no outlined Download, so the same glyph serves both states.
    NavTab(
        Routes.DOWNLOADS, R.string.shell_tab_downloads, ExtraIcons.Download, ExtraIcons.Download,
        IconColours::navDownloads,
    ),
)

/** Portrait: four destinations on a 56 dp bar, flat on the page colour, above the gesture-nav inset. */
@Composable
private fun BottomNavBar(selected: String?, onSelect: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().height(Sizes.navBar)) {
            NAV_TABS.forEach { tab ->
                NavTabItem(
                    tab,
                    selected = tab.route == selected,
                    onClick = { onSelect(tab.route) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

/** Landscape: the same four destinations as a vertical rail on the left edge. */
@Composable
private fun NavRail(selected: String?, onSelect: (String) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(Spacing.s64).padding(vertical = Spacing.s8),
        verticalArrangement = Arrangement.spacedBy(Spacing.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NAV_TABS.forEach { tab ->
            NavTabItem(
                tab,
                selected = tab.route == selected,
                onClick = { onSelect(tab.route) },
                modifier = Modifier.size(width = Spacing.s64, height = Sizes.navBar),
            )
        }
    }
}

/**
 * One destination: icon over label, no indicator. Selection is the tab's icon colour and a SemiBold
 * ink label; unselected is muted. The visible label is the name, so the icon has no content
 * description; the selectable's merged semantics read the label. The tab's whole box is the touch
 * target.
 */
@Composable
private fun NavTabItem(tab: NavTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val iconTint = if (selected) tab.colour(MaterialTheme.fyiExtras.icons) else MaterialTheme.colorScheme.onSurfaceVariant
    val labelColour = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (selected) tab.selectedIcon else tab.icon,
            contentDescription = null,
            modifier = Modifier.size(Sizes.icon),
            tint = iconTint,
        )
        Text(
            stringResource(tab.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = labelColour,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

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
    // Claim-counted, not a boolean: during a nav transition the incoming screen composes before
    // the outgoing one disposes, so Detail's onDispose used to stomp the shorts pager's `true`
    // (nav bar stayed up on shorts opened from Similar). Last-writer-wins can't express overlap.
    private var claims by mutableStateOf(0)
    val active: Boolean get() = claims > 0
    fun acquire() { claims++ }
    fun release() { if (claims > 0) claims-- }
}

/** System-bar insets as last DELIVERED to the window -- fed by MainActivity's decor listener,
 *  never read from a cache. The one inset source the scaffold trusts; see its usage comment. */
object SystemBarInsetsState {
    var insets by mutableStateOf(WindowInsets(0))
        private set

    // Last portrait-shaped delivery (no side insets, real top): the known-good set
    // [restoreLatched] falls back to when the OS skips redelivery after a fullscreen exit.
    private var portraitLatch: WindowInsets? = null

    fun update(left: Int, top: Int, right: Int, bottom: Int) {
        val fresh = WindowInsets(left, top, right, bottom)
        insets = fresh
        if (left == 0 && right == 0 && top > 0) portraitLatch = fresh
    }

    /** PipePipe's own cure for this OS bug, translated: it zeroes the inset padding BY HAND on
     *  fullscreen exit ("Android will not do it when orientation changes from landscape to
     *  portrait" -- their comment). Restore the latched portrait set instead of waiting for a
     *  dispatch that may never come; a genuine dispatch afterwards still wins via [update]. */
    fun restoreLatched() {
        portraitLatch?.let { insets = it }
    }
}

/** Which nav item is lit. detail/listing/settings light nothing — they're reachable from more than one tab. */
private fun selectedTab(route: String?): String? = when (route) {
    Routes.HOME -> Routes.HOME
    Routes.SHORTS -> Routes.SHORTS
    Routes.LIBRARY, Routes.PLAYLIST -> Routes.LIBRARY
    Routes.DOWNLOADS -> Routes.DOWNLOADS
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
