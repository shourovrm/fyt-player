package com.fyiplayer.app.ui

import com.fyiplayer.app.player.PauseGlyph
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fyiplayer.app.R
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.repo.DownloadItem
import com.fyiplayer.app.data.repo.DownloadState
import com.fyiplayer.app.data.repo.youtubeThumbnailFor
import com.fyiplayer.app.download.DownloadProgress
import com.fyiplayer.app.download.DownloadQueue
import com.fyiplayer.app.player.PlaybackSession
import com.fyiplayer.app.ui.theme.NumeralFontFamily
import com.fyiplayer.app.ui.theme.Radius
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import com.fyiplayer.app.ui.theme.fyiExtras
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The download queue: progress per row, pause/resume/cancel/retry, play and delete of finished
 * downloads (one row, or a multi-selection). [DownloadQueue]
 * is a process-scoped singleton (see its doc) so this screen can be entered/left freely without
 * losing track of what's running -- no arguments needed, matching every other bottom-nav tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onOpenDetail: (VideoRef) -> Unit) {
    val context = LocalContext.current
    val queue = remember(context) { DownloadQueue.get(context) }
    val rows by queue.rows.collectAsStateWithLifecycle()
    val errors by queue.errors.collectAsStateWithLifecycle()
    val progress by queue.progress.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // No sheet defaults to the destructive choice -- see RemoveSheet.
    var menuTarget by remember { mutableStateOf<DownloadItem?>(null) }
    var removeTarget by remember { mutableStateOf<DownloadItem?>(null) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    // Page URLs of selected finished rows. Saved so a rotation keeps the selection.
    var selection by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    val selecting = selection.isNotEmpty()
    BackHandler(enabled = selecting) { selection = emptySet() }

    // Only a finished download can be played or selected; the rest of the queue has per-row buttons.
    val completed = rows.filter { it.state == DownloadState.COMPLETED }
    val completedVideos = completed.map { it.ref }
    val completedKeys = completedVideos.map { it.pageUrl }
    val downloadsTitle = stringResource(R.string.downloads_title)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (selecting) {
                SelectionTopBar(
                    count = selection.size,
                    onClose = { selection = emptySet() },
                    onSelectAll = { selection = selectAllOrNoneByKey(completedKeys, selection) },
                    allState = selectionStateOf(completedKeys, selection),
                    actions = {
                        IconButton(onClick = {
                            val refs = selectedInOrder(completedVideos, selection)
                            selection = emptySet()
                            if (refs.isNotEmpty()) PlaybackSession.play(refs, 0, queueTitle = downloadsTitle)
                        }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.downloads_play_selected))
                        }
                        IconButton(onClick = { confirmDeleteSelected = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = pluralStringResource(R.plurals.downloads_delete_files, selection.size))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(downloadsTitle) },
                    actions = {
                        if (completed.isNotEmpty()) {
                            IconButton(onClick = { PlaybackSession.play(completedVideos, 0, queueTitle = downloadsTitle) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.downloads_play_all))
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (rows.isEmpty()) {
                DownloadsEmptyState()
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = Spacing.s8),
                ) {
                    items(rows, key = { it.ref.pageUrl }) { item ->
                        DownloadRow(
                            item = item,
                            error = errors[item.ref.pageUrl],
                            liveProgress = progress[item.ref.pageUrl],
                            selected = item.ref.pageUrl in selection,
                            selecting = selecting,
                            onClick = {
                                if (selecting) selection = selection.toggled(item.ref.pageUrl) else onOpenDetail(item.ref)
                            },
                            onLongPress = { selection = selection.toggled(item.ref.pageUrl) },
                            onPause = { scope.launch { queue.pause(item.ref.pageUrl) } },
                            onResume = { scope.launch { queue.resume(item.ref.pageUrl) } },
                            onRemove = { removeTarget = item },
                            onRetry = { scope.launch { queue.retry(item.ref.pageUrl) } },
                            onMore = { menuTarget = item },
                        )
                    }
                }
            }
        }
    }

    menuTarget?.let { item ->
        DownloadMenuSheet(
            onOpenWithApp = { openDownloadedFile(context, item.filePath); menuTarget = null },
            onRemove = { removeTarget = item; menuTarget = null },
            onDismiss = { menuTarget = null },
        )
    }

    removeTarget?.let { item ->
        val fileDeleteFailedMessage = stringResource(R.string.downloads_delete_failed)
        RemoveSheet(
            title = stringResource(R.string.downloads_remove_title),
            body = removeBody(item.totalBytes, fileCount = 1),
            removeLabel = stringResource(R.string.downloads_remove_keep_file),
            deleteLabel = pluralStringResource(R.plurals.downloads_delete_files, 1),
            onRemove = { scope.launch { queue.cancel(item.ref.pageUrl) }; removeTarget = null },
            onDeleteToo = {
                scope.launch {
                    if (!queue.cancelAndDelete(item.ref.pageUrl)) showToast(context, fileDeleteFailedMessage)
                }
                removeTarget = null
            },
            onDismiss = { removeTarget = null },
        )
    }

    if (confirmDeleteSelected) {
        val selected = completed.filter { it.ref.pageUrl in selection }
        val someFilesNotDeletedMessage = stringResource(R.string.downloads_delete_some_failed)
        RemoveSheet(
            title = pluralStringResource(R.plurals.downloads_remove_selected_title, selected.size, selected.size),
            body = removeBody(selected.sumOf { it.totalBytes }, fileCount = selected.size),
            removeLabel = stringResource(R.string.downloads_remove_keep_file),
            deleteLabel = pluralStringResource(R.plurals.downloads_delete_files, selected.size),
            onRemove = {
                val keys = selected.map { it.ref.pageUrl }
                selection = emptySet()
                confirmDeleteSelected = false
                scope.launch { keys.forEach { queue.cancel(it) } }
            },
            onDeleteToo = {
                val keys = selected.map { it.ref.pageUrl }
                selection = emptySet()
                confirmDeleteSelected = false
                scope.launch {
                    val deleted = keys.map { queue.cancelAndDelete(it) }
                    if (deleted.any { !it }) showToast(context, someFilesNotDeletedMessage)
                }
            },
            onDismiss = { confirmDeleteSelected = false },
        )
    }
}

/** The only empty state on this screen -- no placeholder rows, since there's nothing "loading"
 *  about an empty queue. The glyph is a text character, not an Icon: material-icons-core has no
 *  download-tray shape. */
@Composable
private fun DownloadsEmptyState() {
    val extras = MaterialTheme.fyiExtras
    Column(
        Modifier.fillMaxSize().padding(top = Spacing.s64, start = Spacing.s32, end = Spacing.s32),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(Spacing.s56).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("⬇", style = MaterialTheme.typography.titleMedium, color = extras.ink3)
        }
        Spacer(Modifier.height(Spacing.s16))
        Text(stringResource(R.string.downloads_empty_title), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun removeBody(totalBytes: Long, fileCount: Int): String {
    if (totalBytes <= 0) return pluralStringResource(R.plurals.downloads_remove_body, fileCount)
    return stringResource(R.string.downloads_remove_frees, formatBytes(totalBytes))
}

/**
 * The shared sheet for both destructive removals (per-row and clear-completed). The safe "remove
 * from list" row sits first, the file-deleting row second in error colour: deleting a file is
 * irreversible, so it is never the default and never the first thing a thumb lands on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemoveSheet(
    title: String,
    body: String,
    removeLabel: String,
    deleteLabel: String,
    onRemove: () -> Unit,
    onDeleteToo: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.pageMargin),
            )
            SheetRow(label = removeLabel, color = MaterialTheme.colorScheme.onSurface, onClick = onRemove)
            SheetRow(label = deleteLabel, color = MaterialTheme.colorScheme.error, onClick = onDeleteToo) {
                Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
            SheetRow(label = stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurface, onClick = onDismiss)
        }
    }
}

/** The per-row menu for a finished download. Both rows are plain actions, so neither is in error colour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadMenuSheet(onOpenWithApp: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            SheetRow(label = stringResource(R.string.downloads_open_with), color = MaterialTheme.colorScheme.onSurface, onClick = onOpenWithApp)
            SheetRow(label = stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.onSurface, onClick = onRemove)
        }
    }
}

@Composable
private fun SheetRow(label: String, color: Color, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Sizes.listOneLine)
            .padding(horizontal = Spacing.pageMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    error: String?,
    liveProgress: DownloadProgress?,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    onMore: () -> Unit,
) {
    val extras = MaterialTheme.fyiExtras
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            // Only a finished download opens or selects; every other state is controlled by its own buttons.
            .combinedClickable(
                enabled = item.state == DownloadState.COMPLETED,
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .semantics { if (selecting) this.selected = selected }
            .heightIn(min = Sizes.listTwoLine)
            .padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row is the click target, so the box only shows state. An unfinished row gets a disabled box.
        if (selecting) {
            Checkbox(
                checked = selected,
                onCheckedChange = null,
                enabled = item.state == DownloadState.COMPLETED,
                modifier = Modifier.padding(end = Spacing.s16),
            )
        }
        Box(
            Modifier.size(Sizes.thumbWidth, Sizes.thumbHeight).clip(RoundedCornerShape(Radius.m)).background(extras.surface3),
            contentAlignment = Alignment.Center,
        ) {
            // Same fallback ResultsList.kt's ResultRow uses for a Library/History row with no
            // stored thumbnail -- youtubeThumbnailFor no-ops for a non-YouTube pageUrl.
            val thumbnailUrl = item.ref.thumbnailUrl ?: youtubeThumbnailFor(item.ref.pageUrl)
            if (thumbnailUrl != null) {
                AsyncImage(model = thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            when (item.state) {
                DownloadState.RUNNING -> ProgressRing(
                    progress = progressFraction(item),
                    label = stringResource(R.string.downloads_pause),
                    onClick = onPause,
                ) {
                    PauseGlyph(tint = MaterialTheme.colorScheme.onSurface)
                }
                DownloadState.PAUSED -> ProgressRing(
                    progress = progressFraction(item),
                    label = stringResource(R.string.downloads_resume),
                    onClick = onResume,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                }
                else -> Unit
            }
        }
        Column(Modifier.padding(start = Spacing.s16).weight(1f)) {
            Text(
                item.ref.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // Failure is the one state worth a colour, and colour goes on a badge, not on body text.
                if (item.state == DownloadState.FAILED) {
                    Text(
                        stringResource(R.string.downloads_state_failed),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(Radius.xs))
                            .background(MaterialTheme.colorScheme.error)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                Text(
                    statusLine(item, error, liveProgress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (item.state) {
                DownloadState.QUEUED, DownloadState.RUNNING, DownloadState.PAUSED -> IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                }
                DownloadState.FAILED -> {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_retry))
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_clear))
                    }
                }
                DownloadState.COMPLETED -> if (!selecting) {
                    IconButton(onClick = onMore) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                }
            }
        }
    }
}

/**
 * The progress ring is the pause/resume button: a 48 dp target with the ring drawn around its
 * 24 dp glyph, so the ring shows how far the download has got and the tap changes its state.
 */
@Composable
private fun ProgressRing(progress: Float, label: String, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    val extras = MaterialTheme.fyiExtras
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(Sizes.touchTarget).semantics { contentDescription = label },
        colors = IconButtonDefaults.iconButtonColors(containerColor = extras.videoScrim, contentColor = extras.onVideo),
    ) {
        Box(Modifier.size(Sizes.button), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = extras.onVideoDim,
            )
            glyph()
        }
    }
}

private fun progressFraction(item: DownloadItem): Float =
    if (item.totalBytes > 0) (item.bytesDownloaded.toFloat() / item.totalBytes).coerceIn(0f, 1f) else 0f

/** Words in the UI face, numbers in the numeral face, joined with a middle dot. */
private fun statusText(word: String?, numbers: List<String>): AnnotatedString = buildAnnotatedString {
    if (word != null) append(word)
    numbers.forEachIndexed { index, number ->
        if (word != null || index > 0) append(" · ")
        withStyle(SpanStyle(fontFamily = NumeralFontFamily)) { append(number) }
    }
}

@Composable
private fun statusLine(item: DownloadItem, error: String?, liveProgress: DownloadProgress?): AnnotatedString {
    return when (item.state) {
        DownloadState.QUEUED -> statusText(stringResource(R.string.downloads_state_queued), emptyList())
        DownloadState.RUNNING -> {
            val percent = (progressFraction(item) * 100).roundToInt()
            val amount = if (item.totalBytes > 0) {
                stringResource(R.string.downloads_progress_of, formatBytes(item.bytesDownloaded), formatBytes(item.totalBytes))
            } else {
                formatBytes(item.bytesDownloaded)
            }
            val speed = liveProgress?.speedBytesPerSecond?.takeIf { it > 0 }
                ?.let { stringResource(R.string.downloads_speed, formatBytes(it.toLong())) }
            val remaining = liveProgress?.etaSeconds?.takeIf { it > 0 }
                ?.let { stringResource(R.string.downloads_time_left, formatDuration(it.toInt())) }
            statusText(null, listOfNotNull("$percent%", amount, speed, remaining))
        }
        DownloadState.PAUSED -> statusText(stringResource(R.string.downloads_state_paused), listOf(formatBytes(item.bytesDownloaded)))
        DownloadState.COMPLETED -> {
            val size = if (item.totalBytes > 0) formatBytes(item.totalBytes) else null
            val duration = downloadDuration(item)?.let { formatDuration(it) }
            // Completed is the normal state, so the line carries no state word.
            statusText(null, listOfNotNull(size, duration))
        }
        // The Failed badge beside this line already names the state; only the reason is left.
        DownloadState.FAILED -> AnnotatedString(error.orEmpty())
    }
}

/** Wall-clock time from first RUNNING to COMPLETED (a paused stretch counts too -- "how long this
 *  took", not "how long the engine was actually busy"). Null until both timestamps exist. */
private fun downloadDuration(item: DownloadItem): Int? {
    val started = item.startedAt ?: return null
    val finished = item.finishedAt ?: return null
    return ((finished - started) / 1000).toInt().coerceAtLeast(0)
}

// internal, not private: [DownloadQualityDialog] (same package) formats approximate sizes too.
internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}

/**
 * Hand a finished download to an app the user picks from the chooser. The file lives in this app's own
 * external files dir, so it can only cross a process boundary as a content:// Uri with a
 * read grant — ACTION_VIEW on a file:// Uri throws FileUriExposedException on this targetSdk.
 */
private fun openDownloadedFile(context: Context, filePath: String?) {
    val file = filePath?.let(::File)
    if (file == null || !file.exists()) {
        Toast.makeText(context, context.getString(R.string.downloads_file_missing), Toast.LENGTH_LONG).show()
        return
    }
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "video/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, null))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.downloads_no_app), Toast.LENGTH_LONG).show()
    } catch (e: IllegalArgumentException) {
        // getUriForFile throws when the file sits outside every declared provider root.
        Toast.makeText(context, context.getString(R.string.downloads_cannot_open), Toast.LENGTH_LONG).show()
    }
}
