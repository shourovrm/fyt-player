package com.fyiplayer.app.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Semantic colours with no Material3 ColorScheme slot: positive/warning states, the
 * third ink tier and the third surface tier. Kept out of the scheme because the app has
 * exactly one deliberate accent and these are status/elevation colours, not brand colour.
 */
data class FyiExtraColors(
    val positive: Color,
    val warning: Color,
    val ink3: Color,
    val surface3: Color,
    val onVideo: Color = OnVideo,
    val onVideoDim: Color = OnVideoDim,
    val videoScrim: Color = VideoScrim,
    val videoScrimLight: Color = VideoScrimLight,
    val videoScrimFaint: Color = VideoScrimFaint,
    val likedOnVideo: Color = LikedOnVideo,
)

private val LocalFyiExtraColors = staticCompositionLocalOf {
    FyiExtraColors(
        positive = DarkPositive,
        warning = DarkWarning,
        ink3 = DarkInk3,
        surface3 = DarkSurface3,
    )
}

val MaterialTheme.fyiExtras: FyiExtraColors
    @Composable
    get() = LocalFyiExtraColors.current

// surfaceContainer*/secondaryContainer are what NavigationBar, bottom sheets, menus and dialogs
// read for their containers and selection indicator. Left undefined, darkColorScheme()/lightColorScheme() fill them from
// Material's baseline purple palette independently of `primary`, so the bottom nav renders
// lavender chrome over otherwise on-brand surfaces. Pinning them here keeps nav on-brand.
private val FyiDarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkOnAccent,
    secondaryContainer = DarkAccentDim,
    onSecondaryContainer = DarkAccent,
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkSurface1,
    onSurface = DarkInk,
    surfaceVariant = DarkSurface2,
    onSurfaceVariant = DarkInk2,
    surfaceContainerLowest = DarkBg,
    surfaceContainerLow = DarkSurface1,
    surfaceContainer = DarkSurface1,
    surfaceContainerHigh = DarkSurface2,
    surfaceContainerHighest = DarkSurface3,
    // Transparent: elevation must not tint surfaces. Flat is the monochrome look.
    surfaceTint = Color.Transparent,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = DarkDestructive,
)

private val FyiLightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightOnAccent,
    secondaryContainer = LightAccentDim,
    onSecondaryContainer = LightAccent,
    background = LightBg,
    onBackground = LightInk,
    surface = LightSurface1,
    onSurface = LightInk,
    surfaceVariant = LightSurface2,
    onSurfaceVariant = LightInk2,
    surfaceContainerLowest = LightBg,
    surfaceContainerLow = LightSurface1,
    surfaceContainer = LightSurface1,
    surfaceContainerHigh = LightSurface2,
    surfaceContainerHighest = LightSurface3,
    // Transparent: elevation must not tint white surfaces. Flat is the monochrome look.
    surfaceTint = Color.Transparent,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = LightDestructive,
)

/**
 * Square-ish corners everywhere: no pill shapes. Material buttons, chips and segmented buttons
 * default to fully rounded ends regardless of this scale, so they pass [ControlShape] explicitly.
 */
private val FyiShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.xs),
    medium = RoundedCornerShape(Radius.s),
    large = RoundedCornerShape(Radius.m),
    extraLarge = RoundedCornerShape(Radius.xl),
)

/** Buttons, chips, segmented controls, switch tracks, the search field and nav indicators. */
val ControlShape = RoundedCornerShape(Radius.xs)

/**
 * Dark is the product default: unlike [androidx.compose.foundation.isSystemInDarkTheme],
 * an undefined platform signal (UI_MODE_NIGHT_UNDEFINED) resolves to dark here, not light.
 */
@Composable
private fun systemPrefersDarkByDefault(): Boolean {
    val uiMode = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return uiMode != Configuration.UI_MODE_NIGHT_NO
}

@Composable
fun FyiTheme(
    darkTheme: Boolean = systemPrefersDarkByDefault(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) FyiDarkColorScheme else FyiLightColorScheme
    val extras = if (darkTheme) {
        FyiExtraColors(positive = DarkPositive, warning = DarkWarning, ink3 = DarkInk3, surface3 = DarkSurface3)
    } else {
        FyiExtraColors(positive = LightPositive, warning = LightWarning, ink3 = LightInk3, surface3 = LightSurface3)
    }
    // No dynamic/Material-You colour: one deliberate accent, wallpaper never overrides it.
    CompositionLocalProvider(LocalFyiExtraColors provides extras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FyiTypography,
            shapes = FyiShapes,
            content = content,
        )
    }
}
