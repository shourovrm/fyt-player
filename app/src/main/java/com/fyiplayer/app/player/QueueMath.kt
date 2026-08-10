package com.fyiplayer.app.player

/**
 * Pure index maths for the playback queue. No Android imports: this is the JVM-testable core.
 * A "queue index" is a position in the caller's `List<VideoRef>`. [order], when present, maps
 * play-order position -> queue index (shuffle); null/absent means play-order == queue order.
 */
enum class RepeatMode { OFF, ONE, ALL }

object QueueMath {

    fun clamp(index: Int, size: Int): Int = if (size <= 0) 0 else index.coerceIn(0, size - 1)

    /** Deterministic Fisher-Yates permutation of 0 until size, keyed by [seed]. Same seed, same order. */
    fun shuffleOrder(size: Int, seed: Long): List<Int> {
        if (size <= 0) return emptyList()
        val order = (0 until size).toMutableList()
        val rnd = kotlin.random.Random(seed)
        for (i in order.size - 1 downTo 1) {
            val j = rnd.nextInt(i + 1)
            val tmp = order[i]; order[i] = order[j]; order[j] = tmp
        }
        return order
    }

    /** Inserts [atQueueIndex] into [order] at play-position [atPlayPosition], shifting every
     *  existing queue index >= [atQueueIndex] up by one. */
    fun insertOrder(order: List<Int>, atQueueIndex: Int, atPlayPosition: Int): List<Int> {
        val shifted = order.map { if (it >= atQueueIndex) it + 1 else it }
        val pos = atPlayPosition.coerceIn(0, shifted.size)
        return shifted.toMutableList().apply { add(pos, atQueueIndex) }
    }

    /** Appends [queueIndex] to the end of the shuffle order. Used when a new item is added to
     *  the end of the queue and should simply follow the existing play order. */
    fun appendOrder(order: List<Int>, queueIndex: Int): List<Int> =
        order + queueIndex

    /** Remaps every queue index in [order] after a queue item is moved from [from] to [to]. */
    fun moveOrder(order: List<Int>, from: Int, to: Int): List<Int> {
        if (from == to) return order
        return order.map { mapIndexAfterMove(it, from, to) }
    }

    private fun mapIndexAfterMove(index: Int, from: Int, to: Int): Int = when {
        index == from -> to
        from < to && index in (from + 1)..to -> index - 1
        from > to && index in to..(from - 1) -> index + 1
        else -> index
    }

    /** Removes [queueIndex] from [order] and shifts every remaining queue index > [queueIndex]
     *  down by one. */
    fun removeOrder(order: List<Int>, queueIndex: Int): List<Int> =
        order.filter { it != queueIndex }.map { if (it > queueIndex) it - 1 else it }

    private fun sequence(size: Int, order: List<Int>?): List<Int> = order ?: (0 until size).toList()
    private fun positionOf(index: Int, seq: List<Int>): Int = seq.indexOf(index).let { if (it < 0) 0 else it }

    /** Next queue index to play, or null when playback should stop (repeat-off, at the end). */
    fun nextIndex(currentIndex: Int, size: Int, repeat: RepeatMode, order: List<Int>? = null): Int? {
        if (size <= 0) return null
        if (repeat == RepeatMode.ONE) return currentIndex
        val seq = sequence(size, order)
        val nextPos = positionOf(currentIndex, seq) + 1
        return when {
            nextPos < size -> seq[nextPos]
            repeat == RepeatMode.ALL -> seq[0]
            else -> null
        }
    }

    /** Previous queue index, or null when there is nowhere to go back to (repeat-off, at the start). */
    fun previousIndex(currentIndex: Int, size: Int, repeat: RepeatMode, order: List<Int>? = null): Int? {
        if (size <= 0) return null
        if (repeat == RepeatMode.ONE) return currentIndex
        val seq = sequence(size, order)
        val prevPos = positionOf(currentIndex, seq) - 1
        return when {
            prevPos >= 0 -> seq[prevPos]
            repeat == RepeatMode.ALL -> seq[size - 1]
            else -> null
        }
    }

    /**
     * Which queue indices need a resolved stream right now: the current item, and at most one
     * ahead. Never more — signed URLs expire, so resolving item 40 while item 3 plays hands the
     * player dead links by the time it gets there.
     */
    fun resolveWindow(currentIndex: Int, size: Int, repeat: RepeatMode, order: List<Int>? = null): List<Int> {
        if (size <= 0 || currentIndex !in 0 until size) return emptyList()
        val next = nextIndex(currentIndex, size, repeat, order)
        return if (next != null && next != currentIndex) listOf(currentIndex, next) else listOf(currentIndex)
    }
}
