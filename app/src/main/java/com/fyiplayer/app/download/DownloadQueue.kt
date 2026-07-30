package com.fyiplayer.app.download

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.ContextCompat
import com.fyiplayer.app.FyiApp
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.StreamResolver
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.repo.DownloadItem
import com.fyiplayer.app.data.repo.DownloadRepository
import com.fyiplayer.app.data.repo.DownloadState
import com.fyiplayer.app.engine.EngineGate
import com.fyiplayer.app.engine.mapEngineError
import com.fyiplayer.app.player.FormatSelection
import com.fyiplayer.app.player.FormatSelector
import com.fyiplayer.app.ui.userMessage
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Mirrors the synthetic id [com.fyiplayer.app.engine.WebViewResolver] stamps on its one tier-2
 *  format. That id is never a real engine format selector -- passing it to `-f` would just fail
 *  oddly, and the engine has no extractor for a page that needed tier2 in the first place, so this
 *  is refused up front as a typed, honest failure instead. */
private const val WEBVIEW_FORMAT_ID = "webview"

sealed class EnqueueOutcome {
    object Queued : EnqueueOutcome()
    data class Failed(val message: String) : EnqueueOutcome()
}

private sealed class EngineOutcome {
    object Done : EngineOutcome()
    object Cancelled : EngineOutcome() // process killed via destroyProcessById -- pause or cancel
    data class Failed(val message: String) : EngineOutcome()
}

/**
 * Process-scoped queue driver over [DownloadRepository]. **Must be a process-wide singleton** --
 * it is the only place tracking which row is currently downloading ([activePageUrl] /
 * [activeProcessId]), so pause/cancel can find the live process to kill. Get one via [get], never
 * via the constructor, or a second instance (UI vs. [DownloadService]) would each think nothing is
 * active and let pause/cancel silently no-op.
 *
 * One item downloads at a time: the engine subprocess is heavy (an embedded interpreter plus
 * ffmpeg for muxing) and the foreground notification only has room for one active row anyway.
 */
class DownloadQueue private constructor(
    private val repository: DownloadRepository,
    private val resolver: StreamResolver,
    private val dir: File,
    private val kickService: () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engineGate = Semaphore(1)

    @Volatile private var activePageUrl: String? = null
    @Volatile private var activeProcessId: String? = null

    // Transient only: DownloadEntity has no error column, and an engine failure message is mapped
    // to a fixed safe-to-display string before it ever lands here (never a raw signed URL).
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()

    // Eagerly, not WhileSubscribed: DownloadService reads [rows].value directly (no collector of
    // its own) to build the notification, so the underlying Room flow must stay live even with
    // zero Compose subscribers, or that read would return a stale or empty snapshot.
    val rows: StateFlow<List<DownloadItem>> =
        repository.observe().stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Resolves [ref] through the app's one resolver seam, picks a format at or under [maxHeight]
     * (default: best available -- a download is a deliberate one-off, not bound by the playback
     * network ceiling), and persists only the format selector + canonical page URL. The signed
     * media URL [resolver] just handed back is never stored: the engine re-resolves it itself from
     * the page URL at run time, via the very same selector, using `--continue` to resume.
     */
    suspend fun enqueue(ref: VideoRef, maxHeight: Int = Int.MAX_VALUE): EnqueueOutcome {
        val resolved = try {
            resolver.resolve(ref)
        } catch (e: ExtractionError) {
            return EnqueueOutcome.Failed(e.userMessage())
        }
        val result = FormatSelector.select(resolved.formats, maxHeight)
        val selection = result.selection
            ?: return EnqueueOutcome.Failed(result.reason ?: "no downloadable format")

        val selector = when (selection) {
            is FormatSelection.Single -> selection.format.formatId
            // The engine's own `-f video+audio` selector syntax merges the pair via ffmpeg --
            // exactly the muxed-download case DESIGN.md calls out, with no extra plumbing here.
            is FormatSelection.Paired -> "${selection.video.formatId}+${selection.audio.formatId}"
        }
        if (selector.isBlank() || selector.contains(WEBVIEW_FORMAT_ID)) {
            return EnqueueOutcome.Failed("this source can't be downloaded yet")
        }
        val totalBytes = when (selection) {
            is FormatSelection.Single -> selection.format.filesizeBytes ?: 0L
            is FormatSelection.Paired ->
                (selection.video.filesizeBytes ?: 0L) + (selection.audio.filesizeBytes ?: 0L)
        }

        _errors.update { it - resolved.ref.pageUrl }
        repository.upsert(
            DownloadItem(
                ref = resolved.ref,
                formatId = selector,
                filePath = null,
                state = DownloadState.QUEUED,
                bytesDownloaded = 0,
                totalBytes = totalBytes,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        kickService()
        return EnqueueOutcome.Queued
    }

    suspend fun pause(pageUrl: String) {
        if (activePageUrl == pageUrl) {
            activeProcessId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
        } else {
            setState(pageUrl, DownloadState.PAUSED)
        }
    }

    suspend fun resume(pageUrl: String) {
        setState(pageUrl, DownloadState.QUEUED)
        kickService()
    }

    suspend fun retry(pageUrl: String) {
        _errors.update { it - pageUrl }
        setState(pageUrl, DownloadState.QUEUED)
        kickService()
    }

    /** Kills the live process if [pageUrl] is running, then drops the row. Leaves any partial
     *  file on disk -- deleting it is a separate, explicit user action this screen doesn't offer. */
    suspend fun cancel(pageUrl: String) {
        _errors.update { it - pageUrl }
        if (activePageUrl == pageUrl) {
            activeProcessId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
        }
        repository.remove(pageUrl)
    }

    suspend fun clearCompleted() {
        rows.value.filter { it.state == DownloadState.COMPLETED }.forEach { repository.remove(it.ref.pageUrl) }
    }

    /**
     * Android 15's `dataSync` foreground-service timeout budget just ran out. Park whatever is
     * running as PAUSED (resumable via `--continue`) and kill its process, on a scope of its own
     * since the service's own scope is about to die with it and blocking here would eat the few
     * seconds the system gives before it kills the process outright.
     */
    suspend fun pauseActive() {
        val pageUrl = activePageUrl ?: return
        val processId = activeProcessId
        setState(pageUrl, DownloadState.PAUSED)
        activePageUrl = null
        activeProcessId = null
        processId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
    }

    /** RUNNING can only mean "the service died mid-download" at process start -- requeue it. */
    suspend fun resetStale() {
        rows.value.filter { it.state == DownloadState.RUNNING }
            .forEach { repository.upsert(it.copy(state = DownloadState.QUEUED, updatedAt = System.currentTimeMillis())) }
    }

    /** Runs one queued row to a terminal-for-now state. False when the queue is empty. */
    suspend fun processNext(onProgress: (String, DownloadProgress) -> Unit = { _, _ -> }): Boolean {
        val next = rows.value.firstOrNull { it.state == DownloadState.QUEUED } ?: return false
        val pageUrl = next.ref.pageUrl
        engineGate.withPermit {
            val processId = UUID.randomUUID().toString()
            activePageUrl = pageUrl
            activeProcessId = processId
            repository.upsert(next.copy(state = DownloadState.RUNNING, updatedAt = System.currentTimeMillis()))

            var lastWriteMillis = 0L
            val outcome = runEngine(next, processId) { progress ->
                val now = System.currentTimeMillis()
                if (now - lastWriteMillis >= 1_000) {
                    lastWriteMillis = now
                    // Fires on the engine's own callback thread, not a coroutine -- that thread has
                    // nothing else to do until this returns, so a blocking bridge is free here.
                    runCatching {
                        runBlocking {
                            repository.upsert(
                                next.copy(
                                    state = DownloadState.RUNNING,
                                    bytesDownloaded = progress.downloadedBytes,
                                    totalBytes = progress.totalBytes ?: next.totalBytes,
                                    updatedAt = now,
                                ),
                            )
                        }
                    }
                }
                onProgress(pageUrl, progress)
            }
            activePageUrl = null
            activeProcessId = null

            when (outcome) {
                EngineOutcome.Done -> {
                    val produced = findProducedFile(dir, next.ref)
                    repository.upsert(
                        next.copy(
                            state = DownloadState.COMPLETED,
                            filePath = produced?.absolutePath,
                            bytesDownloaded = produced?.length() ?: next.bytesDownloaded,
                            totalBytes = produced?.length() ?: next.totalBytes,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
                EngineOutcome.Cancelled -> {
                    // cancel() already deleted the row; only a plain pause() needs PAUSED written.
                    // ponytail: small window where cancel()'s own remove() hasn't landed yet and
                    // this rewrites PAUSED just before it -- harmless (remove() still wins), add a
                    // request-flag only if this is ever observed to matter in practice.
                    if (repository.get(pageUrl) != null) setState(pageUrl, DownloadState.PAUSED)
                }
                is EngineOutcome.Failed -> {
                    _errors.update { it + (pageUrl to outcome.message) }
                    repository.upsert(next.copy(state = DownloadState.FAILED, updatedAt = System.currentTimeMillis()))
                }
            }
        }
        return true
    }

    private suspend fun setState(pageUrl: String, state: DownloadState) {
        val current = repository.get(pageUrl) ?: return
        repository.upsert(current.copy(state = state, updatedAt = System.currentTimeMillis()))
    }

    /**
     * The engine resolves [item]'s page URL itself with `-f formatId` -- the signed URL [enqueue]
     * saw is never handled by this process again. `--continue` resumes a paused row's partial
     * file; `--merge-output-format mp4` gives a deterministic container on the video+audio path
     * (single-format rows are written in their own container unmuxed, per the engine's default).
     */
    private suspend fun runEngine(
        item: DownloadItem,
        processId: String,
        onProgress: (DownloadProgress) -> Unit,
    ): EngineOutcome = withContext(Dispatchers.IO) {
        if (item.formatId.isBlank() || item.formatId.contains(WEBVIEW_FORMAT_ID)) {
            return@withContext EngineOutcome.Failed("this source can't be downloaded yet")
        }
        try {
            EngineGate.await()
            val request = YoutubeDLRequest(item.ref.pageUrl).apply {
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--continue")
                addOption("-f", item.formatId)
                addOption("--merge-output-format", "mp4")
                addOption("-o", destTemplate(dir, item.ref))
                addOption("--progress-template", PROGRESS_TEMPLATE)
            }
            YoutubeDL.getInstance().execute(request, processId) { _, _, line ->
                parseProgressLine(line)?.let(onProgress)
            }
            EngineOutcome.Done
        } catch (e: YoutubeDL.CanceledException) {
            EngineOutcome.Cancelled
        } catch (e: Exception) {
            // The raw exception message is the engine's stderr and can echo a signed CDN URL --
            // classify first, store only the fixed friendly string, same as the resolver path.
            EngineOutcome.Failed(mapEngineError(e).userMessage())
        }
    }

    companion object {
        @Volatile private var instance: DownloadQueue? = null

        /** Same-instance-per-process, exactly like [com.fyiplayer.app.data.db.AppDatabase.get] --
         *  see the class doc for why a second instance is unsafe. */
        fun get(context: Context): DownloadQueue = instance ?: synchronized(this) {
            instance ?: run {
                val appContext = context.applicationContext
                val app = appContext as FyiApp
                val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: appContext.filesDir
                DownloadQueue(
                    repository = DownloadRepository(app.database.downloadDao()),
                    resolver = app.resolver,
                    dir = dir,
                    kickService = {
                        // Once the dataSync foreground budget for the day is spent, starting the
                        // service again throws ForegroundServiceStartNotAllowedException. The row
                        // stays QUEUED and drains on the next start -- never crash the caller.
                        runCatching {
                            ContextCompat.startForegroundService(appContext, Intent(appContext, DownloadService::class.java))
                        }
                    },
                ).also { instance = it }
            }
        }
    }
}

// A fixed app-private directory built entirely from our own inputs (never a user- or DB-supplied
// path), so unlike a picker-backed destination this needs no root/traversal validation: there is
// exactly one root, and every name under it is one this function generated.
private fun safeBaseName(ref: VideoRef): String {
    val title = ref.title.take(120).replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "video" }
    val suffix = (ref.pageUrl.hashCode() and 0x7fffffff).toString(36) // stable per page URL
    return "$title-$suffix"
}

/** `%(ext)s` lets the engine pick the real extension; [findProducedFile] recovers the concrete
 *  path afterwards. Stable across pause/resume of the same row, so `--continue` keeps matching. */
private fun destTemplate(dir: File, ref: VideoRef): String {
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "${safeBaseName(ref)}.%(ext)s").absolutePath
}

private fun findProducedFile(dir: File, ref: VideoRef): File? {
    val prefix = "${safeBaseName(ref)}."
    return dir.listFiles { f -> f.isFile && f.name.startsWith(prefix) }?.maxByOrNull { it.lastModified() }
}
