package com.fyiplayer.app.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.Resolved
import com.fyiplayer.app.core.StreamResolver
import com.fyiplayer.app.core.VideoRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Immutable snapshot for the UI. Deliberately carries no media URL — Contracts.kt's rule. */
data class PlayerState(
    val current: VideoRef? = null,
    val index: Int = -1,
    val queueSize: Int = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: ExtractionError? = null,
    val selectedHeight: Int? = null,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffled: Boolean = false,
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

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    val exoPlayer: ExoPlayer get() = player

    private var queue: List<VideoRef> = emptyList()
    private var order: List<Int>? = null // shuffle order; null = queue order
    private var index: Int = -1

    // player timeline position -> queue index, at most 2 entries: [current] or [current, prefetched]
    private var window: List<Int> = emptyList()
    private var prepared: PreparedItem? = null // resolved-ahead item, adopted without a re-resolve
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
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        player = ExoPlayer.Builder(context.applicationContext).build().apply {
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
        }
    }

    private fun ensureInit() = check(::player.isInitialized) { "PlaybackSession.init() was not called" }

    fun play(refs: List<VideoRef>, startIndex: Int) {
        ensureInit()
        queue = refs
        order = null
        index = QueueMath.clamp(startIndex, refs.size)
        prepared = null
        retriedIndex = null
        // a fresh state must seed isPlaying from the player: onIsPlayingChanged only fires on a
        // change, and skipping between two already-playing items would otherwise never fire it.
        _state.value = PlayerState(index = index, queueSize = queue.size, isPlaying = player.isPlaying)
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

    fun seekTo(positionMs: Long) {
        ensureInit()
        player.seekTo(positionMs)
    }

    fun togglePlayPause() {
        ensureInit()
        player.playWhenReady = !player.playWhenReady
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
        player.stop()
        player.clearMediaItems()
        _state.value = PlayerState()
    }

    fun release() {
        loadJob?.cancel(); prefetchJob?.cancel(); tickerJob?.cancel()
        if (::player.isInitialized) player.release()
    }

    private fun publishQueueState() {
        _state.update { it.copy(index = index, queueSize = queue.size, current = queue.getOrNull(index)) }
    }

    /** Resolve [i] and load it as the only window item, then prefetch the one after it. */
    private fun startAt(i: Int) {
        loadJob?.cancel()
        prefetchJob?.cancel()
        val ref = queue.getOrNull(i) ?: return
        window = emptyList()
        loadJob = scope.launch {
            val item = resolveItem(i, ref) ?: return@launch
            player.setMediaSource(MediaItemFactory.create(item.selection))
            player.prepare()
            player.playWhenReady = true
            retriedIndex = null
            window = listOf(i)
            _state.update {
                it.copy(
                    current = ref, index = i, queueSize = queue.size, error = null,
                    selectedHeight = item.height, isPlaying = player.isPlaying,
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
                _state.update { it.copy(current = ref, index = i, queueSize = queue.size, error = e) }
            }
            return null
        }
        val result = FormatSelector.select(resolved.formats, maxHeight())
        val selection = result.selection
        if (selection == null) {
            if (i == index) {
                _state.update {
                    it.copy(
                        current = ref, index = i, queueSize = queue.size,
                        error = ExtractionError.Unsupported(result.reason ?: "no playable format"),
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
            player.addMediaSource(MediaItemFactory.create(item.selection))
        }
    }

    /** Swaps state onto an item whose format is already resolved — an auto-advance or a fast skip
     *  — so the UI gets height/queue info immediately instead of a blank beat. */
    private fun adoptPrepared(item: PreparedItem) {
        retriedIndex = null
        val ref = queue.getOrNull(item.queueIndex) ?: return
        _state.update {
            it.copy(
                current = ref, index = item.queueIndex, queueSize = queue.size, error = null,
                selectedHeight = item.height,
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
