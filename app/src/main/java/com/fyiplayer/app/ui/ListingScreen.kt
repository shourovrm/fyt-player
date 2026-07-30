package com.fyiplayer.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fyiplayer.app.core.Listing
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.player.PlaybackSession

/** A channel or playlist listing (DESIGN.md §5), paged the same way as Home's single-source tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingScreen(listing: Listing, onOpenDetail: (VideoRef) -> Unit, onBack: () -> Unit) {
    val vm: ListingViewModel = viewModel()
    val listState = rememberLazyListState()
    var actionSheetRef by remember { mutableStateOf<VideoRef?>(null) }

    LaunchedEffect(listing) { vm.ensureLoaded(listing) }

    fun playAndOpen(ref: VideoRef) {
        val index = vm.items.indexOfFirst { it.pageUrl == ref.pageUrl }.coerceAtLeast(0)
        PlaybackSession.play(vm.items, index)
        onOpenDetail(ref)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(listing.title.ifBlank { "Listing" }, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        if (vm.items.isEmpty() && vm.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val errors = vm.error?.let {
                listOf(ErrorRow(listing.title, it, onRetry = if (vm.blocked) null else { { vm.retry(listing) } }))
            } ?: emptyList()
            ResultsListColumn(
                items = vm.items,
                errors = errors,
                hasMore = vm.nextPage != null,
                onLoadMore = { vm.loadMore(listing) },
                onClick = ::playAndOpen,
                onLongPress = { actionSheetRef = it },
                listState = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                isLoadingMore = vm.loading && vm.items.isNotEmpty(),
            )
        }
    }

    actionSheetRef?.let { ref -> VideoActionSheet(ref, onDismiss = { actionSheetRef = null }) }
}
