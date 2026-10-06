package com.fyiplayer.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.core.SearchFilter
import com.fyiplayer.app.core.SearchSort
import com.fyiplayer.app.core.SearchType

/** Result type chips, then sort chips, in one scrolling row above the search results. Shown only
 *  when a source in the search honours the filter ([com.fyiplayer.app.core.VideoSource.providesSearchFilters]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchFilterRow(filter: SearchFilter, onChange: (SearchFilter) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchType.entries.forEach { type ->
            FilterChip(
                selected = filter.type == type,
                onClick = { onChange(filter.copy(type = type)) },
                label = { Text(type.label) },
            )
        }
        SearchSort.entries.forEach { sort ->
            FilterChip(
                selected = filter.sort == sort,
                onClick = { onChange(filter.copy(sort = sort)) },
                label = { Text(sort.label) },
            )
        }
    }
}
