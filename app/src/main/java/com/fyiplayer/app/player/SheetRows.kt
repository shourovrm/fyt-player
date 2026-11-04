package com.fyiplayer.app.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import com.fyiplayer.app.R
import com.fyiplayer.app.ui.theme.Spacing

/** Title row of a sheet: [title] in titleLarge, then a close icon that calls [onClose]. */
@Composable
fun SheetTitleRow(title: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Spacing.pageMargin, end = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
        }
    }
}

/** One choice in a sheet list. The selected row shows a check mark in the accent colour. */
@Composable
fun ChoiceListItem(
    headline: String,
    selected: Boolean,
    onSelect: () -> Unit,
    headlineStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    ListItem(
        headlineContent = { Text(headline, style = headlineStyle) },
        trailingContent = {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    )
}
