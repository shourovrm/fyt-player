package com.fyiplayer.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fyiplayer.app.R
import com.fyiplayer.app.data.repo.DownloadItem
import com.fyiplayer.app.data.repo.DownloadState
import com.fyiplayer.app.data.repo.youtubeThumbnailFor
import com.fyiplayer.app.download.DownloadProgress
import com.fyiplayer.app.download.DownloadQueue
import com.fyiplayer.app.ui.theme.Radius
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing
import com.fyiplayer.app.ui.theme.fyiExtras
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The download queue: progress per row, pause/resume/cancel/retry, clear-completed. [DownloadQueue]
 * is a process-scoped singleton (see its doc) so this screen can be entered/left freely without
 * losing track of what's running -- no arguments needed, matching every other bottom-nav tab.
 */
@Composable
fun DownloadsScreen() {
    val context = LocalContext.current
    val queue = remember(context) { DownloadQueue.get(context) }
    val rows by queue.rows.collectAsStateWithLifecycle()
    val errors by queue.errors.collectAsStateWithLifecycle()
    val progress by queue.progress.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Neither dialog defaults to the destructive choice -- see RemoveConfirmDialog.
    var removeTarget by remember { mutableStateOf<DownloadItem?>(null) }
    var confirmClearCompleted by remember { mutableStateOf(false) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.pageMargin, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.downloads_title), style = MaterialTheme.typography.titleLarge)
                if (rows.any { it.state == DownloadState.COMPLETED }) {
                    TextButton(onClick = { confirmClearCompleted = true }) { Text(stringResource(R.string.downloads_clear_completed)) }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
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
                                onPause = { scope.launch { queue.pause(item.ref.pageUrl) } },
                                onResume = { scope.launch { queue.resume(item.ref.pageUrl) } },
                                onCancel = { removeTarget = item },
                                onRetry = { scope.launch { queue.retry(item.ref.pageUrl) } },
                                onOpen = { openDownloadedFile(context, item.filePath) },
                            )
                        }
                    }
                }
            }
        }
    }

    removeTarget?.let { item ->
        val fileDeleteFailedMessage = stringResource(R.string.downloads_delete_failed)
        RemoveConfirmDialog(
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

    if (confirmClearCompleted) {
        val completed = rows.filter { it.state == DownloadState.COMPLETED }
        val count = completed.size
        val totalSize = completed.sumOf { it.totalBytes }
        val someFilesNotDeletedMessage = stringResource(R.string.downloads_delete_some_failed)
        RemoveConfirmDialog(
            title = stringResource(R.string.downloads_clear_title),
            body = removeBody(totalSize, fileCount = count),
            removeLabel = stringResource(R.string.downloads_remove_keep_file),
            deleteLabel = pluralStringResource(R.plurals.downloads_delete_files, count),
            onRemove = { scope.launch { queue.clearCompleted() }; confirmClearCompleted = false },
            onDeleteToo = {
                scope.launch {
                    if (!queue.clearCompletedAndDeleteFiles()) showToast(context, someFilesNotDeletedMessage)
                }
                confirmClearCompleted = false
            },
            onDismiss = { confirmClearCompleted = false },
        )
    }
}

/** The only empty state on this screen -- no placeholder rows, since there's nothing "loading"
 *  about an empty queue. The glyph is a text character, not an Icon: material-icons-core has no
 *  download-tray shape (same gap [GlyphButton] below works around). */
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
        Spacer(Modifier.height(Spacing.s8))
        Text(
            stringResource(R.string.downloads_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun removeBody(totalBytes: Long, fileCount: Int): String {
    val choices = pluralStringResource(R.plurals.downloads_remove_body, fileCount)
    if (totalBytes <= 0) return choices
    return choices + " " + stringResource(R.string.downloads_remove_frees, formatBytes(totalBytes))
}

/**
 * The shared shape for both destructive confirmations (per-row and clear-completed): the safe
 * "remove from list" choice sits first and plain, the file-deleting choice sits second and
 * error-tinted -- deleting a file is irreversible and must never be the default or the visually
 * emphasised option. `confirmButton`/`dismissButton` are left to a bare Cancel; both real choices
 * live in `text` instead, side by side and equally reachable, not stacked as primary/secondary.
 */
@Composable
private fun RemoveConfirmDialog(
    title: String,
    body: String,
    removeLabel: String,
    deleteLabel: String,
    onRemove: () -> Unit,
    onDeleteToo: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(body)
                Spacer(Modifier.height(Spacing.s16))
                TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) { Text(removeLabel) }
                TextButton(onClick = onDeleteToo, modifier = Modifier.fillMaxWidth()) {
                    Text(deleteLabel, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    error: String?,
    liveProgress: DownloadProgress?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
) {
    val extras = MaterialTheme.fyiExtras
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listTwoLine)
            .padding(horizontal = Spacing.pageMargin, vertical = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(Sizes.thumbWidth, Sizes.thumbHeight).clip(RoundedCornerShape(Radius.s))
                .background(extras.surface3),
        ) {
            // Same fallback ResultsList.kt's ResultRow uses for a Library/History row with no
            // stored thumbnail -- youtubeThumbnailFor no-ops for a non-YouTube pageUrl.
            val thumbnailUrl = item.ref.thumbnailUrl ?: youtubeThumbnailFor(item.ref.pageUrl)
            if (thumbnailUrl != null) {
                AsyncImage(model = thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.padding(start = Spacing.s16).weight(1f)) {
            Text(
                item.ref.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.state == DownloadState.RUNNING || item.state == DownloadState.PAUSED) {
                LinearProgressIndicator(
                    progress = { progressFraction(item) },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.s8).height(Sizes.progressTrack).clip(RoundedCornerShape(2.dp)),
                    color = if (item.state == DownloadState.PAUSED) extras.ink3 else MaterialTheme.colorScheme.primary,
                    trackColor = extras.surface3,
                )
            }
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
                DownloadState.QUEUED -> GlyphButton("✕", R.string.action_cancel, onCancel)
                DownloadState.RUNNING -> {
                    GlyphButton("⏸", R.string.downloads_pause, onPause)
                    GlyphButton("✕", R.string.action_cancel, onCancel)
                }
                DownloadState.PAUSED -> {
                    GlyphButton("▶", R.string.downloads_resume, onResume)
                    GlyphButton("✕", R.string.action_cancel, onCancel)
                }
                DownloadState.FAILED -> {
                    GlyphButton("↻", R.string.action_retry, onRetry)
                    GlyphButton("✕", R.string.action_clear, onCancel)
                }
                DownloadState.COMPLETED -> {
                    GlyphButton("▶", R.string.action_play, onOpen)
                    GlyphButton("✕", R.string.action_clear, onCancel)
                }
            }
        }
    }
}

@Composable
private fun GlyphButton(glyph: String, labelResource: Int, onClick: () -> Unit) {
    val label = stringResource(labelResource)
    // Text glyphs, not Icon: material-icons-core has no Pause/SkipNext-shaped glyph for these
    // actions (DECISIONS.md gotcha) -- semantics carries the real label for screen readers.
    // IconButton's own 48dp minimum touch target is kept: narrowing it put Pause and Cancel
    // within a thumb's width of each other.
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Text(glyph, style = MaterialTheme.typography.titleMedium)
    }
}

private fun progressFraction(item: DownloadItem): Float =
    if (item.totalBytes > 0) (item.bytesDownloaded.toFloat() / item.totalBytes).coerceIn(0f, 1f) else 0f

@Composable
private fun statusLine(item: DownloadItem, error: String?, liveProgress: DownloadProgress?): String {
    val separator = " · "
    return when (item.state) {
        DownloadState.QUEUED -> stringResource(R.string.downloads_state_queued)
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
            listOfNotNull("$percent%", amount, speed, remaining).joinToString(separator)
        }
        DownloadState.PAUSED -> stringResource(R.string.downloads_state_paused) + separator + formatBytes(item.bytesDownloaded)
        DownloadState.COMPLETED -> {
            val size = if (item.totalBytes > 0) formatBytes(item.totalBytes) else null
            val duration = downloadDuration(item)?.let { formatDuration(it) }
            listOfNotNull(stringResource(R.string.downloads_state_completed), size, duration).joinToString(separator)
        }
        // The Failed badge beside this line already names the state; only the reason is left.
        DownloadState.FAILED -> error.orEmpty()
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
 * Hand a finished download to whatever app the user has for it. The file lives in this app's own
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
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.downloads_no_app), Toast.LENGTH_LONG).show()
    } catch (e: IllegalArgumentException) {
        // getUriForFile throws when the file sits outside every declared provider root.
        Toast.makeText(context, context.getString(R.string.downloads_cannot_open), Toast.LENGTH_LONG).show()
    }
}
