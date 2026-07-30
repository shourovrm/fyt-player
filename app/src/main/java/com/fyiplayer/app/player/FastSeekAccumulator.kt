package com.fyiplayer.app.player

/**
 * Accumulating double-tap seek: each tap within [windowMillis] of the previous one on the *same*
 * side adds [stepSeconds] to a running total instead of restarting it; a tap on the other side, or
 * one arriving after the window closes, starts a fresh accumulation. [clock] is injected so tests
 * fake time instead of sleeping. Pure — no Android imports.
 */
class FastSeekAccumulator(
    private val stepSeconds: Int,
    private val windowMillis: Long = 800,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    enum class Direction { FORWARD, BACKWARD }

    /** [signedSeconds] positive forward, negative backward. [isBurstStart] true when this tap
     *  began a fresh accumulation (first tap, direction switch, or window expiry) — the caller
     *  uses it to snapshot a fixed seek base once per burst instead of re-reading a moving
     *  position on every stacked tap. */
    data class TapResult(val signedSeconds: Int, val isBurstStart: Boolean)

    var direction: Direction? = null
        private set
    var totalSeconds: Int = 0
        private set
    private var lastTapAtMillis: Long = Long.MIN_VALUE

    /** Registers a tap. */
    fun tap(dir: Direction): TapResult {
        val now = clock()
        val expired = now - lastTapAtMillis > windowMillis
        val isBurstStart = expired || direction != dir
        totalSeconds = if (isBurstStart) stepSeconds else totalSeconds + stepSeconds
        direction = dir
        lastTapAtMillis = now
        val signed = if (dir == Direction.FORWARD) totalSeconds else -totalSeconds
        return TapResult(signed, isBurstStart)
    }

    /** Whether the overlay should still be showing (last tap within [windowMillis]). */
    fun isActive(): Boolean = direction != null && clock() - lastTapAtMillis <= windowMillis

    /** Clears accumulation, e.g. once the overlay has finished hiding. */
    fun reset() {
        direction = null
        totalSeconds = 0
        lastTapAtMillis = Long.MIN_VALUE
    }
}
