package com.fyiplayer.app.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Pauses playback after a chosen number of minutes. One timer for the whole process, shared by
 * the full player and Shorts. Held outside any screen so it keeps counting while the app is in
 * the background; the playback service keeps the process alive while something plays. Not saved:
 * a killed process has nothing playing to pause.
 */
object SleepTimer {
    private const val TICK_MS = 1_000L

    // Main thread: the player may only be touched there.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val _running = MutableStateFlow<RunningSleepTimer?>(null)

    /** The running timer, or null when none runs. */
    val running: StateFlow<RunningSleepTimer?> = _running.asStateFlow()

    /** Starts a timer of [minutes], replacing any running one. */
    fun start(minutes: Int) {
        require(minutes > 0) { "sleep timer needs a positive duration, got $minutes" }
        job?.cancel()
        val endsAtMs = System.currentTimeMillis() + minutes * 60_000L
        job = scope.launch {
            var left = endsAtMs - System.currentTimeMillis()
            while (left > 0) {
                _running.value = RunningSleepTimer(minutes, left)
                delay(minOf(TICK_MS, left))
                left = endsAtMs - System.currentTimeMillis()
            }
            _running.value = null
            PlaybackSession.exoPlayer.pause()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _running.value = null
    }
}

/** A running timer: the [minutes] it was started with and the [remainingMs] until it pauses. */
data class RunningSleepTimer(val minutes: Int, val remainingMs: Long)

/** Whole minutes left, rounded up, so the badge never reads 0 while the timer still runs. */
internal fun sleepTimerMinutesLeft(remainingMs: Long): Long = (remainingMs + 59_999L) / 60_000L

/** Remaining time as m:ss, or h:mm:ss from one hour up. */
internal fun formatSleepTimerRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs + 999L) / 1_000L
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds % 3_600 / 60
    val seconds = totalSeconds % 60
    if (hours > 0) return "%d:%02d:%02d".format(hours, minutes, seconds)
    return "%d:%02d".format(minutes, seconds)
}

/** Total minutes of a custom timer if it is from 1 minute to 12 hours, else null. */
internal fun sleepTimerTotalMinutes(hours: Int, minutes: Int): Int? = (hours * 60 + minutes).takeIf { it in 1..720 }
