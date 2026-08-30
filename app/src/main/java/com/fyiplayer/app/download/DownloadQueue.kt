package com.fyiplayer.app.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import com.fyiplayer.app.FyiApp
import com.fyiplayer.app.core.CaptionTrack
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
    /** Set by the picker at selection time (never during [deriveDownloadOptions]) when the user
     *  also wants a caption track saved alongside this video -- the chosen track's own
     *  [CaptionTrack.languageCode], re-matched against a fresh resolve at download time. Still not
     *  a URL, so still safe to hold like the rest of this type. */
    val subtitleLanguageCode: String? = null,
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

    @Volatile private var activePageUrl: String? = null
    @Volatile private var activeProcessId: String? = null
    // Set for rows on the direct-stream path (extractor-resolved); null for engine rows.
    @Volatile private var activeStreamSignal: StreamDownloader.CancelSignal? = null

    private fun stopActive() {
        activeStreamSignal?.cancel()
        activeProcessId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
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

    /** Snapshot of the most recent [resolveOptions] resolve. Reused by [fetchApproxBytes] and
     *  [lastCaptions] instead of a second resolve -- safe because the picker is modal, so at most
     *  one download dialog (and therefore one resolve) is ever showing at a time; "most recent"
     *  always means "the video this dialog is for". */
    private data class LastResolve(val pageUrl: String, val formats: List<MediaFormat>, val captions: List<CaptionTrack>)
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
        lastResolve = LastResolve(resolved.ref.pageUrl, resolved.formats, resolved.captions)
        return ResolveOutcome.Ready(resolved.ref, options)
    }

    /** Caption tracks for whatever video the last [resolveOptions] resolved -- read by the download
     *  quality picker to offer the subtitle checkbox without a second resolve. */
    fun lastCaptions(): List<CaptionTrack> = lastResolve?.captions ?: emptyList()

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
                subtitleLanguageCode = option.subtitleLanguageCode,
            ),
        )
        kickService()
        return EnqueueOutcome.Queued
    }

    suspend fun pause(pageUrl: String) {
        if (activePageUrl == pageUrl) {
            stopActive()
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
        if (activePageUrl == pageUrl) stopActive()
        repository.remove(pageUrl)
    }

    /** Same as [cancel], but also deletes the produced file and any leftover `.part`/`.ytdl`
     *  sidecar the engine wrote for this row -- so a partial download removed this way leaves
     *  nothing orphaned on disk. Irreversible; the caller must already have an explicit
     *  confirmation before calling this. Returns false if a matched file resisted deletion (still
     *  there afterwards), so the caller can say so instead of pretending it worked. */
    suspend fun cancelAndDelete(pageUrl: String): Boolean {
        val item = repository.get(pageUrl)
        _errors.update { it - pageUrl }
        if (activePageUrl == pageUrl) stopActive()
        repository.remove(pageUrl)
        return item?.let { deleteDownloadFiles(dir, it.ref, it.filePath) } ?: true
    }

    suspend fun clearCompleted() {
        rows.value.filter { it.state == DownloadState.COMPLETED }.forEach { repository.remove(it.ref.pageUrl) }
    }

    /** Batch [cancelAndDelete] over every completed row. Irreversible; caller confirms first.
     *  Returns false if any file resisted deletion. */
    suspend fun clearCompletedAndDeleteFiles(): Boolean {
        val completed = rows.value.filter { it.state == DownloadState.COMPLETED }
        completed.forEach { repository.remove(it.ref.pageUrl) }
        return completed.fold(true) { allOk, item -> deleteDownloadFiles(dir, item.ref, item.filePath) && allOk }
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
        val streamSignal = activeStreamSignal
        setState(pageUrl, DownloadState.PAUSED)
        activePageUrl = null
        activeProcessId = null
        activeStreamSignal = null
        streamSignal?.cancel()
        processId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
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
            val processId = UUID.randomUUID().toString()
            activePageUrl = pageUrl
            activeProcessId = processId
            // startedAt only on the FIRST run: a paused/resumed row keeps its original start time,
            // so "duration taken" on completion reflects the whole queued lifetime, not just the
            // final resume.
            val running = next.copy(
                state = DownloadState.RUNNING,
                startedAt = next.startedAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            repository.upsert(running)

            var lastWriteMillis = 0L
            val outcome = runDownload(running, processId) { progress ->
                _progress.update { it + (pageUrl to progress) }
                val now = System.currentTimeMillis()
                if (now - lastWriteMillis >= 1_000) {
                    lastWriteMillis = now
                    // Fires on the engine's own callback thread, not a coroutine -- that thread has
                    // nothing else to do until this returns, so a blocking bridge is free here.
                    runCatching {
                        runBlocking {
                            repository.upsert(
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
            activePageUrl = null
            activeProcessId = null
            activeStreamSignal = null
            _progress.update { it - pageUrl }

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
                    repository.upsert(finished)
                    // Best-effort copy into the user's chosen folder, if any -- the row above is
                    // already COMPLETED and stays that way regardless of how this turns out; the
                    // private file it points at is what the in-app Downloads screen opens.
                    produced?.let { file -> treeUri()?.let { uri -> exportToTree(appContext, file, Uri.parse(uri)) } }
                    onFinished(finished, DownloadState.COMPLETED)
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
                    val failed = running.copy(state = DownloadState.FAILED, updatedAt = System.currentTimeMillis())
                    repository.upsert(failed)
                    onFinished(failed, DownloadState.FAILED)
                }
            }
        }
        return true
    }

    private suspend fun setState(pageUrl: String, state: DownloadState) {
        val current = repository.get(pageUrl) ?: return
        repository.upsert(current.copy(state = state, updatedAt = System.currentTimeMillis()))
    }

    /** YouTube rows download through the extractor chain (the only signed-in path — an engine
     *  subprocess is anonymous and hits the same age wall playback used to); every other source
     *  keeps the engine, which is still the only downloader that knows their extractors. */
    private suspend fun runDownload(
        item: DownloadItem,
        processId: String,
        onProgress: (DownloadProgress) -> Unit,
    ): EngineOutcome =
        if (item.ref.sourceId == "youtube") runStream(item, onProgress)
        else runEngine(item, processId, onProgress)

    private suspend fun runStream(
        item: DownloadItem,
        onProgress: (DownloadProgress) -> Unit,
    ): EngineOutcome {
        val resolved = try {
            resolver.resolve(item.ref)
        } catch (e: ExtractionError) {
            return EngineOutcome.Failed(e.userMessage())
        }
        val signal = StreamDownloader.CancelSignal()
        activeStreamSignal = signal
        if (!dir.exists()) dir.mkdirs()
        val baseName = safeBaseName(item.ref)
        return when (val outcome = streamDownloader.download(
            formats = resolved.formats,
            selector = item.formatId,
            dir = dir,
            baseName = baseName,
            signal = signal,
            onProgress = onProgress,
        )) {
            is StreamDownloader.Outcome.Done -> {
                // Best-effort and separate from the video's own outcome: a dead caption track or a
                // network blip here must never fail a download that otherwise finished fine.
                item.subtitleLanguageCode?.let { lang ->
                    withContext(Dispatchers.IO) {
                        runCatching { downloadSubtitle(httpClient, resolved.captions, lang, dir, baseName) }
                    }
                }
                EngineOutcome.Done
            }
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
                // Without --newline the engine rewrites one progress line with \r, so the line
                // reader that feeds the callback below never sees a complete line and progress
                // stays at zero for the whole download.
                addOption("--newline")
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

// A fixed app-private directory built entirely from our own inputs (never a user- or DB-supplied
// path), so unlike a picker-backed destination this needs no root/traversal validation: there is
// exactly one root, and every name under it is one this function generated. internal (not
// private): the delete-matching logic is pure string work, worth unit-testing without a File.
internal fun safeBaseName(ref: VideoRef): String {
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

/** True for the finished file AND every sidecar the engine can leave behind for [ref] -- `.part`,
 *  `.ytdl`, or any other extension variant -- since they all share the one `<safeBaseName>.<ext>`
 *  shape regardless of which extension the engine picked. Matching by this shared prefix, instead
 *  of hardcoding a suffix list, is what lets deletion catch a `.part` file without knowing the
 *  engine's sidecar naming scheme in detail. */
internal fun matchesDownloadFile(fileName: String, ref: VideoRef): Boolean =
    fileName.startsWith("${safeBaseName(ref)}.")

private val SUBTITLE_EXTENSIONS = setOf("srt", "ttml", "vtt")

/** The media file only -- a subtitle sidecar is written last (same basename) and would otherwise
 *  win "newest", making the row point at a 50 KB .ttml. */
private fun findProducedFile(dir: File, ref: VideoRef): File? {
    return dir.listFiles { f -> f.isFile && matchesDownloadFile(f.name, ref) && f.extension !in SUBTITLE_EXTENSIONS }
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

/** Best-effort Content-Length probe for the quality picker's "…" -> real size upgrade. Never logs
 *  the URL (same rule as every other media-URL touch point); a failure just leaves the size
 *  unknown, same as if [approxBytes][MediaFormat.filesizeBytes] had never been reported. */
private fun headContentLength(client: OkHttpClient, format: MediaFormat): Long? {
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

/** Fetches the one matching caption track's file next to the just-finished video, same basename,
 *  the track's own extension. Caller wraps this in `runCatching` -- any failure here (track gone
 *  since the dialog was shown, network error) is swallowed, never fails the video download. */
private fun downloadSubtitle(client: OkHttpClient, captions: List<CaptionTrack>, languageCode: String, dir: File, baseName: String) {
    val track = captions.firstOrNull { it.languageCode == languageCode } ?: return
    val request = Request.Builder().url(track.url).build()
    client.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) return
        val body = resp.body ?: return
        val out = File(dir, "$baseName.${subtitleExtension(track.mimeType)}")
        out.outputStream().use { sink -> body.byteStream().copyTo(sink) }
    }
}

/** Mirrors [com.fyiplayer.app.source.newpipe.captionMimeType]'s three known mime types. */
private fun subtitleExtension(mimeType: String): String = when (mimeType) {
    "application/x-subrip" -> "srt"
    "application/ttml+xml" -> "ttml"
    else -> "vtt"
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
