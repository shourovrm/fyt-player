package com.fyiplayer.app.player

/**
 * Text for the quality button. [isAuto] wins over [selectedHeight] because the height is still
 * the one the ceiling picked after the user chose Auto; only [isAuto] says the choice was Auto.
 * A null height with no Auto choice means nothing is known yet, so the button shows [autoText]
 * instead of a guessed height.
 */
fun qualityLabel(selectedHeight: Int?, isAuto: Boolean, autoText: String): String =
    if (isAuto || selectedHeight == null) autoText else "${selectedHeight}p"
