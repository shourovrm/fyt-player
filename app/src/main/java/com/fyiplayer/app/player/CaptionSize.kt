package com.fyiplayer.app.player

/**
 * One of four caption text sizes, in typographic points (1/72 inch). A fixed size, not a fraction
 * of the video height: a tall Short would otherwise get captions several times bigger than a
 * landscape video's.
 */
enum class CaptionSize(val points: Float) {
    SMALL(8f),
    MEDIUM(10f),
    LARGE(14f),
    EXTRA_LARGE(16f);

    companion object {
        val DEFAULT: CaptionSize = MEDIUM

        /**
         * Reads a stored [CaptionSize.name]. An unknown or missing name falls back to [DEFAULT]
         * rather than failing, so a pref written by a future build with a renamed step still opens.
         */
        fun fromName(name: String?): CaptionSize = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
