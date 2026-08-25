package com.fyiplayer.app.player

import android.content.Context
import android.content.Intent
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
import com.fyiplayer.app.core.CaptionTrack
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.Resolved
import com.fyiplayer.app.core.StreamResolver
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.prefs.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

    // Mirror of Prefs.sponsorBlock, same pattern as maxHeight: format selection/skip checks run
    // on the player thread and cannot suspend on a DataStore read.
    private var sponsorBlockEnabled: () -> Boolean = { false }
    // Injected by FyiApp; returns null when the pref is off, the search fails, or nothing
    // qualifies -- STATE_ENDED's handler treats null as "nothing to autoplay", same as no queue.
    private var autoplayNext: suspend (VideoRef) -> VideoRef? = { null }
    // Position persistence seams (FyiApp gates on the pref and owns the near-end-clears rule);
    // the session only decides WHEN: resume lookup at item start, save on tick/pause/end.
    private var loadPosition: suspend (String) -> Long? = { null }
    private var savePosition: suspend (String, Long, Long) -> Unit = { _, _, _ -> }
    private var sponsorFetchJob: Job? = null
    // Segments for the item currently at `index`. Never trusted after an item change until
    // fetchSponsorSegments's own index check confirms the response is still for the right item.
    private var sponsorSegments: List<SponsorSegment> = emptyList()
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
    // When the current item was last resolved -- togglePlayPause checks this against isStale
    // before resuming, so a long-paused signed URL gets refreshed instead of hitting the player dead.
    private var currentResolvedAtMillis: Long = 0
    private var loadJob: Job? = null
    private var prefetchJob: Job? = null
    private var tickerJob: Job? = null
    private var retriedIndex: Int? = null // one re-resolve attempt per item on an expired URL
    private var autoplayFired = false // guards STATE_ENDED's possible re-emission from double-firing autoplay

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
        sponsorBlockEnabled: () -> Boolean = { false },
        autoplayNext: suspend (VideoRef) -> VideoRef? = { null },
        loadPosition: suspend (String) -> Long? = { null },
        savePosition: suspend (String, Long, Long) -> Unit = { _, _, _ -> },
    ) {
        if (::player.isInitialized) return
        this.resolver = resolver
        this.maxHeight = maxHeight
        this.sponsorBlockEnabled = sponsorBlockEnabled
        this.autoplayNext = autoplayNext
        this.loadPosition = loadPosition
        this.savePosition = savePosition
        appContext = context.applicationContext
        MediaItemFactory.init(appContext)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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
        player.stop()
        player.clearMediaItems()
        // PlaybackService.onGetSession just hands back the session over this same player, so
        // starting it here (idempotent if already running) is enough for lockscreen/Bluetooth
        // controls and the notification to exist for the rest of this queue's lifetime.
        // Plain startService, NOT startForegroundService: the latter arms the OS's
        // must-call-startForeground timer, but media3 only promotes the service to foreground
        // once a session is actually engaged (playWhenReady + READY/BUFFERING) — if resolution
        // is still running when the timer fires, the system kills the whole app
        // (ForegroundServiceDidNotStartInTimeException, seen on device). play() is always
        // called with the app in the foreground, so startService is permitted.
        appContext.startService(Intent(appContext, PlaybackService::class.java))
        queue = refs
        order = null
        index = QueueMath.clamp(startIndex, refs.size)
        prepared = null
        retriedIndex = null
        autoplayFired = false
        currentFormats = emptyList()
        currentCaptions = emptyList()
        currentResolvedAtMillis = 0
        // a fresh state must seed isPlaying from the player: onIsPlayingChanged only fires on a
        // change, and skipping between two already-playing items would otherwise never fire it.
        // speed is seeded too: it's a player-level setting that survives across queues.
        _state.value = PlayerState(
            index = index, queueSize = queue.size, queue = queue,
            isPlaying = player.isPlaying, speed = player.playbackParameters.speed,
        )
        _progress.value = PlaybackProgress()
        startAt(index)
    }

    /** Inserts [ref] to play right after the current item. */
    fun playNext(ref: VideoRef) {
        ensureInit()
        if (queue.isEmpty()) { play(listOf(ref), 0); return }
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

    /** Appends [ref] to the end of the queue. */
    fun enqueue(ref: VideoRef) {
        ensureInit()
        if (queue.isEmpty()) { play(listOf(ref), 0); return }
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
    }

    /** Advance one item. Reuses the prefetched slot when it's still the right one, so the common
     *  case costs no re-resolve and no network wait. */
    fun skipNext() {
        ensureInit()
        val target = QueueMath.nextIndex(index, queue.size, _state.value.repeatMode, order) ?: return
        val item = prepared
        if (window.size > 1 && window[1] == target && item != null && item.queueIndex == target) {
            index = target
            adoptPrepared(item)
            player.seekTo(1, 0L)
            trimConsumedWindow()
            player.play()
            prefetchNext()
            return
        }
        index = target
        startAt(target)
    }

    /** Back one item. Always re-resolves: only the current item and the one ahead of it are ever
     *  kept live, so the previous item's signed URL is long gone. */
    fun skipPrevious() {
        ensureInit()
        val target = QueueMath.previousIndex(index, queue.size, _state.value.repeatMode, order) ?: return
        index = target
        startAt(target)
    }

    /** Jump to an arbitrary queue position — what tapping a row in the queue list does. Always
     *  re-resolves: only the current item and the one ahead of it ever hold a live signed URL. */
    fun playAt(i: Int) {
        ensureInit()
        if (i !in queue.indices || i == index) return
        index = i
        prepared = null
        retriedIndex = null
        // Publish `current` NOW, not after the async resolve in startAt: the queue sheet opens the
        // target's watch page right after this, and DetailScreen's entry guard replaces the whole
        // queue with a single-item one whenever state.current is not the page's video.
        publishQueueState()
        startAt(i)
    }

    /** Reorders the queue: moves the item at [from] to [to]. Keeps [index] pointing at the same
     *  item it did before the move, and drops the prefetch window when the move could have
     *  touched it. */
    fun move(from: Int, to: Int) {
        ensureInit()
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val affectsWindow = from >= index || to >= index
        val item = queue[from]
        queue = queue.toMutableList().apply { removeAt(from); add(to, item) }
        order = order?.let { QueueMath.moveOrder(it, from, to) }
        index = when {
            from == index -> to
            from < index && to >= index -> index - 1
            from > index && to <= index -> index + 1
            else -> index
        }
        if (affectsWindow) dropPrefetchedWindowSlot()
        publishQueueState()
        if (affectsWindow) prefetchNext()
    }

    fun seekTo(positionMs: Long) {
        ensureInit()
        player.seekTo(positionMs)
    }

    fun togglePlayPause() {
        ensureInit()
        val ref = queue.getOrNull(index)
        // Error state, or a source the player itself gave up on (e.g. a killed surface/source
        // after sitting backgrounded a couple of minutes -- flipping playWhenReady on a dead
        // source does nothing visible): recover instead of toggling a player with nothing to play.
        if (ref != null && (_state.value.error != null || player.playbackState == Player.STATE_IDLE)) {
            retryCurrent()
            return
        }
        // Resuming into a stream that's been resolved long enough its signed URL may already be
        // dead: refresh proactively (position-preserving) instead of letting the player discover
        // that itself and eat the default retry backoff before onPlayerError fires.
        if (!player.playWhenReady && ref != null && isStale(currentResolvedAtMillis)) {
            resolver.invalidate(ref.pageUrl)
            startAt(index, resumeAtMs = player.currentPosition)
            return
        }
        player.playWhenReady = !player.playWhenReady
    }

    /** Recovers the current item: re-resolves and re-prepares in place, same machinery as the
     *  expired-URL path above and [onPlayerError]'s auto re-resolve, just triggered manually --
     *  the Retry action on an error state, or [togglePlayPause] finding the player dead. Position
     *  is read from [progress] rather than the player: a failed resolve already cleared the
     *  player's own source (see [resolveItem]'s catch blocks), so the player's own
     *  [ExoPlayer.getCurrentPosition] can no longer be trusted for where playback actually was --
     *  [tickPosition] stops writing [progress] the moment the error path cancels [tickerJob], so
     *  its last value is exactly the frozen resume point. */
    fun retryCurrent() {
        ensureInit()
        val ref = queue.getOrNull(index) ?: return
        val resumeAt = _progress.value.positionMs
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
        prepared = null
        window = listOf(index)
        player.setMediaSource(MediaItemFactory.create(selection, queue.getOrNull(index), currentCaptions))
        player.prepare()
        player.seekTo(resumeAt)
        player.playWhenReady = true
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
        val affectsWindow = i >= index // current or anything after it can desync the prefetch slot
        queue = queue.toMutableList().apply { removeAt(i) }
        if (queue.isEmpty()) { clear(); return }
        order = order?.let { QueueMath.removeOrder(it, i) }
        if (affectsWindow) dropPrefetchedWindowSlot()
        when {
            i < index -> index -= 1
            wasCurrent -> { startAt(QueueMath.clamp(index, queue.size)); return }
        }
        publishQueueState()
        if (affectsWindow) prefetchNext()
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
        loadJob?.cancel(); prefetchJob?.cancel(); tickerJob?.cancel()
        queue = emptyList(); order = null; index = -1
        window = emptyList(); prepared = null; retriedIndex = null
        currentFormats = emptyList()
        currentCaptions = emptyList()
        currentResolvedAtMillis = 0
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
     *  the expiry re-resolve paths (onPlayerError, togglePlayPause's staleness check) where this
     *  is the same item continuing, not a genuinely new one starting at 0. */
    private fun startAt(i: Int, resumeAtMs: Long? = null) {
        loadJob?.cancel()
        prefetchJob?.cancel()
        val ref = queue.getOrNull(i) ?: return
        autoplayFired = false // a genuinely new item is starting -- re-arm the end-of-queue check
        window = emptyList()
        clearSponsorSegments() // item is changing -- the old item's segments must not carry over
        warmNext(i)
        loadJob = scope.launch {
            val item = resolveItem(i, ref) ?: return@launch
            player.setMediaSource(MediaItemFactory.create(item.selection, ref, item.resolved.captions))
            player.prepare()
            // resumeAtMs given = the SAME item continuing (expiry re-resolve, retry). A genuinely
            // new start may pick up its saved resume point instead. Shorts never resume -- a
            // swipe-through clip restarting mid-way would just be confusing.
            val resume = resumeAtMs ?: if (!ref.isShort) loadPosition(ref.pageUrl) else null
            if (resume != null) player.seekTo(resume)
            player.playWhenReady = true
            // retriedIndex is deliberately NOT cleared here. This runs on the re-resolve that a
            // failed item triggered, so clearing it would re-arm the retry for the same item and
            // a host that rejects every fresh URL (403) would loop forever. It is cleared only
            // when the user genuinely moves to another item.
            window = listOf(i)
            currentFormats = item.resolved.formats
            currentCaptions = item.resolved.captions
            currentResolvedAtMillis = item.resolved.resolvedAtMillis
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
                )
            }
            prefetchNext()
        }
    }

    /** One item's resolve + format pick. On failure, writes the error to state only if [i] is
     *  still the item actually being loaded — a fast skip during resolve must not clobber a newer
     *  error (or success) with a stale one. */
    private suspend fun resolveItem(i: Int, ref: VideoRef): PreparedItem? {
        val resolved = try {
            resolver.resolve(ref)
        } catch (e: ExtractionError) {
            if (i == index) {
                // the previous item must not keep playing (or auto-advance) under the error guardrail.
                // Kill the ticker NOW: its cancel via onIsPlayingChanged is a posted event, and one
                // stray tick after clearMediaItems() would overwrite positionMs with 0, losing the
                // resume point retryCurrent() reads back.
                tickerJob?.cancel()
                player.stop()
                player.clearMediaItems()
                prepared = null
                currentFormats = emptyList()
                currentCaptions = emptyList()
                clearSponsorSegments()
                _state.update {
                    it.copy(
                        current = ref, firstFrameRendered = false, index = i, queueSize = queue.size, queue = queue,
                        error = e, availableHeights = emptyList(), availableCaptions = emptyList(),
                    )
                }
            }
            return null
        }
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
            if (i == index) {
                // the previous item must not keep playing (or auto-advance) under the error guardrail.
                // Kill the ticker NOW: its cancel via onIsPlayingChanged is a posted event, and one
                // stray tick after clearMediaItems() would overwrite positionMs with 0, losing the
                // resume point retryCurrent() reads back.
                tickerJob?.cancel()
                player.stop()
                player.clearMediaItems()
                prepared = null
                currentFormats = emptyList()
                currentCaptions = emptyList()
                clearSponsorSegments()
                _state.update {
                    it.copy(
                        current = ref, firstFrameRendered = false, index = i, queueSize = queue.size, queue = queue,
                        error = ExtractionError.Unsupported(result.reason ?: "no playable format"),
                        availableHeights = emptyList(), availableCaptions = emptyList(),
                    )
                }
            }
            return null
        }
        val height = when (selection) {
            is FormatSelection.Single -> selection.format.height
            is FormatSelection.Paired -> selection.video.height
        }
        return PreparedItem(i, resolved, selection, height)
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
        val n = QueueMath.nextIndex(index, queue.size, _state.value.repeatMode, order) ?: return
        if (n == index) return
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
     *  — so the UI gets height/queue info immediately instead of a blank beat. */
    /** [resetFirstFrame]: false on ExoPlayer's own auto-advance -- there the next period's
     *  onRenderedFirstFrame can be delivered BEFORE onMediaItemTransition (seen live: shorts
     *  page stuck on its thumbnail), so a reset here would never be cleared. The gap is ~0 on
     *  auto-advance anyway (next window already buffered). Manual skips seek AFTER this, so
     *  their first frame always comes later and the reset is safe. */
    private fun adoptPrepared(item: PreparedItem, resetFirstFrame: Boolean = true) {
        retriedIndex = null
        autoplayFired = false // a genuinely new item is starting -- re-arm the end-of-queue check
        val ref = queue.getOrNull(item.queueIndex) ?: return
        currentFormats = item.resolved.formats
        currentCaptions = item.resolved.captions
        currentResolvedAtMillis = item.resolved.resolvedAtMillis
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
            // pause/stop: capture the resume point now -- the 5s cadence below may be behind
            persistPosition()
            return
        }
        tickerJob = scope.launch {
            var ticks = 0
            while (isActive) {
                val position = player.currentPosition.coerceAtLeast(0)
                _progress.value = PlaybackProgress(position, player.duration.coerceAtLeast(0))
                if (player.isPlaying) checkSponsorSkip(position)
                if (++ticks % 10 == 0) persistPosition() // every ~5s while playing
                delay(500)
            }
        }
    }

    /** Writes the current position through [savePosition]. Guards make it safe to call from any
     *  playback event: shorts are skipped, and a cleared/errored player (position 0, duration
     *  unset) writes nothing -- so a stop after an error can't wipe a real saved position. */
    private fun persistPosition() {
        val ref = _state.value.current ?: return
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
    }

    /** Fires a SponsorBlock lookup for the item now at [i] -- gated by the mirrored pref and only
     *  for YouTube page URLs. The response is applied only if [index] still points at [i] when it
     *  lands, so a slow reply can't skip in whatever plays next. */
    private fun fetchSponsorSegments(i: Int, ref: VideoRef) {
        if (!sponsorBlockEnabled() || ref.sourceId != "youtube") return
        val videoId = youtubeVideoId(ref.pageUrl) ?: return
        sponsorFetchJob = scope.launch {
            val segments = SponsorBlock.fetchSponsorSegments(videoId)
            if (index == i) sponsorSegments = segments
        }
    }

    /** Seeks past a sponsor segment [positionMs] just entered, once per segment -- lastSkippedSegmentStart
     *  guards against re-seeking every tick while sitting inside (or just past) the same segment. */
    private fun checkSponsorSkip(positionMs: Long) {
        val segment = sponsorSegments.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs } ?: return
        if (lastSkippedSegmentStart == segment.startMs) return
        lastSkippedSegmentStart = segment.startMs
        player.seekTo(segment.endMs)
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            tickPosition(isPlaying)
        }

        override fun onRenderedFirstFrame() {
            _state.update { it.copy(firstFrameRendered = true) }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _state.update { it.copy(videoWidth = videoSize.width, videoHeight = videoSize.height) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
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
                    if (ref != null) scope.launch {
                        val next = autoplayNext(ref)
                        if (next != null) play(listOf(next), 0)
                    }
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
            trimConsumedWindow()
            prefetchNext()
        }

        /** A prefetched signed URL can age out before the player reaches it, or the network can
         *  drop under it (WiFi->LTE handover). Re-prepare the current item once from where it
         *  was; a second failure on the same item stops instead of looping. */
        override fun onPlayerError(error: PlaybackException) {
            val expired = isExpiredHttpError(error)
            if ((expired || isNetworkCause(error)) && retriedIndex != index) {
                retriedIndex = index
                val ref = queue.getOrNull(index)
                // Only a dead URL invalidates the resolver: after a transport blip the cached
                // formats are still good and re-using them is the fast path.
                if (expired && ref != null) resolver.invalidate(ref.pageUrl)
                startAt(index, resumeAtMs = player.currentPosition)
            } else {
                // ids/codes only, never the exception's message — it can embed the dead signed URL.
                // Only genuine transport trouble may claim "no connection": a googlevideo 403 on a
                // fresh URL (seen live) is the platform refusing this client, not the user's network.
                val mapped = if (isNetworkCause(error)) {
                    ExtractionError.Network("playback error ${error.errorCode}")
                } else {
                    ExtractionError.Unsupported("playback error ${error.errorCode}")
                }
                _state.update { it.copy(error = mapped) }
            }
        }
    }

}

/** A signed URL that aged out comes back as one of these; re-resolve rather than retry it.
 *  internal, not private: MediaItemFactory's no-retry LoadErrorHandlingPolicy checks the same set. */
internal val EXPIRED_HTTP_CODES = setOf(401, 403, 410)

/** Walks the cause chain for the HTTP codes that mean "this signed URL is dead", per Contracts's
 *  [com.fyiplayer.app.core.ExtractionError.Expired]. Never logs the message: it can carry the dead
 *  URL. Shared by PlaybackSession's onPlayerError and MediaItemFactory's fail-fast retry policy. */
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

internal fun isExpiredHttpError(error: Throwable?): Boolean {
    var cause: Throwable? = error
    while (cause != null) {
        if (cause is HttpDataSource.InvalidResponseCodeException &&
            cause.responseCode in EXPIRED_HTTP_CODES
        ) return true
        cause = cause.cause
    }
    return false
}

// Signed URLs are commonly good for ~1h; refresh with margin before the player would discover
// staleness itself and eat the default retry backoff (see MediaItemFactory's policy).
private const val STREAM_STALE_MS = 50 * 60 * 1000L

/** True once a resolve is old enough its signed URLs might already be dead. 0 means "nothing
 *  resolved yet" -- never stale. Pure so it's unit-testable without ExoPlayer. */
internal fun isStale(resolvedAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean =
    resolvedAtMillis != 0L && nowMillis - resolvedAtMillis > STREAM_STALE_MS

/** What a caption pick becomes across a MediaSource rebuild: kept for [isSameItem] (same video,
 *  different rendition -- [PlaybackSession.selectQuality]), reset to Off for every other reload
 *  (a genuinely different item, which always starts captions at Off). Pure and player-free so this
 *  policy is unit-testable without ExoPlayer. */
internal fun carryOverCaptionSelection(previous: String?, isSameItem: Boolean): String? =
    previous.takeIf { isSameItem }
