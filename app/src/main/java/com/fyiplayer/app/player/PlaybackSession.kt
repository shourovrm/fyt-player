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
    val positionMs: Long = 0,
    val durationMs: Long = 0,
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

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

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
    private var retriedIndex: Int? = null // one re-resolve attempt per item on an expired URL

    private class PreparedItem(
        val queueIndex: Int,
        val resolved: Resolved,
        val selection: FormatSelection,
        val height: Int?,
    )

    fun init(context: Context, resolver: StreamResolver, maxHeight: () -> Int = { 1080 }) {
        if (::player.isInitialized) return
        this.resolver = resolver
        this.maxHeight = maxHeight
        appContext = context.applicationContext
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
        player = ExoPlayer.Builder(appContext, renderersFactory).build().apply {
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
        currentFormats = emptyList()
        currentCaptions = emptyList()
        // a fresh state must seed isPlaying from the player: onIsPlayingChanged only fires on a
        // change, and skipping between two already-playing items would otherwise never fire it.
        // speed is seeded too: it's a player-level setting that survives across queues.
        _state.value = PlayerState(
            index = index, queueSize = queue.size, queue = queue,
            isPlaying = player.isPlaying, speed = player.playbackParameters.speed,
        )
        startAt(index)
    }

    /** Inserts [ref] to play right after the current item. */
    fun playNext(ref: VideoRef) {
        ensureInit()
        if (queue.isEmpty()) { play(listOf(ref), 0); return }
        val insertAt = index + 1
        queue = queue.toMutableList().apply { add(insertAt, ref) }
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
        queue = queue + ref
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
        player.playWhenReady = !player.playWhenReady
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
        if (affectsWindow) dropPrefetchedWindowSlot()
        when {
            i < index -> index -= 1
            wasCurrent -> { startAt(QueueMath.clamp(index, queue.size)); return }
        }
        publishQueueState()
        if (affectsWindow) prefetchNext()
    }

    fun clear() {
        ensureInit()
        loadJob?.cancel(); prefetchJob?.cancel(); tickerJob?.cancel()
        queue = emptyList(); order = null; index = -1
        window = emptyList(); prepared = null; retriedIndex = null
        currentFormats = emptyList()
        currentCaptions = emptyList()
        player.stop()
        player.clearMediaItems()
        _state.value = PlayerState()
        // nothing left to play: drop the notification/session instead of leaving a stale one up
        appContext.stopService(Intent(appContext, PlaybackService::class.java))
    }

    fun release() {
        loadJob?.cancel(); prefetchJob?.cancel(); tickerJob?.cancel()
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

    /** Resolve [i] and load it as the only window item, then prefetch the one after it. */
    private fun startAt(i: Int) {
        loadJob?.cancel()
        prefetchJob?.cancel()
        val ref = queue.getOrNull(i) ?: return
        window = emptyList()
        loadJob = scope.launch {
            val item = resolveItem(i, ref) ?: return@launch
            player.setMediaSource(MediaItemFactory.create(item.selection, ref, item.resolved.captions))
            player.prepare()
            player.playWhenReady = true
            // retriedIndex is deliberately NOT cleared here. This runs on the re-resolve that a
            // failed item triggered, so clearing it would re-arm the retry for the same item and
            // a host that rejects every fresh URL (403) would loop forever. It is cleared only
            // when the user genuinely moves to another item.
            window = listOf(i)
            currentFormats = item.resolved.formats
            currentCaptions = item.resolved.captions
            // A different item always starts captions at Off, never whatever the previous item had.
            val language = carryOverCaptionSelection(_state.value.selectedCaptionLanguage, isSameItem = false)
            applyCaptionSelection(language)
            _state.update {
                it.copy(
                    current = ref, index = i, queueSize = queue.size, queue = queue, error = null,
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
                currentFormats = emptyList()
                currentCaptions = emptyList()
                _state.update {
                    it.copy(
                        current = ref, index = i, queueSize = queue.size, queue = queue,
                        error = e, availableHeights = emptyList(), availableCaptions = emptyList(),
                    )
                }
            }
            return null
        }
        val result = FormatSelector.select(resolved.formats, maxHeight())
        val selection = result.selection
        if (selection == null) {
            if (i == index) {
                currentFormats = emptyList()
                currentCaptions = emptyList()
                _state.update {
                    it.copy(
                        current = ref, index = i, queueSize = queue.size, queue = queue,
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
    private fun adoptPrepared(item: PreparedItem) {
        retriedIndex = null
        val ref = queue.getOrNull(item.queueIndex) ?: return
        currentFormats = item.resolved.formats
        currentCaptions = item.resolved.captions
        // A different item, same as startAt -- captions reset to Off, never carried over.
        val language = carryOverCaptionSelection(_state.value.selectedCaptionLanguage, isSameItem = false)
        applyCaptionSelection(language)
        _state.update {
            it.copy(
                current = ref, index = item.queueIndex, queueSize = queue.size, queue = queue, error = null,
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
        if (!isPlaying) return
        tickerJob = scope.launch {
            while (isActive) {
                _state.update {
                    it.copy(
                        positionMs = player.currentPosition.coerceAtLeast(0),
                        durationMs = player.duration.coerceAtLeast(0),
                    )
                }
                delay(500)
            }
        }
    }

    /** Walks the cause chain for the HTTP codes that mean "this signed URL is dead", per
     *  Contracts's [ExtractionError.Expired]. Never logs the message: it can carry the dead URL. */
    private fun isExpiredHttpError(error: Throwable): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException &&
                cause.responseCode in EXPIRED_HTTP_CODES
            ) return true
            cause = cause.cause
        }
        return false
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            tickPosition(isPlaying)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _state.update { it.copy(videoWidth = videoSize.width, videoHeight = videoSize.height) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
            if (playbackState == Player.STATE_ENDED) {
                // Reached the true end of the player's own (<=2-item) timeline with nothing to
                // auto-advance into — happens when repeat/shuffle changed after the last prefetch.
                val target = QueueMath.nextIndex(index, queue.size, _state.value.repeatMode, order)
                if (target != null && target != index) { index = target; startAt(target) }
            }
        }

        /** Auto-advance into the prefetched slot: move our own index with it, re-point state, and
         *  refill the window. A manual skip goes through [skipNext] and never lands here. */
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
            val target = QueueMath.nextIndex(index, queue.size, _state.value.repeatMode, order) ?: return
            index = target
            val readyItem = prepared?.takeIf { it.queueIndex == target }
            if (readyItem != null) adoptPrepared(readyItem) else publishQueueState()
            trimConsumedWindow()
            prefetchNext()
        }

        /** A prefetched signed URL can age out before the player reaches it. Re-resolve the
         *  current item once; a second failure on the same item stops instead of looping. */
        override fun onPlayerError(error: PlaybackException) {
            if (isExpiredHttpError(error) && retriedIndex != index) {
                retriedIndex = index
                startAt(index)
            } else {
                // ids/codes only, never the exception's message — it can embed the dead signed URL
                _state.update { it.copy(error = ExtractionError.Network("playback error ${error.errorCode}")) }
            }
        }
    }

}

/** A signed URL that aged out comes back as one of these; re-resolve rather than retry it. */
private val EXPIRED_HTTP_CODES = setOf(401, 403, 410)

/** What a caption pick becomes across a MediaSource rebuild: kept for [isSameItem] (same video,
 *  different rendition -- [PlaybackSession.selectQuality]), reset to Off for every other reload
 *  (a genuinely different item, which always starts captions at Off). Pure and player-free so this
 *  policy is unit-testable without ExoPlayer. */
internal fun carryOverCaptionSelection(previous: String?, isSameItem: Boolean): String? =
    previous.takeIf { isSameItem }
