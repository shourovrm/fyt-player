package com.fyiplayer.app.ui.theme

import androidx.compose.ui.text.TextStyle

/** A type role at a multiple of its size: the one place a font size is changed outside Type.kt, so
 *  callers keep to the role and the gate test's "no fontSize override" rule still holds. */
fun TextStyle.scaledBy(factor: Float): TextStyle = copy(fontSize = fontSize * factor)
