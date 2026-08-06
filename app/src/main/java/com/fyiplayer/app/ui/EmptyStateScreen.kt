package com.fyiplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.ui.theme.fyiExtras

/**
 * Quiet empty state: a title and one explanatory line, centered. Scrollable only so the shell's
 * hoisted nested-scroll connection still has something to react to. Deliberately no placeholder
 * rows — filler boxes read as broken loading, not as emptiness.
 */
@Composable
fun EmptyStateScreen(title: String, message: String) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(start = 32.dp, top = 160.dp, end = 32.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.fyiExtras.ink3,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
