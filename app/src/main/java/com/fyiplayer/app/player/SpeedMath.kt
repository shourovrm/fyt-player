package com.fyiplayer.app.player

import kotlin.math.abs
import kotlin.math.roundToInt

internal const val SPEED_MIN = 0.1f
internal const val SPEED_MAX = 3f
internal const val SPEED_STEP = 0.05f

/** The marks the slider settles on: 0.25, 0.5 ... 3. */
private const val MAJOR_STEP = 0.25f

/** Clamps to 0.1-3 and rounds to the nearest 0.05, so repeated nudges never drift (0.1 + 0.05 + ...). */
internal fun roundSpeed(speed: Float): Float =
    ((speed.coerceIn(SPEED_MIN, SPEED_MAX) / SPEED_STEP).roundToInt() * SPEED_STEP)
        .let { (it * 100).roundToInt() / 100f }

/** Magnet for the slider: a value within one step of a major mark lands on the mark. */
internal fun snapSpeed(speed: Float): Float {
    val rounded = roundSpeed(speed)
    val major = (rounded / MAJOR_STEP).roundToInt() * MAJOR_STEP
    return if (major >= SPEED_MIN && abs(major - rounded) <= SPEED_STEP + 0.001f) major else rounded
}

/** "1x", "1.5x", "0.35x": no trailing zeros. */
internal fun formatSpeed(speed: Float): String {
    val hundredths = (roundSpeed(speed) * 100).roundToInt()
    val whole = hundredths / 100
    val fraction = hundredths % 100
    return when {
        fraction == 0 -> "${whole}x"
        fraction % 10 == 0 -> "$whole.${fraction / 10}x"
        else -> "$whole.${fraction.toString().padStart(2, '0')}x"
    }
}
