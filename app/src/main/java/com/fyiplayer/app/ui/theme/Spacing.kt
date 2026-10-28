package com.fyiplayer.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Every dp in the app comes from here, except the Material nested units 0/1/2/4/6/10 that the
// gate test allows inline (borders, hairlines, badge insets). Values are Material 3 (androidx
// material3 1.4.0): see docs/design-system.md. Add a token here rather than a literal elsewhere.

/** The 8 dp spacing scale (Material `space100` = 8 dp). */
object Spacing {
    val s8: Dp = 8.dp
    val s16: Dp = 16.dp
    val s24: Dp = 24.dp
    val s32: Dp = 32.dp
    val s40: Dp = 40.dp
    val s48: Dp = 48.dp
    val s56: Dp = 56.dp
    val s64: Dp = 64.dp

    /** Page margin under 600 dp width. */
    val pageMargin: Dp = s16
}

/** Component sizes. Named by role so a call site reads as intent, not as a number. */
object Sizes {
    val touchTarget: Dp = 48.dp
    val icon: Dp = 24.dp
    val iconSmall: Dp = 16.dp
    val iconLarge: Dp = 32.dp
    val transportGlyph: Dp = 40.dp
    val button: Dp = 40.dp
    val textField: Dp = 56.dp
    val searchBar: Dp = 56.dp
    val chip: Dp = 32.dp
    val listOneLine: Dp = 56.dp
    val listTwoLine: Dp = 72.dp
    val listThreeLine: Dp = 88.dp
    val navBar: Dp = 80.dp
    val miniPlayer: Dp = 72.dp
    val fab: Dp = 56.dp
    val transportButton: Dp = 64.dp
    val progressTrack: Dp = 4.dp
    val progressThin: Dp = 2.dp

    // 16:9 thumbnails snapped to the 4 dp grid.
    val thumbWidth: Dp = 120.dp
    val thumbHeight: Dp = 68.dp
    val miniThumbWidth: Dp = 96.dp
    val miniThumbHeight: Dp = 54.dp
    val shortsCardWidth: Dp = 160.dp
    val shortsCardHeight: Dp = 284.dp
    val channelAvatar: Dp = 56.dp
    val channelAvatarLarge: Dp = 88.dp
    val commentAvatar: Dp = 32.dp
    val seekPreviewWidth: Dp = 160.dp
    val seekPreviewHeight: Dp = 90.dp
    val sheetMaxWidth: Dp = 320.dp
    val dialogMinWidth: Dp = 280.dp
    val playerPortraitHeight: Dp = 220.dp

    // About page. The glyph is drawn on the adaptive icon's 108 dp canvas so its safe zone fits the tile.
    val aboutLogo: Dp = 72.dp
    val aboutLogoGlyph: Dp = 108.dp
    val actionButton: Dp = 56.dp
}

/** Material shape scale. */
object Radius {
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp
    val xl: Dp = 28.dp
}
