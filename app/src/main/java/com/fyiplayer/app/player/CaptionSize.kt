package com.fyiplayer.app.player

/**
 * One of four caption text sizes. [scale] multiplies Media3's default caption size, so [MEDIUM]
 * (1.0) is exactly the size captions had before this setting existed.
 */
enum class CaptionSize(val scale: Float) {
    SMALL(0.8f),
    MEDIUM(1.0f),
    LARGE(1.3f),
    EXTRA_LARGE(1.7f);

    companion object {
        val DEFAULT: CaptionSize = MEDIUM

        /**
         * Reads a stored [CaptionSize.name]. An unknown or missing name falls back to [DEFAULT]
         * rather than failing, so a pref written by a future build with a renamed step still opens.
         */
        fun fromName(name: String?): CaptionSize = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
