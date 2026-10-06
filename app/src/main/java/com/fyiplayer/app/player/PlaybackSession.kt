package com.fyiplayer.app.player

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import android.os.Looper
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.exoplayer.text.TextOutput
import com.fyiplayer.app.DiagLog
import com.fyiplayer.app.core.CaptionTrack
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.Resolved
import com.fyiplayer.app.core.SponsorPolicy
import com.fyiplayer.app.core.SponsorSegment
import com.fyiplayer.app.core.StreamResolver
import com.fyiplayer.app.core.decideSponsorAction
import com.fyiplayer.app.core.usableSponsorSegments
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.prefs.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Immutable snapshot for the UI. Deliberately carries no media URL — Contracts.kt's rule.
 * [availableHeights] is the resolution ladder the quality picker may offer, as plain ints derived
 * from the current item's formats; the [MediaFormat]s themselves (signed URLs) never leave
 * [PlaybackSession] — see [selectQuality].
 */
data class PlayerState(
    val current: VideoRef? = null,
    val index: Int = -1,
    val queueSize: Int = 0,
    val queue: List<VideoRef> = emptyList(),
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    /** Player sits in STATE_ENDED: the centre button becomes Replay. */
    val ended: Boolean = false,
    val error: ExtractionError? = null,
    val selectedHeight: Int? = null,
    val availableHeights: List<Int> = emptyList(),
    // Captions default OFF (Contracts.kt's CaptionTrack carries no selection flag, and `init`
    // disables the text renderer to match) -- null means Off, same as [CaptionSheet]'s own model.
    val availableCaptions: List<CaptionTrack> = emptyList(),
    val selectedCaptionLanguage: String? = null,
    val speed: Float = 1f,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffled: Boolean = false,
    /** Bumped each time playback moves to another video on its own (queue auto-advance, autoplay
     *  next). Detail follows it so the watch page never shows video A while B plays. */
    val autoAdvances: Int = 0,
    // Real decoder-reported frame size, 0 until the first frame. Fullscreen orientation locks to
    // this once known, instead of forcing landscape on a portrait video (see applyAspectOrientation).
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    // False from the moment an item becomes current until its first frame hits the surface --
    // the window where the player's shutter is black. Shorts pages cover it with the thumbnail.
    val firstFrameRendered: Boolean = false,
)

/** Position/duration only, ticked every 500ms by [PlaybackSession.tickPosition] -- split out of
 *  [PlayerState] because folding these into it made every state.collectAsState() reader (mini
 *  player, queue bar, the whole PlayerScreen, the shorts pager and every page it hands PlayerState
 *  to) recompose twice a second even when it shows none of this. Only the narrow progress-bar /
 *  elapsed-time composables should collect [PlaybackSession.progress]; [PlayerState] now changes
 *  on real events only (item change, play/pause, queue edits, speed...). */
data class PlaybackProgress(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

/**
 * Process-scoped owner of the player, the queue and [PlayerState]. A plain object, not a
 * ViewModel: it must outlive every screen, or navigating away from a playing video leaves audio
 * running with no UI handle on it.
 *
 * ExoPlayer's own playlist holds at most two entries: the item playing and one prefetched ahead.
 * It is never handed the whole queue — stream URLs are signed and short-lived, so loading item 40
 * while item 3 plays would hand the player links already dead by the time it gets there. [window]
 * tracks which queue indices those two timeline slots currently correspond to, so a skip or a
 * queue edit can tell whether the prefetched slot is still the right one.
 *
 * Injection seam: the Application class MUST call [init] once at process start, before any screen
 * touches playback. [maxHeight] is read synchronously — resolving can't block on a second
 * suspension mid-flight, so the caller mirrors its resolution setting into something synchronous
 * (e.g. a StateFlow's `.value`) and hands that read here.
 */
object PlaybackSession {
    private lateinit var player: ExoPlayer
    private lateinit var resolver: StreamResolver
    private lateinit var maxHeight: () -> Int
    private lateinit var scope: CoroutineScope
    private lateinit var appContext: Context

    // Mirror of Prefs.backgroundPlayback: the ON_STOP callback below needs a synchronous read,
    // and re-reads reactively so flipping the setting applies without an app restart.
    @Volatile private var backgroundPlaybackAllowed = true

    // Mirror of the SponsorBlock prefs (master switch, per-category modes, channel whitelist),
    // same pattern as maxHeight: skip checks run on the player thread and cannot suspend on a
    // DataStore read.
    private var sponsorPolicy: () -> SponsorPolicy = { SponsorPolicy() }
    // Injected by FyiApp; returns null when the pref is off, the search fails, or nothing
    // qualifies -- STATE_ENDED's handler treats null as "nothing to autoplay", same as no queue.
    private var autoplayNext: suspend (VideoRef) -> VideoRef? = { null }
    // Position persistence seams (FyiApp gates on the pref and owns the near-end-clears rule);
    // the session only decides WHEN: resume lookup at item start, save on tick/pause/end.
    private var loadPosition: suspend (String) -> Long? = { null }
    private var savePosition: suspend (String, Long, Long) -> Unit = { _, _, _ -> }
    // Playback resumption seams (page URL + title only, never a media URL): the owner persists the
    // last non-short item that started, and hands it back when a headset/Bluetooth play press finds
    // the process or service reborn over an empty player. Defaults make resumption a no-op.
    private var saveLastPlayed: suspend (pageUrl: String, title: String) -> Unit = { _, _ -> }
    private var loadLastPlayed: suspend () -> VideoRef? = { null }
    private var sponsorFetchJob: Job? = null
    // Segments for the item currently at `index`. Never trusted after an item change until
    // fetchSponsorSegments's own index check confirms the response is still for the right item.
    private var sponsorSegments: List<SponsorSegment> = emptyList()
    private val _sponsorMarkers = MutableStateFlow<List<SponsorSegment>>(emptyList())
    /** Segments the seekbar draws: those the user's current modes and channel whitelist allow. */
    val sponsorMarkers: StateFlow<List<SponsorSegment>> = _sponsorMarkers.asStateFlow()
    private val _sponsorOffer = MutableStateFlow<SponsorSegment?>(null)
    /** The show-button segment playback is inside right now, or null. */
    val sponsorOffer: StateFlow<SponsorSegment?> = _sponsorOffer.asStateFlow()
    private var lastSkippedSegmentStart: Long? = null // guards re-seeking every tick inside a segment

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    val exoPlayer: ExoPlayer get() = player

    private var queue: List<VideoRef> = emptyList()
    private var order: List<Int>? = null // shuffle order; null = queue order
    private var index: Int = -1

    // player timeline position -> queue index, at most 2 entries: [current] or [current, prefetched]
    private var window: List<Int> = emptyList()
    private var prepared: PreparedItem? = null // resolved-ahead item, adopted without a re-resolve
    // The current item's raw formats, kept private — see PlayerState's doc. Only heights derived
    // from this ever reach [PlayerState.availableHeights].
    private var currentFormats: List<MediaFormat> = emptyList()
    // Same item's caption tracks, re-attached to every rebuilt MediaSource (selectQuality included)
    // -- see MediaItemFactory.create's doc for why the merge has to happen on every rebuild.
    private var currentCaptions: List<CaptionTrack> = emptyList()
    private var loadJob: Job? = null
    private var prefetchJob: Job? = null
    private var tickerJob: Job? = null
    // One re-resolve attempt per item after an expired URL or a transport failure. Re-armed on item
    // change, on a play press, and after HEALTHY_TICKS_TO_REARM_RETRY ticks of continuous playing.
    private var retriedIndex: Int? = null
    // The other bounded error recoveries (see PlayerErrorPolicy). Per item: re-armed with
    // resetPlayerErrorBudget() when a genuinely new item starts and after a healthy stretch.
    private var defaultPositionSeeks = 0
    private var lowerRenditionSpent = false
    // Not per item: the surface belongs to the app, so its cooldown is wall-clock.
    private var lastSurfaceRecoveryAtMs: Long? = null
    private var autoplayFired = false // guards STATE_ENDED's possible re-emission from double-firing autoplay
    // The pending "what plays after the queue ends" lookup. Kept so a user's own pick can cancel it.
    private var autoplayJob: Job? = null
    // The item the PLAYER actually holds, as opposed to `index`/state.current which a skip or pick
    // publishes before its resolve lands. Position saves must attribute the player's position to
    // this one, or a pause during the resolve would write the old video's time onto the new one.
    private var loadedRef: VideoRef? = null

    // Diagnostic timestamps (SystemClock.elapsedRealtime, ms); 0 = not set. See DiagLog.
    private var loadBeganAtMs = 0L
    private var awaitingReadyLog = false
    private var awaitingFirstFrameLog = false
    private var pausedAtMs = 0L // when playWhenReady last went false
    private var resumePressedAtMs = 0L // set by a plain resume, cleared when audio is actually playing

    private const val HEALTHY_TICKS_TO_REARM_RETRY = 60 // 60 x 500ms = 30s of continuous playing

    private class PreparedItem(
        val queueIndex: Int,
        val resolved: Resolved,
        val selection: FormatSelection,
        val height: Int?,
    )

    fun init(
        context: Context,
        resolver: StreamResolver,
        maxHeight: () -> Int = { 1080 },
        sponsorPolicy: () -> SponsorPolicy = { SponsorPolicy() },
        autoplayNext: suspend (VideoRef) -> VideoRef? = { null },
        loadPosition: suspend (String) -> Long? = { null },
        savePosition: suspend (String, Long, Long) -> Unit = { _, _, _ -> },
        saveLastPlayed: suspend (pageUrl: String, title: String) -> Unit = { _, _ -> },
        loadLastPlayed: suspend () -> VideoRef? = { null },
    ) {
        if (::player.isInitialized) return
        this.resolver = resolver
        this.maxHeight = maxHeight
        this.sponsorPolicy = sponsorPolicy
        this.autoplayNext = autoplayNext
        this.loadPosition = loadPosition
        this.savePosition = savePosition
        this.saveLastPlayed = saveLastPlayed
        this.loadLastPlayed = loadLastPlayed
        appContext = context.applicationContext
        MediaItemFactory.init(appContext)
        // Backstop for any launch without its own catch: log the class and carry on rather than
        // letting an uncaught exception from a SupervisorJob child kill the process.
        val uncaughtHandler = CoroutineExceptionHandler { _, throwable ->
            diag("uncaught in session scope error=${throwable.javaClass.simpleName}")
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + uncaughtHandler)
        // Sideloaded captions ride SingleSampleMediaSource, which hands the renderer RAW subtitle
        // samples — media3's "legacy decoding" path, disabled by default since 1.4. Without this
        // opt-in, selecting any caption kills playback with IllegalStateException ("can't handle
        // application/ttml+xml samples"), which the UI then mislabels as a network failure.
        val renderersFactory = object : DefaultRenderersFactory(appContext) {
            override fun buildTextRenderers(
                context: Context,
                output: TextOutput,
                outputLooper: Looper,
                extensionRendererMode: Int,
                out: ArrayList<Renderer>,
            ) {
                // The platform's caption files embed their own region positioning, which lands
                // cues at the TOP of the surface. Stripping position/line/anchor per cue drops
                // every track to SubtitleView's default placement: bottom-centered.
                val bottomAnchored = TextOutput { cueGroup ->
                    output.onCues(
                        CueGroup(
                            cueGroup.cues.map {
                                it.buildUpon()
                                    .setLine(Cue.DIMEN_UNSET, Cue.LINE_TYPE_FRACTION)
                                    .setLineAnchor(Cue.TYPE_UNSET)
                                    .setPosition(Cue.DIMEN_UNSET)
                                    .setPositionAnchor(Cue.TYPE_UNSET)
                                    .setSize(Cue.DIMEN_UNSET)
                                    .build()
                            },
                            cueGroup.presentationTimeUs,
                        ),
                    )
                }
                super.buildTextRenderers(context, bottomAnchored, outputLooper, extensionRendererMode, out)
                out.filterIsInstance<TextRenderer>()
                    .forEach { it.experimentalSetLegacyDecodingEnabled(true) }
            }
        }
        // Default LoadControl targets 2.5s-to-first-frame / 50s max buffer, tuned for on-device
        // local files. Shortened min/max (12s/20s) trades some rebuffer resilience for faster
        // start on a network stream; matches what PipePipe ships for the same reason.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 12_000,
                /* maxBufferMs = */ 20_000,
                /* bufferForPlaybackMs = */ 2_000,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        player = ExoPlayer.Builder(appContext, renderersFactory).setLoadControl(loadControl).build().apply {
            // audio focus and becoming-noisy belong on the player, not the media session
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            setHandleAudioBecomingNoisy(true)
            // Screen-off streaming: without a held CPU + WiFi lock, background playback stalls
            // once the device dozes. Needs WAKE_LOCK (declared in the manifest).
            setWakeMode(C.WAKE_MODE_NETWORK)
            addListener(playerListener)
            // Captions off by default (project requirement): a subtitle track carries no
            // selection/default flag (MediaItemFactory), but text tracks with no flag can still be
            // auto-picked by locale heuristics -- disabling the renderer outright is the only
            // deterministic "off".
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }
        // A second Prefs instance is fine: preferencesDataStore's delegate is keyed on the
        // (shared) applicationContext, so this and FyiApp's Prefs share one underlying store.
        Prefs(appContext).backgroundPlayback
            .onEach { backgroundPlaybackAllowed = it }
            .launchIn(scope)
        // Setting OFF means "don't keep playing when backgrounded" -- honour that on the one
        // process-wide lifecycle signal for foreground/background, not per-Activity (a rotation
        // or navigating between screens must not look like backgrounding).
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP && !backgroundPlaybackAllowed) player.pause()
            }
        )
    }

    private fun ensureInit() = check(::player.isInitialized) { "PlaybackSession.init() was not called" }

    private fun diag(line: String) = DiagLog.log("Playback", line)

    private fun resetPlayerErrorBudget() {
        defaultPositionSeeks = 0
        lowerRenditionSpent = false
    }

    private fun msSince(startedAtMs: Long): Long = SystemClock.elapsedRealtime() - startedAtMs

    /** Looks up what plays after [endedIndex] finished, and starts it unless the user moved on
     *  meanwhile. startAt/clear cancel [autoplayJob]; the re-check covers a replay or a seek that
     *  left the same item un-ended without going through them. */
    private fun startAutoplay(endedRef: VideoRef, endedIndex: Int) {
        autoplayJob?.cancel()
        autoplayJob = scope.launch {
            val lookedUp: VideoRef? = try {
                autoplayNext(endedRef)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                diag("autoplay lookup failed error=${e.javaClass.simpleName}")
                null
            }
            val next = lookedUp ?: return@launch
            val stillOnEndedItem = index == endedIndex &&
                queue.getOrNull(index)?.pageUrl == endedRef.pageUrl &&
                player.playbackState == Player.STATE_ENDED
            if (!stillOnEndedItem) return@launch
            // play() -> startAt() cancels autoplayJob; this coroutine is it, so detach first.
            autoplayJob = null
            play(listOf(next), 0)
            _state.update { it.copy(autoAdvances = it.autoAdvances + 1) }
        }
    }

    fun play(refs: List<VideoRef>, startIndex: Int) {
        ensureInit()
        // play() always means a genuinely new queue (never a mid-queue continuation -- those go
        // through skipNext/skipPrevious/playAt instead), so the OLD item must not keep playing
        // (audibly or visibly) into whatever surface picks it up next -- e.g. the Shorts pager
        // reattaching the one shared PlayerView the instant this is called, well before startAt's
        // async resolve below hands it a new source. clearMediaItems() (not just stop(), which
        // alone can leave PlayerView's shutter closed-check believing it's still the same period
        // -- see PlayerView.ComponentListener#onTracksChanged) empties the timeline, which is what
        // actually makes PlayerView close its shutter instead of holding the outgoing frame.
        persistPosition() // the outgoing item's position, while the player still holds it
        player.stop()
        player.clearMediaItems()
        loadedRef = null
        startPlaybackService()
        queue = refs
        order = null
        index = QueueMath.clamp(startIndex, refs.size)
        prepared = null
        retriedIndex = null
        autoplayFired = false
        currentFormats = emptyList()
        currentCaptions = emptyList()
        // a fresh state must seed isPlaying from the player: onIsPlayingChanged only fires on a
        // change, and skipping between two already-playing items would otherwise never fire it.
        // speed is seeded too: it's a player-level setting that survives across queues.
        // Loop is per video (YouTube's shape): a new queue starts un-looped, and the player's own
        // flag must follow or state says OFF while the player keeps repeating.
        player.repeatMode = Player.REPEAT_MODE_OFF
        _state.value = PlayerState(
            index = index, queueSize = queue.size, queue = queue,
            isPlaying = player.isPlaying, speed = player.playbackParameters.speed,
            autoAdvances = _state.value.autoAdvances, // a counter, must survive the reset
        )
        _progress.value = PlaybackProgress()
        startAt(index)
    }

    /**
     * PlaybackService.onGetSession just hands back the session over this same player, so starting
     * it here (idempotent if already running) is enough for lockscreen/Bluetooth controls and the
     * notification to exist for the rest of this queue's lifetime.
     * Plain startService, NOT startForegroundService: the latter arms the OS's
     * must-call-startForeground timer, but media3 only promotes the service to foreground once a
     * session is actually engaged (playWhenReady + READY/BUFFERING) -- if resolution is still
     * running when the timer fires, the system kills the whole app
     * (ForegroundServiceDidNotStartInTimeException, seen on device).
     * Not guaranteed to be allowed: autoplay calls play() from STATE_ENDED while the app may be
     * backgrounded, and Android 12+ then refuses with ForegroundServiceStartNotAllowedException
     * (an IllegalStateException). Playback itself needs no service, so a refusal only costs the
     * notification/lockscreen controls -- never the playback call.
     */
    private fun startPlaybackService() {
        try {
            appContext.startService(Intent(appContext, PlaybackService::class.java))
        } catch (e: RuntimeException) {
            diag("startService refused error=${e.javaClass.simpleName}")
        }
    }

    /** media3 stops [PlaybackService] after a long pause (seen on device: 25 min paused, process
     *  alive, no service and no media session left). The player itself stays prepared, so an
     *  in-app resume played -- but with no session there was no notification, no lockscreen or
     *  headset control and no foreground service keeping background playback alive. Starting an
     *  already-running service is a no-op. */
    private fun ensureServiceForResume() = startPlaybackService()

    /** Inserts [ref] to play right after the current item. A video already in the queue is moved
     *  there instead of duplicated -- the queue never holds the same page twice. */
    fun playNext(ref: VideoRef) {
        ensureInit()
        if (queue.isEmpty()) { play(listOf(ref), 0); return }
        val existing = queue.indexOfFirst { it.pageUrl == ref.pageUrl }
        if (existing == index) return
        if (existing >= 0) {
            move(existing, QueueMath.playNextTarget(existing, index))
            return
        }
        val insertAt = index + 1
        queue = queue.toMutableList().apply { add(insertAt, ref) }
        order = order?.let {
            val playPos = it.indexOf(index)
            QueueMath.insertOrder(it, insertAt, playPos + 1)
        }
        // insertion always lands exactly where a prefetch would have; simplest correct thing is
        // to drop it and let prefetchNext redo the resolve against the now-current queue.
        dropPrefetchedWindowSlot()
        publishQueueState()
        prefetchNext()
    }

    /** Appends [ref] to the end of the queue. Returns false (and does nothing) when the same page
     *  is already queued -- callers show "already in queue" instead of a silent duplicate. */
    fun enqueue(ref: VideoRef): Boolean {
        ensureInit()
        if (queue.isEmpty()) { play(listOf(ref), 0); return true }
        if (queue.any { it.pageUrl == ref.pageUrl }) return false
        val newIndex = queue.size
        queue = queue + ref
        order = order?.let { QueueMath.appendOrder(it, newIndex) }
        publishQueueState()
        // Root cause of "Queue does nothing": every other mutator (playNext/move/removeAt) calls
        // prefetchNext() after touching `queue`, so it re-derives from the grown list. This one
        // didn't. Most visible when the old queue had already run out (nextIndex was null, player
        // sat in STATE_ENDED with nothing scheduled) -- appending never re-checked, so playback
        // just stayed stopped forever. prefetchNext() re-derives via QueueMath.nextIndex against
        // the live queue and, once it resolves, calls player.addMediaSource -- ExoPlayer resumes
        // out of ENDED on its own once a next period exists and playWhenReady is still true.
        prefetchNext()
        return true
    }

    /** Advance one item. Reuses the prefetched slot when it's still the right one, so the common
     *  case costs no re-resolve and no network wait. */
    fun skipNext() {
        ensureInit()
        val target = QueueMath.nextIndex(index, queue.size, _state.value.repeatMode, order) ?: return
        val item = prepared
        if (window.size > 1 && window[1] == target && item != null && item.queueIndex == target) {
            persistPosition() // before adoptPrepared moves loadedRef and the seek moves the player
            index = target
            adoptPrepared(item)
            player.seekTo(1, 0L)
            trimConsumedWindow()
            player.play()
            prefetchNext()
            return
        }
        switchToItem(target)
    }

    /** Back one item. Always re-resolves: only the current item and the one ahead of it are ever
     *  kept live, so the previous item's signed URL is long gone. */
    fun skipPrevious() {
        ensureInit()
        val target = QueueMath.previousIndex(index, queue.size, _state.value.repeatMode, order) ?: return
        switchToItem(target)
    }

    /** Jump to an arbitrary queue position — what tapping a row in the queue list does. Always
     *  re-resolves: only the current item and the one ahead of it ever hold a live signed URL. */
    fun playAt(i: Int) {
        ensureInit()
        if (i !in queue.indices || i == index) return
        switchToItem(i)
    }

    /** The user moved to [target] and its source has to be resolved: publish the move NOW, not
     *  after the async resolve in [startAt]. The queue sheet opens the target's watch page right
     *  after playAt, and DetailScreen's entry guard replaces the whole queue with a single-item one
     *  whenever state.current is not the page's video; the shorts pager decides its next swipe from
     *  state.index, which lagged a whole resolve behind on the skip paths. firstFrameRendered goes
     *  false because the surface still shows the old item's frame until the new one lands. */
    private fun switchToItem(target: Int) {
        index = target
        prepared = null
        retriedIndex = null
        publishQueueState()
        _state.update { it.copy(firstFrameRendered = false, error = null) }
        startAt(target)
    }

    /** Reorders the queue: moves the item at [from] to [to]. Keeps [index] pointing at the same
     *  item it did before the move. The prefetch slot is always rebuilt: under shuffle the
     *  prefetched item can sit anywhere in the queue, so "both ends before the index" is no proof
     *  it was untouched. */
    fun move(from: Int, to: Int) {
        ensureInit()
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val item = queue[from]
        queue = queue.toMutableList().apply { removeAt(from); add(to, item) }
        order = order?.let { QueueMath.moveOrder(it, from, to) }
        dropAndRemapWindow { QueueMath.indexAfterMove(it, from, to) }
        index = QueueMath.indexAfterMove(index, from, to)
        publishQueueState()
        continueAfterQueueEdit()
    }

    /** After an edit that renumbered the queue: an in-flight load still carries the OLD index, so
     *  it is restarted under the new one; otherwise just refill the prefetch slot. */
    private fun continueAfterQueueEdit() {
        if (loadJob?.isActive == true) startAt(index) else prefetchNext()
    }

    /** Drops the loaded prefetch slot, then re-points the surviving window entry (the current
     *  item) through [remap] after a queue edit shifted positions. Without the remap `window` keeps
     *  the old numbering, onMediaItemTransition then finds no ready item and prefetchNext's
     *  `window[0] != index` guard bails forever. */
    private fun dropAndRemapWindow(remap: (Int) -> Int) {
        dropPrefetchedWindowSlot()
        window = window.map(remap)
    }

    fun seekTo(positionMs: Long) {
        ensureInit()
        player.seekTo(positionMs)
        // The ticker only runs while playing; without this a seek while paused leaves the
        // scrubber on the old position until playback resumes.
        _progress.update { it.copy(positionMs = positionMs.coerceAtLeast(0)) }
    }

    fun togglePlayPause() {
        ensureInit()
        val ref = queue.getOrNull(index)
        val pausedForMs = if (pausedAtMs == 0L) -1L else msSince(pausedAtMs)
        // Error state, or a source the player itself gave up on (e.g. a killed surface/source
        // after sitting backgrounded a couple of minutes -- flipping playWhenReady on a dead
        // source does nothing visible): recover instead of toggling a player with nothing to play.
        if (ref != null && (_state.value.error != null || player.playbackState == Player.STATE_IDLE)) {
            val reason = if (_state.value.error != null) "error" else "idle"
            diag("toggle branch=retryCurrent reason=$reason pausedMs=$pausedForMs")
            retryCurrent()
            return
        }
        // Resume just plays, even after hours paused: the source stays prepared and signed URLs
        // usually outlive the old 50-min guess (YouTube's run ~6h), so a proactive re-resolve cost
        // a full extractor call for nothing (PipePipe: play, recover on error). A really dead URL
        // fails fast into onPlayerError's position-preserving re-resolve; re-arm it here so an
        // earlier blip on this item can't leave a long-paused resume with no recovery left.
        if (!player.playWhenReady) retriedIndex = null
        if (player.playbackState == Player.STATE_ENDED) {
            diag("toggle branch=replayFromEnded pausedMs=$pausedForMs")
            // Replaying: a still-pending related-video lookup must not hijack it, and the next
            // end of this video may autoplay again.
            autoplayJob?.cancel()
            autoplayJob = null
            autoplayFired = false
            ensureServiceForResume()
            player.seekTo(0)
            player.playWhenReady = true
            return
        }
        val resuming = !player.playWhenReady
        diag("toggle branch=${if (resuming) "plainResume" else "pause"} pausedMs=$pausedForMs")
        if (resuming) {
            resumePressedAtMs = SystemClock.elapsedRealtime()
            ensureServiceForResume()
        }
        player.playWhenReady = resuming
    }

    /** Loop the current video on/off. */
    fun toggleLoop() {
        setRepeatMode(if (_state.value.repeatMode == RepeatMode.ONE) RepeatMode.OFF else RepeatMode.ONE)
    }

    /** Recovers the current item: re-resolves and re-prepares in place, same machinery as the
     *  expired-URL path above and [onPlayerError]'s auto re-resolve, just triggered manually --
     *  the Retry action on an error state, or [togglePlayPause] finding the player dead. Position
     *  is read from [progress] rather than the player: a failed resolve already cleared the
     *  player's own source (see [failItem]), so the player's own
     *  [ExoPlayer.getCurrentPosition] can no longer be trusted for where playback actually was --
     *  [tickPosition] stops writing [progress] the moment the error path cancels [tickerJob], so
     *  its last value is exactly the frozen resume point. */
    fun retryCurrent() {
        ensureInit()
        val ref = queue.getOrNull(index) ?: return
        val resumeAt = _progress.value.positionMs
        diag("retryCurrent index=$index resumeAtMs=$resumeAt")
        ensureServiceForResume()
        resolver.invalidate(ref.pageUrl) // resolver may have cached the dead/stale result
        _state.update { it.copy(error = null) }
        startAt(index, resumeAtMs = resumeAt)
    }

    fun setSpeed(speed: Float) {
        ensureInit()
        player.setPlaybackSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    /** Reselects a format from the current item's already-resolved list — no re-resolve, no
     *  network wait. [height] null reapplies the configured ceiling ("Auto"). Only ever picks
     *  from [currentFormats], so this can never offer a height the item has no format for. */
    fun selectQuality(height: Int?) {
        ensureInit()
        if (currentFormats.isEmpty()) return
        val result = FormatSelector.select(currentFormats, height ?: maxHeight())
        val selection = result.selection ?: return
        val resumeAt = player.currentPosition
        // A quality change must not start playback the user had paused.
        val wasPlayWhenReady = player.playWhenReady
        prepared = null
        window = listOf(index)
        player.setMediaSource(MediaItemFactory.create(selection, queue.getOrNull(index), currentCaptions))
        player.prepare()
        player.seekTo(resumeAt)
        player.playWhenReady = wasPlayWhenReady
        val newHeight = when (selection) {
            is FormatSelection.Single -> selection.format.height
            is FormatSelection.Paired -> selection.video.height
        }
        // Same item, different rendition -- the caption pick survives (carryOverCaptionSelection),
        // unlike every other reload path below, which starts a genuinely different item at Off.
        val language = carryOverCaptionSelection(_state.value.selectedCaptionLanguage, isSameItem = true)
        applyCaptionSelection(language)
        _state.update { it.copy(selectedHeight = newHeight, selectedCaptionLanguage = language) }
        prefetchNext()
    }

    /** Selects a text track by language, or null for Off. [availableCaptions] already lists what
     *  the current item published, so the caller (CaptionSheet) always passes back one of those or
     *  null -- this never guesses at a track the player has no source for. */
    fun selectCaption(track: CaptionTrack?) {
        ensureInit()
        applyCaptionSelection(track?.languageCode)
        _state.update { it.copy(selectedCaptionLanguage = track?.languageCode) }
    }

    /** Mutates the player's own [androidx.media3.common.TrackSelectionParameters] -- language-code
     *  matching, not a track-index override, is what survives [selectQuality] rebuilding the
     *  MediaSource with a fresh (re-indexed) subtitle track group. */
    private fun applyCaptionSelection(languageCode: String?) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, languageCode == null)
            .setPreferredTextLanguage(languageCode)
            .build()
    }

    fun setRepeatMode(mode: RepeatMode) {
        ensureInit()
        _state.update { it.copy(repeatMode = mode) }
        // REPEAT_ONE is handled entirely by the player (loops the current source, no re-resolve);
        // REPEAT_ALL/OFF cross-item wrap is our own job — see onMediaItemTransition below.
        player.repeatMode = if (mode == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        dropPrefetchedWindowSlot() // the old prefetch may no longer match under the new mode
        prefetchNext()
    }

    fun toggleShuffle() {
        ensureInit()
        val enabling = order == null
        order = if (enabling) QueueMath.shuffleOrder(queue.size, seed = System.currentTimeMillis()) else null
        dropPrefetchedWindowSlot() // the old prefetch may no longer be next under the new order
        _state.update { it.copy(shuffled = enabling) }
        prefetchNext()
    }

    fun removeAt(i: Int) {
        ensureInit()
        if (i !in queue.indices) return
        val wasCurrent = i == index
        queue = queue.toMutableList().apply { removeAt(i) }
        if (queue.isEmpty()) { clear(); return }
        order = order?.let { QueueMath.removeOrder(it, i) }
        if (wasCurrent) {
            dropPrefetchedWindowSlot()
            // clamp, not just startAt(clamp): removing the last item leaves `index` past the end,
            // and resolveItem only reports a failure for the item that equals `index`.
            index = QueueMath.clamp(index, queue.size)
            startAt(index)
            return
        }
        dropAndRemapWindow { QueueMath.indexAfterRemove(it, removed = i) }
        index = QueueMath.indexAfterRemove(index, removed = i)
        publishQueueState()
        continueAfterQueueEdit()
    }

    /** Drops every queue entry except the one currently playing -- the queue bar's ×/"Clear".
     *  Closing the queue dismisses upcoming items, never the current video: queue ≠ player, so
     *  playback is never interrupted. Only the prefetched-next player slot (if any) is torn down;
     *  the current item's MediaSource is untouched. */
    fun clearQueue() {
        ensureInit()
        val current = queue.getOrNull(index)
        if (current == null) { clear(); return } // nothing playing: same as a full clear
        dropPrefetchedWindowSlot() // removes the prefetched slot from the player, if one exists
        queue = listOf(current)
        order = null
        index = 0
        window = listOf(0) // queue just got reindexed to a single item at 0
        retriedIndex = null // tied to the old index numbering, now meaningless
        // The shuffle order indexed the old queue, so it had to go; say so, or the button keeps
        // claiming shuffle is on over a queue that no longer has one.
        _state.update { it.copy(shuffled = false) }
        publishQueueState()
        prefetchNext() // no-op on a 1-item queue, but every mutator ends with this -- see class doc
    }

    /** The watch page resolves title/uploader/thumbnail AFTER playback of a bare URL ref (share/
     *  open-with) started; without this the mini player and lockscreen show an empty title. Only
     *  metadata changes -- same pageUrl, nothing about playback is touched. */
    fun updateCurrentMeta(ref: VideoRef) {
        ensureInit()
        val i = index
        if (queue.getOrNull(i)?.pageUrl != ref.pageUrl) return
        queue = queue.toMutableList().also { it[i] = ref }
        _state.update { st -> if (st.current?.pageUrl == ref.pageUrl) st.copy(current = ref, queue = queue) else st }
    }

    fun clear() {
        ensureInit()
        persistPosition() // before the player is emptied below
        loadJob?.cancel(); prefetchJob?.cancel(); tickerJob?.cancel()
        autoplayJob?.cancel(); autoplayJob = null
        loadedRef = null
        queue = emptyList(); order = null; index = -1
        window = emptyList(); prepared = null; retriedIndex = null
        currentFormats = emptyList()
        currentCaptions = emptyList()
        clearSponsorSegments()
        player.stop()
        player.clearMediaItems()
        _state.value = PlayerState()
        _progress.value = PlaybackProgress()
        // nothing left to play: drop the notification/session instead of leaving a stale one up
        appContext.stopService(Intent(appContext, PlaybackService::class.java))
    }

    fun release() {
        loadJob?.cancel(); prefetchJob?.cancel(); tickerJob?.cancel(); sponsorFetchJob?.cancel()
        autoplayJob?.cancel(); autoplayJob = null
        if (::player.isInitialized) player.release()
    }

    private fun publishQueueState() {
        _state.update {
            it.copy(index = index, queueSize = queue.size, queue = queue, current = queue.getOrNull(index))
        }
    }

    // ponytail: the tier2 WebView fallback (engine/WebViewResolver.kt) returns one formatless
    // entry (height = null) for what's really an adaptive HLS master — mapNotNull drops it, so
    // that path's quality sheet shows "Auto" only instead of the renditions Media3 actually
    // parses out of the master. Honest, not wrong: no fake resolution gets offered. Add a second,
    // track-selection-mode sheet (Player.Listener onTracksChanged -> per-height
    // TrackSelectionOverride, instead of this formats list) only if that tier2 path turns out to
    // matter enough to spend the extra plumbing on.
    private fun availableHeightsOf(formats: List<MediaFormat>): List<Int> =
        formats.filter { !it.isAudioOnly }.mapNotNull { it.height }.distinct().sortedDescending()

    /** Resolve [i] and load it as the only window item, then prefetch the one after it.
     *  [resumeAtMs], when given, seeks back to it after the fresh source is prepared -- used by
     *  the expiry re-resolve path (onPlayerError) where this is the same item continuing, not a
     *  genuinely new one starting at 0. */
    private fun startAt(i: Int, resumeAtMs: Long? = null) {
        loadJob?.cancel()
        prefetchJob?.cancel()
        // Whatever the user picks here supersedes a pending "play a related video" lookup.
        autoplayJob?.cancel()
        autoplayJob = null
        val ref = queue.getOrNull(i) ?: return
        // A genuinely new item replaces the one the player holds once its resolve lands; save the
        // outgoing item's position now. A same-item restart (retry, expiry) keeps its own.
        if (resumeAtMs == null) {
            persistPosition()
            resetPlayerErrorBudget()
        }
        autoplayFired = false // a genuinely new item is starting -- re-arm the end-of-queue check
        window = emptyList()
        clearSponsorSegments() // item is changing -- the old item's segments must not carry over
        loadBeganAtMs = SystemClock.elapsedRealtime()
        awaitingReadyLog = false
        awaitingFirstFrameLog = false
        diag("load start index=$i resumeGiven=${resumeAtMs != null}")
        warmNext(i)
        loadJob = scope.launch {
            // resumeAtMs given = the SAME item continuing (expiry re-resolve, retry). A genuinely
            // new start may pick up its saved resume point instead. Shorts never resume -- a
            // swipe-through clip restarting mid-way would just be confusing. The lookup runs beside
            // the resolve so reading it costs no extra time on the path to the first frame.
            val savedPositionLookup = if (resumeAtMs == null && !ref.isShort) {
                async { loadPosition(ref.pageUrl) }
            } else {
                null
            }
            val item = resolveItem(i, ref) ?: return@launch
            val source = MediaItemFactory.create(item.selection, ref, item.resolved.captions)
            // The source starts AT the resume point. Seeking after prepare() let playback begin
            // from 0 first (audible blip, wasted first segment fetch) before jumping.
            val resume = resumeAtMs ?: savedPositionLookup?.await()
            if (resume != null) player.setMediaSource(source, resume) else player.setMediaSource(source)
            player.prepare()
            loadedRef = ref
            rememberForResumption(ref)
            awaitingReadyLog = true
            awaitingFirstFrameLog = true
            diag("sourceSet afterLoadStartMs=${msSince(loadBeganAtMs)}")
            player.playWhenReady = true
            // retriedIndex is deliberately NOT cleared here. This runs on the re-resolve that a
            // failed item triggered, so clearing it would re-arm the retry for the same item and
            // a host that rejects every fresh URL (403) would loop forever. It is cleared only
            // when the user moves to another item or presses play, or once the item has played
            // for HEALTHY_TICKS_TO_REARM_RETRY ticks (a URL that works that long is not looping).
            window = listOf(i)
            currentFormats = item.resolved.formats
            currentCaptions = item.resolved.captions
            fetchSponsorSegments(i, ref)
            // A different item always starts captions at Off, never whatever the previous item had.
            val language = carryOverCaptionSelection(_state.value.selectedCaptionLanguage, isSameItem = false)
            applyCaptionSelection(language)
            _state.update {
                it.copy(
                    current = ref, firstFrameRendered = false, index = i, queueSize = queue.size, queue = queue, error = null,
                    selectedHeight = item.height, availableHeights = availableHeightsOf(item.resolved.formats),
                    availableCaptions = item.resolved.captions, selectedCaptionLanguage = language,
                    isPlaying = player.isPlaying,
                    // A new item's size is unknown until its own first frame; fullscreen orientation
                    // reads these and would briefly lock to the previous item's shape. The same-item
                    // paths (retry, expiry re-resolve) keep theirs: the decoder reports no new size.
                    videoWidth = if (resumeAtMs == null) 0 else it.videoWidth,
                    videoHeight = if (resumeAtMs == null) 0 else it.videoHeight,
                )
            }
            prefetchNext()
        }
    }

    /** Records [ref] as what a media-button play press should bring back. Shorts are excluded for
     *  the same reason they never resume a position. Page URL and title only. */
    private fun rememberForResumption(ref: VideoRef) {
        if (ref.isShort) return
        scope.launch { saveLastPlayed(ref.pageUrl, ref.title) }
    }

    /** Called by [PlaybackService] when a headset/Bluetooth play press arrives with nothing loaded
     *  (process or service restarted): replays the last remembered item through the normal path.
     *  A no-op while the session already holds a queue -- the press then just resumes that. */
    fun resumeLastPlayed() {
        ensureInit()
        scope.launch {
            if (queue.isNotEmpty()) return@launch
            val last = loadLastPlayed() ?: return@launch
            if (queue.isNotEmpty()) return@launch // something started during the lookup
            diag("resumption from last played")
            play(listOf(last), 0)
        }
    }

    /** One item's resolve + format pick. On failure, writes the error to state only if [i] is
     *  still the item actually being loaded — a fast skip during resolve must not clobber a newer
     *  error (or success) with a stale one. */
    private suspend fun resolveItem(i: Int, ref: VideoRef): PreparedItem? {
        val resolveBeganAtMs = SystemClock.elapsedRealtime()
        val resolved = try {
            resolver.resolve(ref)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: ExtractionError) {
            diag("resolve failed index=$i error=${e.javaClass.simpleName} ms=${msSince(resolveBeganAtMs)}")
            failItem(i, ref, e)
            return null
        } catch (e: Exception) {
            // A resolver tier bug (parser crash, bad JSON) must become an error screen: nothing
            // above this launches with a handler, so an escape here would kill the process.
            // Class name only -- the message can embed a URL.
            diag("resolve failed index=$i error=${e.javaClass.simpleName} ms=${msSince(resolveBeganAtMs)}")
            failItem(i, ref, ExtractionError.Unsupported("resolver failed: ${e.javaClass.simpleName}"))
            return null
        }
        diag("resolve ok index=$i ms=${msSince(resolveBeganAtMs)}")
        val result = FormatSelector.select(resolved.formats, maxHeight())
        val selection = result.selection
        if (selection == null) {
            // Shape only -- protocol/codecs/height per format, never a URL -- enough to see why
            // a non-YouTube resolve produced nothing playable.
            android.util.Log.d(
                "PlaybackSession",
                "no selection: ${result.reason}; formats=" + resolved.formats.joinToString { f ->
                    "${f.protocol}/${f.container}/${f.videoCodec}+${f.audioCodec}/${f.height}"
                },
            )
            diag("resolve failed index=$i error=NoPlayableFormat")
            failItem(i, ref, ExtractionError.Unsupported(result.reason ?: "no playable format"))
            return null
        }
        val height = when (selection) {
            is FormatSelection.Single -> selection.format.height
            is FormatSelection.Paired -> selection.video.height
        }
        return PreparedItem(i, resolved, selection, height)
    }

    /** Puts [error] on screen for [i], but only if [i] is still the item being loaded. */
    private fun failItem(i: Int, ref: VideoRef, error: ExtractionError) {
        if (i != index) return
        // the previous item must not keep playing (or auto-advance) under the error guardrail.
        // Kill the ticker NOW: its cancel via onIsPlayingChanged is a posted event, and one
        // stray tick after clearMediaItems() would overwrite positionMs with 0, losing the
        // resume point retryCurrent() reads back.
        tickerJob?.cancel()
        player.stop()
        player.clearMediaItems()
        loadedRef = null
        prepared = null
        currentFormats = emptyList()
        currentCaptions = emptyList()
        clearSponsorSegments()
        _state.update {
            it.copy(
                current = ref, firstFrameRendered = false, index = i, queueSize = queue.size, queue = queue,
                error = error, availableHeights = emptyList(), availableCaptions = emptyList(),
            )
        }
    }

    private var warmJob: Job? = null

    /** Resolve-only warm-up of the item after [i], started in PARALLEL with [i]'s own resolve.
     *  Nothing touches the player: [prefetchNext] (which runs after [i] loads) re-resolves the
     *  same ref and hits the resolver's cache instantly. Without this a swipe that lands before
     *  the previous prefetch finished, or a fling (playAt), paid two full resolves back to back. */
    private fun warmNext(i: Int) {
        warmJob?.cancel()
        val n = QueueMath.nextIndex(i, queue.size, _state.value.repeatMode, order) ?: return
        val ref = queue.getOrNull(n)?.takeIf { n != i } ?: return
        warmJob = scope.launch { runCatching { resolver.resolve(ref) } }
    }

    /** Resolves exactly one item ahead and appends it to the player's own timeline. Never more —
     *  see the class doc. Silent on failure: the real advance re-resolves for real. */
    private fun prefetchNext() {
        prefetchJob?.cancel()
        val n = QueueMath.nextIndex(index, queue.size, _state.value.repeatMode, order)
        // Every queue edit ends here, so this is the one place that catches a slot already loaded
        // for an item that is no longer next (e.g. an append under repeat-all changed what follows).
        if (window.size > 1 && window[1] != n) dropPrefetchedWindowSlot()
        if (n == null || n == index) return
        val ref = queue.getOrNull(n) ?: return
        val startedAt = index
        prefetchJob = scope.launch {
            val item = resolveItem(n, ref) ?: return@launch
            // the queue may have moved on while this was resolving
            if (index != startedAt || window.size != 1 || window[0] != index) return@launch
            prepared = item
            window = window + n
            player.addMediaSource(MediaItemFactory.create(item.selection, ref, item.resolved.captions))
        }
    }

    /** Swaps state onto an item whose format is already resolved — an auto-advance or a fast skip
     *  — so the UI gets height/queue info immediately instead of a blank beat.
     *
     *  [resetFirstFrame]: false on ExoPlayer's own auto-advance -- there the next period's
     *  onRenderedFirstFrame can be delivered BEFORE onMediaItemTransition (seen live: shorts
     *  page stuck on its thumbnail), so a reset here would never be cleared. The gap is ~0 on
     *  auto-advance anyway (next window already buffered). Manual skips seek AFTER this, so
     *  their first frame always comes later and the reset is safe. */
    private fun adoptPrepared(item: PreparedItem, resetFirstFrame: Boolean = true) {
        retriedIndex = null
        resetPlayerErrorBudget()
        autoplayFired = false // a genuinely new item is starting -- re-arm the end-of-queue check
        val ref = queue.getOrNull(item.queueIndex) ?: return
        loadedRef = ref
        rememberForResumption(ref)
        currentFormats = item.resolved.formats
        currentCaptions = item.resolved.captions
        clearSponsorSegments() // different item, same as startAt -- old item's segments must not carry over
        fetchSponsorSegments(item.queueIndex, ref)
        // A different item, same as startAt -- captions reset to Off, never carried over.
        val language = carryOverCaptionSelection(_state.value.selectedCaptionLanguage, isSameItem = false)
        applyCaptionSelection(language)
        _state.update {
            it.copy(
                current = ref, firstFrameRendered = if (resetFirstFrame) false else it.firstFrameRendered, index = item.queueIndex, queueSize = queue.size, queue = queue, error = null,
                selectedHeight = item.height, availableHeights = availableHeightsOf(item.resolved.formats),
                availableCaptions = item.resolved.captions, selectedCaptionLanguage = language,
                // seed from the player: an advance between two already-playing items never fires
                // onIsPlayingChanged, so a value left at the previous default would stick.
                isPlaying = player.isPlaying,
                // Same race as firstFrameRendered above: on an auto-advance the new item's size
                // event can already have landed, so only a manual skip may clear it.
                videoWidth = if (resetFirstFrame) 0 else it.videoWidth,
                videoHeight = if (resetFirstFrame) 0 else it.videoHeight,
            )
        }
        prepared = null
    }

    /** Drops already-played window entries so the current item is timeline position 0 again. */
    private fun trimConsumedWindow() {
        while (player.currentMediaItemIndex > 0 && player.mediaItemCount > 1) {
            player.removeMediaItem(0)
            window = window.drop(1)
        }
    }

    /** Removes an already-loaded prefetch slot from both our own bookkeeping and the player's
     *  timeline, e.g. because the queue, repeat mode or shuffle order changed underneath it. */
    private fun dropPrefetchedWindowSlot() {
        prepared = null
        if (window.size > 1) {
            player.removeMediaItem(1)
            window = window.dropLast(1)
        }
    }

    private fun tickPosition(isPlaying: Boolean) {
        tickerJob?.cancel()
        if (!isPlaying) {
            // pause/stop: capture the resume point now -- the 5s cadence below may be behind.
            // isPlaying also drops on every rebuffer; that is not a stop and would write the DB
            // each time the network dips.
            if (shouldPersistOnStop(player.playWhenReady, player.playbackState)) persistPosition()
            return
        }
        tickerJob = scope.launch {
            var ticks = 0
            var healthyPlayingTicks = 0
            while (isActive) {
                val position = player.currentPosition.coerceAtLeast(0)
                _progress.value = PlaybackProgress(position, player.duration.coerceAtLeast(0))
                if (player.isPlaying) {
                    applySponsorPolicy(position)
                    // A long video can blip more than once; after a sustained healthy stretch the
                    // one-retry budget is spent on a NEW failure, not the one already recovered from.
                    if (++healthyPlayingTicks == HEALTHY_TICKS_TO_REARM_RETRY) {
                        retriedIndex = null
                        resetPlayerErrorBudget()
                    }
                } else {
                    healthyPlayingTicks = 0
                }
                if (++ticks % 10 == 0) persistPosition() // every ~5s while playing
                delay(500)
            }
        }
    }

    /** Writes the current position through [savePosition]. Guards make it safe to call from any
     *  playback event: shorts are skipped, and a cleared/errored player (position 0, duration
     *  unset) writes nothing -- so a stop after an error can't wipe a real saved position. */
    private fun persistPosition() {
        // loadedRef, not state.current: a skip publishes current before the player holds it.
        val ref = loadedRef ?: return
        if (ref.isShort) return
        val duration = player.duration
        val position = player.currentPosition
        if (duration <= 0 || position <= 0) return
        scope.launch { savePosition(ref.pageUrl, position, duration) }
    }

    /** Cancels any in-flight fetch and drops whatever segments were held -- called at every point
     *  playback moves off the item they were fetched for, or stops outright. */
    private fun clearSponsorSegments() {
        sponsorFetchJob?.cancel()
        sponsorFetchJob = null
        sponsorSegments = emptyList()
        lastSkippedSegmentStart = null
        _sponsorMarkers.value = emptyList()
        _sponsorOffer.value = null
    }

    /** Fires a SponsorBlock lookup for the item now at [i] -- gated by the master switch, by at
     *  least one category being on, by the channel whitelist, and by YouTube page URLs. The
     *  response is applied only if [index] still points at [i] when it lands, so a slow reply
     *  can't skip in whatever plays next. */
    private fun fetchSponsorSegments(i: Int, ref: VideoRef) {
        val policy = sponsorPolicy()
        if (!policy.enabled || !policy.anyCategoryActive || ref.sourceId != "youtube") return
        if (policy.isChannelWhitelisted(ref.uploaderUrl)) return
        val videoId = youtubeVideoId(ref.pageUrl) ?: return
        sponsorFetchJob = scope.launch {
            val segments = SponsorBlock.fetchSponsorSegments(videoId)
            if (index == i) {
                sponsorSegments = segments
                applySponsorPolicy(player.currentPosition)
            }
        }
    }

    /** Runs every tick while playing: re-reads the policy (so a mode or whitelist change applies
     *  to the video already playing), publishes markers and the skip offer, and performs an
     *  automatic skip once per segment -- lastSkippedSegmentStart guards against re-seeking every
     *  tick while sitting inside (or just past) the segment that was skipped. */
    private fun applySponsorPolicy(positionMs: Long) {
        if (sponsorSegments.isEmpty()) return
        val policy = sponsorPolicy()
        val usable = usableSponsorSegments(sponsorSegments, policy, queue.getOrNull(index)?.uploaderUrl)
        _sponsorMarkers.value = usable
        lastSkippedSegmentStart = sponsorSkipGuardAfter(lastSkippedSegmentStart, positionMs)
        val decision = decideSponsorAction(positionMs, usable, policy, lastSkippedSegmentStart)
        _sponsorOffer.value = decision.offer
        val skipped = decision.skip ?: return
        lastSkippedSegmentStart = skipped.startMs
        player.seekTo(skipped.endMs)
    }

    /** The skip button: jumps to the end of the show-button segment playback is inside. */
    fun skipSponsorSegment() {
        ensureInit()
        val offer = _sponsorOffer.value ?: return
        lastSkippedSegmentStart = offer.startMs
        seekTo(offer.endMs)
        _sponsorOffer.value = null
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && resumePressedAtMs != 0L) {
                diag("resumeAudible afterPressMs=${msSince(resumePressedAtMs)}")
                resumePressedAtMs = 0L
            }
            _state.update { it.copy(isPlaying = isPlaying) }
            tickPosition(isPlaying)
        }

        override fun onRenderedFirstFrame() {
            _state.update { it.copy(firstFrameRendered = true) }
            if (awaitingFirstFrameLog) {
                awaitingFirstFrameLog = false
                diag("firstFrame afterLoadStartMs=${msSince(loadBeganAtMs)}")
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                pausedAtMs = 0L
            } else if (pausedAtMs == 0L) {
                pausedAtMs = SystemClock.elapsedRealtime()
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _state.update { it.copy(videoWidth = videoSize.width, videoHeight = videoSize.height) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && awaitingReadyLog) {
                awaitingReadyLog = false
                diag("ready afterLoadStartMs=${msSince(loadBeganAtMs)}")
            }
            _state.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    ended = playbackState == Player.STATE_ENDED,
                )
            }
            if (playbackState == Player.STATE_ENDED) {
                // watched to the end: position==duration rides through savePosition, whose owner
                // clears the row (a finished video must not grow a stale resume bar)
                persistPosition()
                // Reached the true end of the player's own (<=2-item) timeline with nothing to
                // auto-advance into — happens when repeat/shuffle changed after the last prefetch.
                val target = QueueMath.nextIndex(index, queue.size, _state.value.repeatMode, order)
                if (target != null && target != index) {
                    index = target; startAt(target)
                } else if (!autoplayFired) {
                    // Queue is genuinely exhausted. STATE_ENDED can re-emit before the async
                    // lookup below returns, so the latch is set synchronously, not after it lands.
                    autoplayFired = true
                    val ref = queue.getOrNull(index)
                    if (ref != null) startAutoplay(ref, endedIndex = index)
                }
            }
        }

        /** Auto-advance into the prefetched slot: move our own index with it, re-point state, and
         *  refill the window. A manual skip goes through [skipNext] and never lands here. */
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
            val target = QueueMath.nextIndex(index, queue.size, _state.value.repeatMode, order) ?: return
            index = target
            val readyItem = prepared?.takeIf { it.queueIndex == target }
            if (readyItem != null) adoptPrepared(readyItem, resetFirstFrame = false) else publishQueueState()
            _state.update { it.copy(autoAdvances = it.autoAdvances + 1) }
            trimConsumedWindow()
            prefetchNext()
        }

        /** Asks [classifyPlayerError] what to do and carries it out; each recovery is bounded by
         *  the budget it reads (see PlayerErrorPolicy), so a failing item always ends on the
         *  error screen. A prefetched signed URL can age out before the player reaches it, the
         *  network can drop under it (WiFi->LTE), a live window can fall behind, the shared
         *  surface can be released under the codec, or a device decoder can refuse a rendition. */
        override fun onPlayerError(error: PlaybackException) {
            val facts = PlayerErrorFacts(
                errorCode = error.errorCode,
                httpStatus = httpResponseCodeOf(error),
                isTransportFailure = isNetworkCause(error),
                isSurfaceReleased = isSurfaceReleasedFailure(error),
            )
            val lowerCeiling = lowerRenditionCeiling(_state.value.availableHeights, _state.value.selectedHeight)
            val now = SystemClock.elapsedRealtime()
            val budget = PlayerErrorBudget(
                reResolveSpent = retriedIndex == index,
                defaultPositionSeeksSpent = defaultPositionSeeks,
                lowerRenditionSpent = lowerRenditionSpent,
                lowerRenditionAvailable = lowerCeiling != null && currentFormats.isNotEmpty(),
                msSinceSurfaceRecovery = lastSurfaceRecoveryAtMs?.let { now - it },
            )
            val action = classifyPlayerError(facts, budget)
            // Codes and class names only, never the exception's message -- it can embed the dead URL.
            diag(
                "playerError code=${error.errorCode} name=${error.errorCodeName} " +
                    "http=${facts.httpStatus} network=${facts.isTransportFailure} " +
                    "surface=${facts.isSurfaceReleased} cause=${error.cause?.javaClass?.simpleName} " +
                    "index=$index action=$action",
            )
            when (action) {
                PlayerErrorAction.SEEK_TO_DEFAULT_POSITION -> {
                    defaultPositionSeeks++
                    player.seekToDefaultPosition()
                    player.prepare()
                }
                PlayerErrorAction.REPREPARE_AFTER_SURFACE_LOSS -> {
                    lastSurfaceRecoveryAtMs = now
                    // Same source, same position: prepare() after an error resumes from where the
                    // player stopped. No re-resolve -- the URL is fine, the surface was not.
                    player.prepare()
                }
                PlayerErrorAction.RERESOLVE_EXPIRED_URL, PlayerErrorAction.RERESOLVE_AFTER_TRANSPORT_FAILURE -> {
                    retriedIndex = index
                    val ref = queue.getOrNull(index)
                    // Only a dead URL invalidates the resolver: after a transport blip the cached
                    // formats are still good and re-using them is the fast path.
                    if (action == PlayerErrorAction.RERESOLVE_EXPIRED_URL && ref != null) resolver.invalidate(ref.pageUrl)
                    startAt(index, resumeAtMs = player.currentPosition)
                }
                PlayerErrorAction.FALL_BACK_TO_LOWER_RENDITION -> {
                    lowerRenditionSpent = true
                    selectQuality(lowerCeiling)
                }
                PlayerErrorAction.SHOW_ERROR -> {
                    // Only genuine transport trouble may claim "no connection": a googlevideo 403
                    // on a fresh URL (seen live) is the platform refusing this client, not the
                    // user's network.
                    val mapped = if (facts.isTransportFailure) {
                        ExtractionError.Network("playback error ${error.errorCode}")
                    } else {
                        ExtractionError.Unsupported("playback error ${error.errorCode}")
                    }
                    _state.update { it.copy(error = mapped) }
                }
            }
        }
    }

}

/** A signed URL that aged out comes back as one of these; re-resolve rather than retry it.
 *  internal, not private: MediaItemFactory's no-retry LoadErrorHandlingPolicy checks the same set. */
internal val EXPIRED_HTTP_CODES = setOf(401, 403, 410)

/** True only for transport-level causes (no route, DNS, timeout) — the cases where "check your
 *  network" is honest advice. An HTTP status is proof the network worked. */
internal fun isNetworkCause(error: Throwable?): Boolean {
    var cause: Throwable? = error
    var depth = 0
    while (cause != null && depth++ < 8) {
        when (cause) {
            is java.net.SocketTimeoutException, is java.net.UnknownHostException,
            is java.net.ConnectException, is javax.net.ssl.SSLException,
            -> return true
        }
        if (cause is HttpDataSource.InvalidResponseCodeException) return false
        cause = cause.cause.takeIf { it !== cause }
    }
    return false
}

/** Walks the cause chain for the HTTP codes that mean "this signed URL is dead", per Contracts's
 *  [com.fyiplayer.app.core.ExtractionError.Expired]. Never logs the message: it can carry the dead
 *  URL. Shared by PlaybackSession's onPlayerError and MediaItemFactory's fail-fast retry policy. */
internal fun isExpiredHttpError(error: Throwable?): Boolean =
    httpResponseCodeOf(error) in EXPIRED_HTTP_CODES

/** The HTTP status carried by the first [HttpDataSource.InvalidResponseCodeException] in the cause
 *  chain, or null when the failure was not an HTTP status. A code only, never the message. */
internal fun httpResponseCodeOf(error: Throwable?): Int? {
    var cause: Throwable? = error
    var depth = 0
    while (cause != null && depth++ < 8) {
        if (cause is HttpDataSource.InvalidResponseCodeException) return cause.responseCode
        cause = cause.cause.takeIf { it !== cause }
    }
    return null
}

/** Whether [Player.Listener.onIsPlayingChanged] going false is a real stop (pause, end, idle)
 *  worth a position write. A rebuffer -- wanting to play, waiting for data -- is not. */
internal fun shouldPersistOnStop(playWhenReady: Boolean, playbackState: Int): Boolean =
    !(playWhenReady && playbackState == Player.STATE_BUFFERING)

/** The sponsor-skip guard after observing [positionMs]: dropped once playback is back before the
 *  segment it last skipped (loop restart, seek back), so that segment is skipped again on the next
 *  pass. While at or past the segment start the guard stays, so the tick right after a skip -- which
 *  can still read a position inside the segment -- does not re-seek. */
internal fun sponsorSkipGuardAfter(lastSkippedSegmentStart: Long?, positionMs: Long): Long? =
    lastSkippedSegmentStart?.takeIf { positionMs >= it }

/** What a caption pick becomes across a MediaSource rebuild: kept for [isSameItem] (same video,
 *  different rendition -- [PlaybackSession.selectQuality]), reset to Off for every other reload
 *  (a genuinely different item, which always starts captions at Off). Pure and player-free so this
 *  policy is unit-testable without ExoPlayer. */
internal fun carryOverCaptionSelection(previous: String?, isSameItem: Boolean): String? =
    previous.takeIf { isSameItem }
