package com.fyiplayer.app.ui.theme

import androidx.compose.ui.graphics.Color

// Dark tokens — the product default. Neutral near-black surfaces, one accent hue.
val DarkBg = Color(0xFF0F1113)
val DarkSurface1 = Color(0xFF16181B)
val DarkSurface2 = Color(0xFF1D2024)
val DarkSurface3 = Color(0xFF262A2F)
val DarkOutline = Color(0xFF2B2F35)
val DarkInk = Color(0xFFE8EAED)
val DarkInk2 = Color(0xFFA9B0B8)
val DarkInk3 = Color(0xFF8A929C)
val DarkAccent = Color(0xFF5AB0E8)
val DarkOnAccent = Color(0xFF04202F)
// Dimmed accent (14% alpha) — nav selection pill only, never text.
val DarkAccentDim = Color(0x245AB0E8)
val DarkPositive = Color(0xFF6CC98F)
val DarkWarning = Color(0xFFD9A441)
val DarkDestructive = Color(0xFFE0736F)

// Light tokens — same structure, contrast-flipped.
val LightBg = Color(0xFFF2F3F5)
val LightSurface1 = Color(0xFFFFFFFF)
val LightSurface2 = Color(0xFFE9EBEE)
val LightSurface3 = Color(0xFFDDE1E6)
val LightOutline = Color(0xFFD5D9DE)
val LightInk = Color(0xFF1A1C1F)
val LightInk2 = Color(0xFF4B5158)
val LightInk3 = Color(0xFF5F666E)
val LightAccent = Color(0xFF0B6B96)
val LightOnAccent = Color(0xFFFFFFFF)
val LightAccentDim = Color(0x1F0B6B96)
val LightPositive = Color(0xFF1D7D43)
val LightWarning = Color(0xFF8A5A00)
val LightDestructive = Color(0xFFB3352F)

// Over-video roles, theme-independent: video is always dark, so ink stays white and the scrim
// stays black in both schemes. Badges, player chrome and the Shorts rail use these, never
// Color.White / Color.Black directly.
val OnVideo = Color(0xFFF2F4F5)
val OnVideoDim = Color(0xB3F2F4F5)
val VideoScrim = Color(0xB8000000)
val VideoScrimLight = Color(0x73000000)
val VideoScrimFaint = Color(0x59000000)
// Marks a liked state over video (Shorts rail). Not a status colour: an identity swatch.
val LikedOnVideo = Color(0xFFE0245E)
