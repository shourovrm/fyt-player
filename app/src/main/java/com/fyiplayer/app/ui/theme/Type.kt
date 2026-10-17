package com.fyiplayer.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// System font stack only — no bundled or downloaded font.
private val base = TextStyle(fontFamily = FontFamily.Default)

// Material 3 type scale, values from androidx material3 1.4.0 TypeScaleTokens. Screens use these
// roles as-is: no `.copy(fontSize = …)` anywhere else (DesignTokensGateTest enforces it).
val FyiTypography = Typography(
    titleLarge = base.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal), // app bar, dialog title
    titleMedium = base.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.15.sp), // video title
    titleSmall = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.5.sp), // row title, field text
    bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.25.sp), // body, settings label
    bodySmall = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.4.sp), // meta, hints
    labelLarge = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp), // buttons, chips, tabs
    labelMedium = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp), // nav labels, section labels
    labelSmall = base.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp), // badges
)
