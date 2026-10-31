package com.fyiplayer.app.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import java.util.concurrent.CopyOnWriteArraySet

/**
 * What the media session (notification, lockscreen, headset, Bluetooth) controls. ExoPlayer only
 * ever holds the playing item and one prefetched slot, so its own idea of "next" is wrong for this
 * app: it jumps into the slot behind [PlaybackSession]'s back, and it knows nothing of autoplay or
 * the watch trail. Next and Previous are therefore answered and executed by [PlaybackSession], the
 * same code the in-app buttons use.
 */
internal class SessionPlayer(player: Player) : ForwardingPlayer(player) {
    private val listeners = CopyOnWriteArraySet<Player.Listener>()
    private var lastNotified: Player.Commands? = null

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        super.removeListener(listener)
    }

    /** Next/Previous availability changes without the wrapped player noticing (autoplay toggled,
     *  trail moved), so tell the session ourselves or the notification keeps its old buttons. */
    fun notifyCommandsChanged() {
        val commands = availableCommands
        if (commands == lastNotified) return
        lastNotified = commands
        listeners.forEach { it.onAvailableCommandsChanged(commands) }
    }

    override fun getAvailableCommands(): Player.Commands {
        val next = PlaybackSession.canSkipNext()
        val previous = PlaybackSession.canSkipPrevious()
        return super.getAvailableCommands().buildUpon()
            .removeAll(
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            )
            .addIf(Player.COMMAND_SEEK_TO_NEXT, next)
            .addIf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, next)
            .addIf(Player.COMMAND_SEEK_TO_PREVIOUS, previous)
            .addIf(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, previous)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    override fun hasNextMediaItem(): Boolean = PlaybackSession.canSkipNext()
    override fun hasPreviousMediaItem(): Boolean = PlaybackSession.canSkipPrevious()

    override fun seekToNext() = PlaybackSession.skipNextFromSession()
    override fun seekToNextMediaItem() = PlaybackSession.skipNextFromSession()
    override fun seekToPrevious() = PlaybackSession.skipPreviousFromSession()
    override fun seekToPreviousMediaItem() = PlaybackSession.skipPreviousFromSession()
}
