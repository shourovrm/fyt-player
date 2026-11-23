package com.fyiplayer.app.player

/**
 * Text for the quality button. [isAuto] wins over [selectedHeight]: on Auto the height is only
 * what the player is on right now, and the button is too small for both (the quality sheet shows
 * it). A null height with no Auto choice means nothing is known yet, so the button shows
 * [autoText] instead of a guessed height.
 */
fun qualityLabel(selectedHeight: Int?, isAuto: Boolean, autoText: String): String =
    if (isAuto || selectedHeight == null) autoText else "${selectedHeight}p"
