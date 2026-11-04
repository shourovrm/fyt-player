package com.fyiplayer.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.fyiplayer.app.R

// Both bundled files are variable fonts (wght axis); one file serves every weight.
@OptIn(ExperimentalTextApi::class)
private fun variableWeights(resId: Int, vararg weights: FontWeight): FontFamily = FontFamily(
    weights.map { weight ->
        Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)

/** Schibsted Grotesk (OFL): every text role. */
val UiFontFamily = variableWeights(
    R.font.schibsted_grotesk, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold,
)

/** Spline Sans Mono (OFL): times, durations, sizes, speeds and counts, so digits line up as they change. */
val NumeralFontFamily = variableWeights(R.font.spline_sans_mono, FontWeight.Normal, FontWeight.Medium)

private val base = TextStyle(fontFamily = UiFontFamily)

/** Numbers in their own face; pair with a type role's size, e.g. `bodySmall.merge(NumeralStyle)`. */
val NumeralStyle = TextStyle(fontFamily = NumeralFontFamily)

// Material 3 type scale, values from androidx material3 1.4.0 TypeScaleTokens. Screens use these
// roles as-is: no `.copy(fontSize = …)` anywhere else (DesignTokensGateTest enforces it).
val FyiTypography = Typography(
    titleLarge = base.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold), // app bar, dialog title
    titleMedium = base.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.15.sp), // video title
    titleSmall = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.5.sp), // row title, field text
    bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.25.sp), // body, settings label
    bodySmall = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.4.sp), // meta, hints
    labelLarge = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp), // buttons, tabs
    labelMedium = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp), // section labels
    labelSmall = base.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp), // badges
)
