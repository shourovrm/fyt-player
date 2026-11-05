package com.fyiplayer.app.ui.theme

import androidx.compose.ui.graphics.Color

// Monochrome: the accent is the ink itself. Red is reserved for LIVE badges and errors.
// Dark tokens — the product default. True black for OLED panels, ink-coloured accent.
val DarkBg = Color(0xFF000000)
val DarkSurface1 = Color(0xFF101012)
val DarkSurface2 = Color(0xFF1A1A1F)
val DarkSurface3 = Color(0xFF2A2A31)
val DarkOutline = Color(0xFF24242A)
val DarkInk = Color(0xFFF1F1F1)
val DarkInk2 = Color(0xFFAAAAAA)
// Ink3 is text on Surface3 too, so it must reach 4.5:1 there (0x969696 gives about 4.8:1).
val DarkInk3 = Color(0xFF969696)
val DarkAccent = Color(0xFFF1F1F1)
val DarkOnAccent = Color(0xFF0F0F0F)
// Dimmed accent (about 12% alpha) — selection backgrounds only, never text.
val DarkAccentDim = Color(0x1FF1F1F1)
val DarkPositive = Color(0xFF6CC98F)
val DarkWarning = Color(0xFFD9A441)
val DarkDestructive = Color(0xFFFF7B6B)

// Light tokens — flat white page, ink-coloured accent.
val LightBg = Color(0xFFFFFFFF)
val LightSurface1 = Color(0xFFFFFFFF)
val LightSurface2 = Color(0xFFF2F2F2)
val LightSurface3 = Color(0xFFE9E9E9)
val LightOutline = Color(0xFFE5E5E5)
val LightInk = Color(0xFF0F0F0F)
val LightInk2 = Color(0xFF606060)
// Ink3 is text on Surface3 too, so it must reach 4.5:1 there (0x666666 gives about 4.7:1).
val LightInk3 = Color(0xFF666666)
val LightAccent = Color(0xFF0F0F0F)
val LightOnAccent = Color(0xFFFFFFFF)
// Ink at about 8% alpha — selection backgrounds only, never text.
val LightAccentDim = Color(0x140F0F0F)
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

// Icon colours: a fixed hue per role, the same on every page. Glyphs only; text stays ink.
// Dark values are lifted so they keep contrast on true black.
val LightNavHome = Color(0xFFE53935)
val DarkNavHome = Color(0xFFFF6B63)
val LightNavShorts = Color(0xFFE65100)
val DarkNavShorts = Color(0xFFFFA726)
val LightNavLibrary = Color(0xFFD81B60)
val DarkNavLibrary = Color(0xFFF06292)
val LightNavDownloads = Color(0xFF2E7D32)
val DarkNavDownloads = Color(0xFF66BB6A)
val LightLike = Color(0xFFE53935)
val DarkLike = Color(0xFFFF6B63)
val LightSave = Color(0xFFC77700)
val DarkSave = Color(0xFFFFCA28)
val LightDownload = Color(0xFF2E7D32)
val DarkDownload = Color(0xFF66BB6A)
val LightShare = Color(0xFF1E88E5)
val DarkShare = Color(0xFF64B5F6)
val LightSponsor = Color(0xFF7E57C2)
val DarkSponsor = Color(0xFFB39DDB)

// Settings hub tiles: one fill per row, identical in both themes, with a white glyph on top.
val TileAccount = Color(0xFF1E88E5)
val TileContent = Color(0xFF00897B)
val TilePlayback = Color(0xFFE53935)
val TileSkipping = Color(0xFF7E57C2)
val TileDownloads = Color(0xFF2E7D32)
val TileHistory = Color(0xFFC77700)
val TileBackup = Color(0xFF5C6BC0)
val TileAbout = Color(0xFF757575)
val OnIconTile = Color(0xFFFFFFFF)
