package com.fyiplayer.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// System font stack only — no bundled or downloaded font.
private val base = TextStyle(fontFamily = FontFamily.Default)

// Eight-step scale mapped onto the closest-fitting Material3 slots; unmapped slots
// keep Typography()'s own defaults, which are already FontFamily.Default.
val FyiTypography = Typography(
    headlineSmall = base.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), // screen headings
    titleLarge = base.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold), // app bar title
    titleMedium = base.copy(fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold), // video title
    bodyLarge = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal), // row title
    bodyMedium = base.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal), // body, result titles
    bodySmall = base.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal), // secondary row values
    labelMedium = base.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium), // summaries, values
    labelSmall = base.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium), // chips, durations
)
