package com.fyiplayer.app.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import com.fyiplayer.app.DiagLog
import com.fyiplayer.app.FyiApp
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.Protocol
import com.fyiplayer.app.core.StreamResolver
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.repo.DownloadItem
import com.fyiplayer.app.data.repo.DownloadRepository
import com.fyiplayer.app.data.repo.DownloadState
import com.fyiplayer.app.data.repo.withTitleIfBlank
import com.fyiplayer.app.engine.EngineGate
import com.fyiplayer.app.engine.mapEngineError
import com.fyiplayer.app.player.FormatSelection
import com.fyiplayer.app.player.FormatSelector
import com.fyiplayer.app.player.mediaHttpClient
import com.fyiplayer.app.ui.userMessage
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Mirrors the synthetic id [com.fyiplayer.app.engine.WebViewResolver] stamps on its one tier-2
 *  format. That id is never a real engine format selector -- passing it to `-f` would just fail
 *  oddly, and the engine has no extractor for a page that needed tier2 in the first place, so this
 *  is refused up front as a typed, honest failure instead. */
private const val WEBVIEW_FORMAT_ID = "webview"

sealed class EnqueueOutcome {
    object Queued : EnqueueOutcome()
    data class Failed(val message: String) : EnqueueOutcome()
}

/**
 * One user-choosable quality/size. Deliberately plain data -- no [MediaFormat], no URL: [formatId]
 * is the engine's own `-f` selector text (e.g. `"137+140"`), which is safe to hold and pass around
 * because it identifies a format *choice*, not a signed media location. Never persisted anywhere
 * except as the [com.fyiplayer.app.data.repo.DownloadItem.formatId] of the row the user picked.
 */
data class DownloadOption(
    val formatId: String,
    val label: String,
    val approxBytes: Long?,
)

sealed class ResolveOutcome {
    data class Ready(val ref: VideoRef, val options: List<DownloadOption>) : ResolveOutcome()
    data class Failed(val message: String) : ResolveOutcome()
}

private sealed class EngineOutcome {
    object Done : EngineOutcome()
    object Cancelled : EngineOutcome() // process killed via destroyProcessById -- pause or cancel
    data class Failed(val message: String) : EngineOutcome()
}

/**
 * Process-scoped queue driver over [DownloadRepository]. **Must be a process-wide singleton** --
 * it is the only place tracking which row is currently downloading ([activeRun]), so pause/cancel
 * can find the live download to stop. Get one via [get], never via the constructor, or a second instance (UI vs. [DownloadService]) would each think nothing is
 * active and let pause/cancel silently no-op.
 *
 * One item downloads at a time: the engine subprocess is heavy (an embedded interpreter plus
 * ffmpeg for muxing) and the foreground notification only has room for one active row anyway.
 */
class DownloadQueue private constructor(
    private val repository: DownloadRepository,
    private val resolver: StreamResolver,
    private val dir: File,
    private val appContext: Context,
    private val treeUri: () -> String?,
    private val kickService: () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engineGate = Semaphore(1)
    // Same rn/UA request shaping as playback (player/MediaHttp.kt) -- an unshapen client is
    // paced by the CDN and a 30 MB download takes twenty minutes. Shared with the size-probe and
    // subtitle fetch below -- one client, not a fresh one per concern.
    private val httpClient = mediaHttpClient()
    private val streamDownloader = StreamDownloader(httpClient)

    /** The one row being processed. Created the moment the row becomes active, before the resolve
     *  that precedes any download: a pause/cancel during those seconds has nothing else to flip, so
     *  [signal] is checked by the download once it gets going. [removed] is set by [cancel] under
     *  [rowWrites], so no later write of this run can re-create the deleted row. */
    private class ActiveRun(val pageUrl: String, val processId: String) {
        val signal = StreamDownloader.CancelSignal()
        @Volatile var removed = false
    }

    @Volatile private var activeRun: ActiveRun? = null

    // Serialises row removal against the active run's own writes (REPLACE upserts would otherwise
    // resurrect a row the user just removed) and makes setState's read-then-write atomic.
    private val rowWrites = Mutex()

    private fun stopRun(run: ActiveRun) {
        run.signal.cancel()
        YoutubeDL.getInstance().destroyProcessById(run.processId)
    }

    private suspend fun writeUnlessRemoved(run: ActiveRun, item: DownloadItem) {
        rowWrites.withLock { if (!run.removed) repository.upsert(item) }
    }

    /** Drops the row; if it is the active one, stops it and bars its further writes first. */
    private suspend fun removeRow(pageUrl: String) {
        rowWrites.withLock {
            activeRun?.takeIf { it.pageUrl == pageUrl }?.let { run ->
                run.removed = true
                stopRun(run)
            }
            repository.remove(pageUrl)
        }
    }

    // Transient only: DownloadEntity has no error column, and an engine failure message is mapped
    // to a fixed safe-to-display string before it ever lands here (never a raw signed URL).
    private val _errors = MutableStateFlow<Map<String, String>>(emptyMap())
    val errors: StateFlow<Map<String, String>> = _errors.asStateFlow()

    // Transient only, unthrottled (unlike the 1s-throttled DB write in processNext): speed/ETA are
    // never persisted, only shown live for whichever row is RUNNING. Cleared for a pageUrl the
    // moment it leaves RUNNING.
    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress.asStateFlow()

    /** Snapshot of the most recent [resolveOptions] resolve. Reused by [fetchApproxBytes] instead
     *  of a second resolve -- safe because the picker is modal, so at most one download dialog
     *  (and therefore one resolve) is ever showing at a time; "most recent" always means "the
     *  video this dialog is for". */
    private data class LastResolve(val pageUrl: String, val formats: List<MediaFormat>)
    @Volatile private var lastResolve: LastResolve? = null

    // Eagerly, not WhileSubscribed: DownloadService reads [rows].value directly (no collector of
    // its own) to build the notification, so the underlying Room flow must stay live even with
    // zero Compose subscribers, or that read would return a stale or empty snapshot.
    val rows: StateFlow<List<DownloadItem>> =
        repository.observe().stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Resolves [ref] through the app's one resolver seam and derives the sizes the user can pick
     * from -- every download asks, never a silent fall-back to the playback resolution preference.
     * Returns plain [DownloadOption]s only; the [MediaFormat]s backing them never leave this class.
     */
    suspend fun resolveOptions(ref: VideoRef): ResolveOutcome {
        val resolved = try {
            resolver.resolve(ref)
        } catch (e: ExtractionError) {
            return ResolveOutcome.Failed(e.userMessage())
        }
        val options = deriveDownloadOptions(resolved.formats, includeManifests = ref.sourceId != "youtube")
        if (options.isEmpty()) return ResolveOutcome.Failed("No downloadable video or audio track found.")
        lastResolve = LastResolve(resolved.ref.pageUrl, resolved.formats)
        return ResolveOutcome.Ready(resolved.ref, options)
    }

    /**
     * Best-effort size for one [option] whose derivation ([deriveDownloadOptions]) had no filesize:
     * a HEAD probe per format id in the selector (summed for a paired video+audio option), against
     * the formats [resolveOptions] already resolved. Null on any failure or manifest format -- the
     * caller just shows nothing, same as a size that was never known.
     */
    suspend fun fetchApproxBytes(option: DownloadOption): Long? = withContext(Dispatchers.IO) {
        val formats = lastResolve?.formats ?: return@withContext null
        var total = 0L
        var any = false
        option.formatId.split('+').forEach { id ->
            val format = formats.firstOrNull { it.formatId == id } ?: return@forEach
            headContentLength(httpClient, format)?.let { total += it; any = true }
        }
        if (any) total else null
    }

    /**
     * Standalone "Download subtitles" action (VideoActionSheet / Detail title long-press) --
     * independent of [start]/[DownloadOption], so it works even for a video whose formats can't be
     * downloaded. Re-resolves [ref] itself rather than reusing [lastResolve]: unlike the quality
     * picker, this action has no modal dialog pinning it to "the last resolve", and [resolver]
     * already caches recent resolves (ChainResolver, 60 min TTL) so a resolve right after opening
     * the quality picker is cheap anyway. Picks one track ([pickSubtitleTrack], no picker) and
     * always writes `.srt`.
     */
    suspend fun downloadSubtitles(ref: VideoRef): SubtitleOutcome {
        val ref = ref.withTitleIfBlank() // bare share-in ref would name the file "video-xxxx.srt"
        val resolved = try {
            resolver.resolve(ref)
        } catch (e: ExtractionError) {
            return SubtitleOutcome.Failed(e.userMessage())
        }
        val track = pickSubtitleTrack(resolved.captions) ?: return SubtitleOutcome.NoSubtitles
        return withContext(Dispatchers.IO) {
            val srt = fetchSrt(httpClient, resolved.ref.sourceId == "youtube", track)
                ?: return@withContext SubtitleOutcome.Unsupported
            if (!dir.exists()) dir.mkdirs()
            val out = File(dir, "${safeBaseName(ref)}.srt")
            out.writeText(srt)
            // Best-effort copy into the user's chosen folder, same as the finished video.
            treeUri()?.let { uri -> exportToTree(appContext, out, Uri.parse(uri)) }
            SubtitleOutcome.Saved
        }
    }

    /**
     * Persists the user's chosen [option] as a queued row. [option] already carries the resolved
     * format selector (see [deriveDownloadOptions]) so this does no re-resolving and touches no
     * signed URL -- the engine re-resolves the page URL itself at run time via that same selector,
     * with `--continue` to resume.
     */
    suspend fun start(ref: VideoRef, option: DownloadOption): EnqueueOutcome {
        if (option.formatId.isBlank() || option.formatId.contains(WEBVIEW_FORMAT_ID)) {
            return EnqueueOutcome.Failed("this source can't be downloaded yet")
        }
        _errors.update { it - ref.pageUrl }
        // Bare-URL opens (share) reach here before Detail's enrichment landed; same fix as likes.
        val ref = ref.withTitleIfBlank()
        repository.upsert(
            DownloadItem(
                ref = ref,
                formatId = option.formatId,
                filePath = null,
                state = DownloadState.QUEUED,
                bytesDownloaded = 0,
                totalBytes = option.approxBytes ?: 0L,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        kickService()
        return EnqueueOutcome.Queued
    }

    suspend fun pause(pageUrl: String) {
        val run = activeRun
        if (run != null && run.pageUrl == pageUrl) {
            stopRun(run)
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

    /** Kills the live process if [pageUrl] is running, then drops the row. Leaves any produced or
     *  partial file on disk -- the safe default; see [cancelAndDelete] for the destructive twin.
     *  Callers must confirm with the user before choosing between the two; this function itself
     *  does not ask. */
    suspend fun cancel(pageUrl: String) {
        _errors.update { it - pageUrl }
        removeRow(pageUrl)
    }

    /** Same as [cancel], but also deletes the produced file and any leftover `.part`/`.ytdl`
     *  sidecar the engine wrote for this row -- so a partial download removed this way leaves
     *  nothing orphaned on disk. Irreversible; the caller must already have an explicit
     *  confirmation before calling this. Returns false if a matched file resisted deletion (still
     *  there afterwards), so the caller can say so instead of pretending it worked. */
    suspend fun cancelAndDelete(pageUrl: String): Boolean {
        val item = repository.get(pageUrl)
        _errors.update { it - pageUrl }
        removeRow(pageUrl)
        return item?.let { deleteDownloadFiles(dir, it.ref, it.filePath) } ?: true
    }

    suspend fun clearCompleted() {
        rows.value.filter { it.state == DownloadState.COMPLETED }.forEach { removeRow(it.ref.pageUrl) }
    }

    /** Batch [cancelAndDelete] over every completed row. Irreversible; caller confirms first.
     *  Returns false if any file resisted deletion. */
    suspend fun clearCompletedAndDeleteFiles(): Boolean {
        val completed = rows.value.filter { it.state == DownloadState.COMPLETED }
        completed.forEach { removeRow(it.ref.pageUrl) }
        return completed.fold(true) { allOk, item -> deleteDownloadFiles(dir, item.ref, item.filePath) && allOk }
    }

    /**
     * Android 15's `dataSync` foreground-service timeout budget just ran out. Park whatever is
     * running as PAUSED (resumable via `--continue`) and kill its process, on a scope of its own
     * since the service's own scope is about to die with it and blocking here would eat the few
     * seconds the system gives before it kills the process outright.
     */
    suspend fun pauseActive() {
        val run = activeRun ?: return
        setState(run.pageUrl, DownloadState.PAUSED)
        activeRun = null
        stopRun(run)
    }

    /** RUNNING can only mean "the service died mid-download" at process start -- requeue it. */
    suspend fun resetStale() {
        rows.value.filter { it.state == DownloadState.RUNNING }
            .forEach { repository.upsert(it.copy(state = DownloadState.QUEUED, updatedAt = System.currentTimeMillis())) }
    }

    /**
     * Runs one queued row to a terminal-for-now state. False when the queue is empty. [onFinished]
     * fires only for a real terminal outcome (COMPLETED/FAILED), never for PAUSED/cancelled -- the
     * caller (the notification) has nothing worth announcing for those.
     */
    suspend fun processNext(
        onProgress: (String, DownloadProgress) -> Unit = { _, _ -> },
        onFinished: (DownloadItem, DownloadState) -> Unit = { _, _ -> },
    ): Boolean {
        val next = rows.value.firstOrNull { it.state == DownloadState.QUEUED } ?: return false
        val pageUrl = next.ref.pageUrl
        engineGate.withPermit {
            val run = ActiveRun(pageUrl, processId = UUID.randomUUID().toString())
            activeRun = run
            try {
                runRow(run, onProgress, onFinished)
            } catch (e: CancellationException) {
                throw e // service stopped; the row stays RUNNING and resetStale requeues it
            } catch (e: Exception) {
                // Nothing may escape to the service's launch (no handler: the app would crash), and
                // the message is never stored -- it can echo a signed URL.
                DiagLog.log("DownloadQueue", "unexpected ${e::class.simpleName}")
                failRow(run, "download failed", onFinished)
            } finally {
                activeRun = null
                _progress.update { it - pageUrl }
            }
        }
        return true
    }

    private suspend fun runRow(
        run: ActiveRun,
        onProgress: (String, DownloadProgress) -> Unit,
        onFinished: (DownloadItem, DownloadState) -> Unit,
    ) {
        val pageUrl = run.pageUrl
        // Re-read the row: the snapshot processNext picked from may be stale (cancelled, or
        // restarted with another format since).
        val running = rowWrites.withLock {
            val current = repository.get(pageUrl)?.takeIf { it.state == DownloadState.QUEUED }
                ?: return@withLock null
            // startedAt only on the FIRST run: a paused/resumed row keeps its original start time,
            // so "duration taken" on completion reflects the whole queued lifetime, not just the
            // final resume.
            current.copy(
                state = DownloadState.RUNNING,
                startedAt = current.startedAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ).also { repository.upsert(it) }
        }
        if (running == null) {
            // processNext picked this row from the `rows` snapshot, which trails the database.
            // Returning at once would let the service loop pick the same stale row again and spin
            // until Room re-emits; wait for the snapshot to catch up (bounded, in case the row
            // was re-queued meanwhile and really is QUEUED again).
            withTimeoutOrNull(2_000) {
                rows.first { list -> list.none { it.ref.pageUrl == pageUrl && it.state == DownloadState.QUEUED } }
            }
            return
        }

        var lastWriteMillis = 0L
        val outcome = runDownload(running, run) { progress ->
            _progress.update { it + (pageUrl to progress) }
            val now = System.currentTimeMillis()
            if (now - lastWriteMillis >= 1_000) {
                lastWriteMillis = now
                // Fires on the engine's own callback thread, not a coroutine -- that thread has
                // nothing else to do until this returns, so a blocking bridge is free here.
                runCatching {
                    runBlocking {
                        writeUnlessRemoved(
                            run,
                            running.copy(
                                bytesDownloaded = progress.downloadedBytes,
                                totalBytes = progress.totalBytes ?: running.totalBytes,
                                updatedAt = now,
                            ),
                        )
                    }
                }
            }
            onProgress(pageUrl, progress)
        }
        _progress.update { it - pageUrl }
        if (run.removed) return // the user removed the row; nothing to record or announce

        when (outcome) {
            EngineOutcome.Done -> {
                val produced = findProducedFile(dir, running.ref)
                val finished = running.copy(
                    state = DownloadState.COMPLETED,
                    filePath = produced?.absolutePath,
                    bytesDownloaded = produced?.length() ?: running.bytesDownloaded,
                    totalBytes = produced?.length() ?: running.totalBytes,
                    finishedAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
                writeUnlessRemoved(run, finished)
                // Best-effort copy into the user's chosen folder, if any -- the row above is
                // already COMPLETED and stays that way regardless of how this turns out; the
                // private file it points at is what the in-app Downloads screen opens.
                produced?.let { file -> treeUri()?.let { uri -> exportToTree(appContext, file, Uri.parse(uri)) } }
                onFinished(finished, DownloadState.COMPLETED)
            }
            EngineOutcome.Cancelled -> setState(pageUrl, DownloadState.PAUSED) // no-op if row is gone
            is EngineOutcome.Failed -> failRow(run, outcome.message, onFinished)
        }
    }

    private suspend fun failRow(
        run: ActiveRun,
        message: String,
        onFinished: (DownloadItem, DownloadState) -> Unit,
    ) {
        val failed = rowWrites.withLock {
            if (run.removed) return@withLock null
            val current = repository.get(run.pageUrl) ?: return@withLock null
            current.copy(state = DownloadState.FAILED, updatedAt = System.currentTimeMillis())
                .also { repository.upsert(it) }
        } ?: return
        _errors.update { it + (run.pageUrl to message) }
        onFinished(failed, DownloadState.FAILED)
    }

    private suspend fun setState(pageUrl: String, state: DownloadState) {
        rowWrites.withLock {
            val current = repository.get(pageUrl) ?: return@withLock
            repository.upsert(current.copy(state = state, updatedAt = System.currentTimeMillis()))
        }
    }

    /** YouTube rows download through the extractor chain (the only signed-in path — an engine
     *  subprocess is anonymous and hits the same age wall playback used to); every other source
     *  keeps the engine, which is still the only downloader that knows their extractors. */
    private suspend fun runDownload(
        item: DownloadItem,
        run: ActiveRun,
        onProgress: (DownloadProgress) -> Unit,
    ): EngineOutcome =
        if (item.ref.sourceId == "youtube") runStream(item, run.signal, onProgress)
        else runEngine(item, run, onProgress)

    private suspend fun runStream(
        item: DownloadItem,
        signal: StreamDownloader.CancelSignal,
        onProgress: (DownloadProgress) -> Unit,
    ): EngineOutcome {
        val resolved = try {
            resolver.resolve(item.ref)
        } catch (e: ExtractionError) {
            return EngineOutcome.Failed(e.userMessage())
        }
        // Pause/cancel may have landed while resolving, before any call existed to abort.
        if (signal.cancelled) return EngineOutcome.Cancelled
        if (!dir.exists()) dir.mkdirs()
        val baseName = safeBaseName(item.ref)
        return when (val outcome = streamDownloader.download(
            formats = resolved.formats,
            selector = item.formatId,
            dir = dir,
            baseName = baseName,
            signal = signal,
            onProgress = onProgress,
            reResolve = {
                // The signed URLs expired mid-file: drop the cached resolve so this is a real
                // fetch, and report a failed one as "no recovery" rather than throwing.
                resolver.invalidate(item.ref.pageUrl)
                try {
                    resolver.resolve(item.ref).formats
                } catch (e: ExtractionError) {
                    null
                }
            },
        )) {
            is StreamDownloader.Outcome.Done -> EngineOutcome.Done
            StreamDownloader.Outcome.Cancelled -> EngineOutcome.Cancelled
            is StreamDownloader.Outcome.Failed -> EngineOutcome.Failed(outcome.message)
        }
    }

    /**
     * The engine resolves [item]'s page URL itself with `-f formatId` -- the signed URL
     * [resolveOptions] saw is never handled by this process again. `--continue` resumes a paused
     * row's partial file; `--merge-output-format mp4` gives a deterministic container on the
     * video+audio path (single-format rows are written in their own container unmuxed, per the
     * engine's default).
     */
    private suspend fun runEngine(
        item: DownloadItem,
        run: ActiveRun,
        onProgress: (DownloadProgress) -> Unit,
    ): EngineOutcome = withContext(Dispatchers.IO) {
        if (item.formatId.isBlank() || item.formatId.contains(WEBVIEW_FORMAT_ID)) {
            return@withContext EngineOutcome.Failed("this source can't be downloaded yet")
        }
        try {
            EngineGate.await()
            // destroyProcessById is a no-op until the process exists; honour a stop that landed first.
            if (run.signal.cancelled) return@withContext EngineOutcome.Cancelled
            val request = YoutubeDLRequest(item.ref.pageUrl).apply {
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--continue")
                addOption("-f", item.formatId)
                addOption("--merge-output-format", "mp4")
                addOption("-o", destTemplate(dir, item.ref))
                // Without --newline the engine rewrites one progress line with \r, so the line
                // reader that feeds the callback below never sees a complete line and progress
                // stays at zero for the whole download.
                addOption("--newline")
                addOption("--progress-template", PROGRESS_TEMPLATE)
            }
            YoutubeDL.getInstance().execute(request, run.processId) { _, _, line ->
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
                    appContext = appContext,
                    treeUri = app::currentDownloadTreeUri,
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

// ext4 limits a file NAME to 255 bytes, not characters: 120 CJK characters are ~360 bytes and failed
// every time. The title gets what remains after the "-<hash>" tail and the longest suffix appended
// to the base name (".video.<format tag>.part", or ".webm.part.ytdl" from the engine).
private const val FILE_NAME_LIMIT_BYTES = 255
private const val HASH_TAIL_BYTES = 7 // "-" + up to 6 base36 digits of a 31-bit hash
private const val LONGEST_SUFFIX_BYTES = 40 // ".video." + MAX_FORMAT_TAG_CHARS + ".part" = 36, plus slack
private const val MAX_TITLE_BYTES = FILE_NAME_LIMIT_BYTES - HASH_TAIL_BYTES - LONGEST_SUFFIX_BYTES

/** Longest prefix of [text] that encodes to at most [maxBytes] of UTF-8, cut on a code point. */
internal fun truncateToUtf8Bytes(text: String, maxBytes: Int): String {
    var usedBytes = 0
    var endIndex = 0
    while (endIndex < text.length) {
        val codePoint = text.codePointAt(endIndex)
        val codePointBytes = when {
            codePoint < 0x80 -> 1
            codePoint < 0x800 -> 2
            codePoint < 0x10000 -> 3
            else -> 4
        }
        if (usedBytes + codePointBytes > maxBytes) break
        usedBytes += codePointBytes
        endIndex += Character.charCount(codePoint)
    }
    return text.substring(0, endIndex)
}

// A fixed app-private directory built entirely from our own inputs (never a user- or DB-supplied
// path), so unlike a picker-backed destination this needs no root/traversal validation: there is
// exactly one root, and every name under it is one this function generated. internal (not
// private): the delete-matching logic is pure string work, worth unit-testing without a File.
internal fun safeBaseName(ref: VideoRef): String {
    val title = truncateToUtf8Bytes(ref.title.take(120).replace(Regex("[\\\\/:*?\"<>|]"), "_"), MAX_TITLE_BYTES)
        .ifBlank { "video" }
    val suffix = (ref.pageUrl.hashCode() and 0x7fffffff).toString(36) // stable per page URL
    return "$title-$suffix"
}

/** `%(ext)s` lets the engine pick the real extension; [findProducedFile] recovers the concrete
 *  path afterwards. Stable across pause/resume of the same row, so `--continue` keeps matching. */
private fun destTemplate(dir: File, ref: VideoRef): String {
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "${safeBaseName(ref)}.%(ext)s").absolutePath
}

/** True for the finished file AND every sidecar the engine can leave behind for [ref] -- `.part`,
 *  `.ytdl`, or any other extension variant -- since they all share the one `<safeBaseName>.<ext>`
 *  shape regardless of which extension the engine picked. Matching by this shared prefix, instead
 *  of hardcoding a suffix list, is what lets deletion catch a `.part` file without knowing the
 *  engine's sidecar naming scheme in detail. */
internal fun matchesDownloadFile(fileName: String, ref: VideoRef): Boolean =
    fileName.startsWith("${safeBaseName(ref)}.")

// ".part" too: a stale partial of another format must never be picked as the finished file.
private val SUBTITLE_EXTENSIONS = setOf("srt", "ttml", "vtt")
private val UNFINISHED_EXTENSIONS = setOf("part", "ytdl")

/** The media file only -- a subtitle sidecar is written last (same basename) and would otherwise
 *  win "newest", making the row point at a 50 KB .ttml. */
private fun findProducedFile(dir: File, ref: VideoRef): File? {
    return dir.listFiles { f -> f.isFile && matchesDownloadFile(f.name, ref) && f.extension !in SUBTITLE_EXTENSIONS && f.extension !in UNFINISHED_EXTENSIONS }
        ?.maxByOrNull { it.lastModified() }
}

/** Deletes the produced file plus every sidecar for [ref] under [dir] -- the one fixed app-private
 *  download directory, never anywhere else. A row that never started (nothing on disk yet) is not
 *  a failure: [File.listFiles] simply returns nothing to delete. Returns false only when a matched
 *  file is still there after a real delete attempt, so a genuine permission/IO failure can be
 *  surfaced instead of silently pretending it worked. */
private fun deleteDownloadFiles(dir: File, ref: VideoRef, filePath: String?): Boolean {
    // Also match on the recorded file's own basename: the row's title can be enriched after the
    // file was named (bare-URL enqueue), so the ref-derived name alone would miss it.
    val stored = filePath?.let { File(it).nameWithoutExtension }
    val candidates = dir.listFiles { f ->
        f.isFile && (matchesDownloadFile(f.name, ref) || (stored != null && f.nameWithoutExtension == stored))
    } ?: return true
    var allOk = true
    for (file in candidates) {
        val deleted = runCatching { file.delete() }.getOrDefault(false)
        if (!deleted && file.exists()) allOk = false
    }
    return allOk
}

/**
 * Pure derivation of the choosable qualities from a resolved format list, reusing
 * [FormatSelector.select] per candidate height so a chosen "1080p" maps through the exact same
 * video-only+audio-only pairing the player itself uses -- never a hand-rolled pick that could
 * silently drop the paired audio track. Distinct heights are taken from the formats themselves
 * (never invented), highest first; entries that collapse onto the same actual selector (e.g. two
 * source heights both falling back to the same pair) are deduped. An audio-only option is
 * appended last when the engine reported one. Any option routed through the synthetic tier-2
 * selector is dropped -- that id can never be downloaded (see [WEBVIEW_FORMAT_ID]).
 */
internal fun deriveDownloadOptions(formats: List<MediaFormat>, includeManifests: Boolean = true): List<DownloadOption> {
    // StreamDownloader writes bytes to a file, so a manifest "format" would save an .m3u8
    // playlist as the finished video (seen live with visionos HLS). The engine path keeps
    // manifests: yt-dlp fetches segments itself, and some non-YouTube sites are HLS-only.
    val pool = if (includeManifests) formats else formats.filter { it.protocol == Protocol.PROGRESSIVE }
    val heights = pool.mapNotNull { it.height }.filter { it > 0 }.distinct().sortedDescending()
    val seenSelectors = mutableSetOf<String>()
    val videoOptions = heights.mapNotNull { ceiling ->
        val selection = FormatSelector.select(pool, ceiling).selection ?: return@mapNotNull null
        val picked = selectionSummary(selection)
        if (picked.selector.isBlank() || picked.selector.contains(WEBVIEW_FORMAT_ID)) return@mapNotNull null
        if (!seenSelectors.add(picked.selector)) return@mapNotNull null
        DownloadOption(picked.selector, "${picked.height ?: ceiling}p", picked.bytes)
    }
    val audioOption = FormatSelector.select(pool, Int.MAX_VALUE, audioOnly = true).selection?.let { selection ->
        val picked = selectionSummary(selection)
        if (picked.selector.isBlank() || picked.selector.contains(WEBVIEW_FORMAT_ID)) null
        else DownloadOption(picked.selector, "Audio only", picked.bytes)
    }
    return videoOptions + listOfNotNull(audioOption)
}

/** Best-effort Content-Length probe for the quality picker's "…" -> real size upgrade, and for
 *  [StreamDownloader]'s progress total (visionos URLs often carry no clen). Never logs
 *  the URL (same rule as every other media-URL touch point); a failure just leaves the size
 *  unknown, same as if [approxBytes][MediaFormat.filesizeBytes] had never been reported. */
internal fun headContentLength(client: OkHttpClient, format: MediaFormat): Long? {
    if (format.protocol != Protocol.PROGRESSIVE) return null // a manifest has no one Content-Length
    return try {
        val builder = Request.Builder().url(format.url).head()
        format.headers.forEach { (k, v) -> builder.header(k, v) }
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.header("Content-Length")?.toLongOrNull()
        }
    } catch (e: Exception) {
        null
    }
}

private data class SelectionSummary(val selector: String, val height: Int?, val bytes: Long?)

private fun selectionSummary(selection: FormatSelection): SelectionSummary = when (selection) {
    is FormatSelection.Single -> SelectionSummary(selection.format.formatId, selection.format.height, selection.format.filesizeBytes)
    // The engine's own `-f video+audio` selector syntax merges the pair via ffmpeg -- exactly the
    // muxed-download case DESIGN.md calls out, with no extra plumbing here.
    is FormatSelection.Paired -> {
        val vb = selection.video.filesizeBytes
        val ab = selection.audio.filesizeBytes
        val bytes = if (vb == null && ab == null) null else (vb ?: 0L) + (ab ?: 0L)
        SelectionSummary("${selection.video.formatId}+${selection.audio.formatId}", selection.video.height, bytes)
    }
}
