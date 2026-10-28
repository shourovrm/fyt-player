package com.fyiplayer.app.player

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.ui.theme.Radius
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.fyiExtras

/**
 * Quality text in an outlined badge. The outline is what makes it read as a button rather than
 * a caption. The badge sizes to its text, so a four-digit height is wider than 40dp and the
 * caller's touch target must grow with it instead of being a fixed square.
 */
@Composable
fun QualityBadge(text: String) {
    val extras = MaterialTheme.fyiExtras
    Box(
        modifier = Modifier
            .border(Sizes.badgeBorder, extras.onVideo, RoundedCornerShape(Radius.xs))
            // Vertical inset keeps the outline clear of the label glyphs; both are gate-allowed insets.
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = extras.onVideo, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
