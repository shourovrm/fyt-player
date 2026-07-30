package com.fyiplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.ui.theme.fyiExtras

/**
 * Shared placeholder body for the phase-1 screens: a title, a short explanation, and enough
 * filler rows to be genuinely scrollable — a LazyColumn so the shell's hoisted nested-scroll
 * connection has something real to react to. Later phases replace the filler with actual rows.
 */
@Composable
fun EmptyStateScreen(title: String, message: String) {
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        item {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.fyiExtras.ink3)
            }
        }
        items(PLACEHOLDER_ROWS) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp)
                    .height(56.dp)
                    .background(MaterialTheme.fyiExtras.surface3, RoundedCornerShape(12.dp)),
            )
        }
    }
}

private const val PLACEHOLDER_ROWS = 20
