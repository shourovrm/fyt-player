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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fyiplayer.app.data.repo.DownloadItem
import com.fyiplayer.app.data.repo.DownloadState
import com.fyiplayer.app.data.repo.youtubeThumbnailFor
import com.fyiplayer.app.download.DownloadProgress
import com.fyiplayer.app.download.DownloadQueue
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
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Downloads", style = MaterialTheme.typography.headlineSmall)
                if (rows.any { it.state == DownloadState.COMPLETED }) {
                    TextButton(onClick = { confirmClearCompleted = true }) { Text("Clear completed") }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (rows.isEmpty()) {
                    DownloadsEmptyState()
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
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
        RemoveConfirmDialog(
            title = "Remove \"${item.ref.title}\"?",
            body = removeBody(item),
            removeLabel = "Remove from list",
            deleteLabel = "Delete file too",
            onRemove = { scope.launch { queue.cancel(item.ref.pageUrl) }; removeTarget = null },
            onDeleteToo = {
                scope.launch {
                    if (!queue.cancelAndDelete(item.ref.pageUrl)) showToast(context, "Removed from list; couldn't delete the file")
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
        val plural = if (count == 1) "" else "s"
        RemoveConfirmDialog(
            title = "Clear $count completed download$plural?",
            body = "Choose whether the downloaded file$plural" +
                (if (totalSize > 0) " (${formatBytes(totalSize)})" else "") + " should also be deleted from storage.",
            removeLabel = "Remove $count from list",
            deleteLabel = "Delete $count file$plural too",
            onRemove = { scope.launch { queue.clearCompleted() }; confirmClearCompleted = false },
            onDeleteToo = {
                scope.launch {
                    if (!queue.clearCompletedAndDeleteFiles()) showToast(context, "Removed from list; couldn't delete every file")
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
        Modifier.fillMaxSize().padding(top = 70.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("⬇", style = MaterialTheme.typography.titleMedium, color = extras.ink3)
        }
        Spacer(Modifier.height(14.dp))
        Text("No downloads yet", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(6.dp))
        Text(
            "Videos you download appear here and play offline.",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
            color = extras.ink3,
            textAlign = TextAlign.Center,
        )
    }
}

private fun removeBody(item: DownloadItem): String {
    val sizeNote = if (item.totalBytes > 0) " (${formatBytes(item.totalBytes)})" else ""
    val fileWord = if (item.state == DownloadState.COMPLETED) "the downloaded file" else "any partial file"
    return "Choose whether $fileWord$sizeNote should also be deleted from storage."
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
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) { Text(removeLabel) }
                TextButton(onClick = onDeleteToo, modifier = Modifier.fillMaxWidth()) {
                    Text(deleteLabel, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(80.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                .background(extras.surface3),
        ) {
            // Same fallback ResultsList.kt's ResultRow uses for a Library/History row with no
            // stored thumbnail -- youtubeThumbnailFor no-ops for a non-YouTube pageUrl.
            val thumbnailUrl = item.ref.thumbnailUrl ?: youtubeThumbnailFor(item.ref.pageUrl)
            if (thumbnailUrl != null) {
                AsyncImage(model = thumbnailUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                item.ref.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.state == DownloadState.RUNNING || item.state == DownloadState.PAUSED) {
                LinearProgressIndicator(
                    progress = { progressFraction(item) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = if (item.state == DownloadState.PAUSED) extras.ink3 else MaterialTheme.colorScheme.primary,
                    trackColor = extras.surface3,
                )
            }
            Text(
                statusLine(item, error, liveProgress),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor(item),
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (item.state) {
                DownloadState.QUEUED -> GlyphButton("✕", "Cancel", onCancel)
                DownloadState.RUNNING -> {
                    GlyphButton("⏸", "Pause", onPause)
                    GlyphButton("✕", "Cancel", onCancel)
                }
                DownloadState.PAUSED -> {
                    GlyphButton("▶", "Resume", onResume)
                    GlyphButton("✕", "Cancel", onCancel)
                }
                DownloadState.FAILED -> {
                    GlyphButton("↻", "Retry", onRetry)
                    GlyphButton("✕", "Clear", onCancel)
                }
                DownloadState.COMPLETED -> {
                    GlyphButton("▶", "Open", onOpen)
                    GlyphButton("✕", "Clear", onCancel)
                }
            }
        }
    }
}

@Composable
private fun GlyphButton(glyph: String, label: String, onClick: () -> Unit) {
    // Text glyphs, not Icon: material-icons-core has no Pause/SkipNext-shaped glyph for these
    // actions (DECISIONS.md gotcha) -- semantics carries the real label for screen readers.
    IconButton(onClick = onClick, modifier = Modifier.width(40.dp).semantics { contentDescription = label }) {
        Text(glyph, style = MaterialTheme.typography.titleMedium)
    }
}

private fun progressFraction(item: DownloadItem): Float =
    if (item.totalBytes > 0) (item.bytesDownloaded.toFloat() / item.totalBytes).coerceIn(0f, 1f) else 0f

private fun statusLine(item: DownloadItem, error: String?, liveProgress: DownloadProgress?): String = when (item.state) {
    DownloadState.QUEUED -> "Queued"
    DownloadState.RUNNING -> {
        val pct = (progressFraction(item) * 100).roundToInt()
        val of = if (item.totalBytes > 0) " of ${formatBytes(item.totalBytes)}" else ""
        val speed = liveProgress?.speedBytesPerSecond?.takeIf { it > 0 }
            ?.let { " · ${formatBytes(it.toLong())}/s" }.orEmpty()
        val eta = liveProgress?.etaSeconds?.takeIf { it > 0 }
            ?.let { " · ETA ${formatDuration(it.toInt())}" }.orEmpty()
        "$pct% · ${formatBytes(item.bytesDownloaded)}$of$speed$eta"
    }
    DownloadState.PAUSED -> "Paused · ${formatBytes(item.bytesDownloaded)}"
    DownloadState.COMPLETED -> {
        val size = if (item.totalBytes > 0) " · ${formatBytes(item.totalBytes)}" else ""
        val duration = downloadDuration(item)?.let { " · ${formatDuration(it)}" }.orEmpty()
        "Downloaded$size$duration"
    }
    DownloadState.FAILED -> "Failed" + (error?.let { " · $it" } ?: "")
}

/** Wall-clock time from first RUNNING to COMPLETED (a paused stretch counts too -- "how long this
 *  took", not "how long the engine was actually busy"). Null until both timestamps exist. */
private fun downloadDuration(item: DownloadItem): Int? {
    val started = item.startedAt ?: return null
    val finished = item.finishedAt ?: return null
    return ((finished - started) / 1000).toInt().coerceAtLeast(0)
}

@Composable
private fun statusColor(item: DownloadItem) = when (item.state) {
    DownloadState.FAILED -> MaterialTheme.colorScheme.error
    DownloadState.COMPLETED -> MaterialTheme.fyiExtras.positive
    else -> MaterialTheme.colorScheme.onSurfaceVariant
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
        Toast.makeText(context, "File is no longer on disk", Toast.LENGTH_LONG).show()
        return
    }
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "video/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this device can open it", Toast.LENGTH_LONG).show()
    } catch (e: IllegalArgumentException) {
        // getUriForFile throws when the file sits outside every declared provider root.
        Toast.makeText(context, "Can't open this file from here", Toast.LENGTH_LONG).show()
    }
}
