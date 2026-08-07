package com.fyiplayer.app.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Keeps [PlaybackSession]'s player controllable from the lockscreen, headset and Bluetooth
 * controls, and alive while the app is backgrounded. Its foreground-service notification CANNOT
 * be hidden and is not suppressed by the user blocking the app's own notifications — it must
 * describe what's actually playing, honestly, not apologize for existing.
 *
 * [PlaybackSession.init] must have already run (the Application does this at process start), so
 * [PlaybackSession.exoPlayer] is safe to read here.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // Tapping the notification/lockscreen art must land somewhere; launcher intent is the
        // only Activity this app has.
        val openApp = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this, 0,
                it.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        mediaSession = MediaSession.Builder(this, PlaybackSession.exoPlayer)
            .apply { openApp?.let(::setSessionActivity) }
            .setCallback(CloseButtonCallback())
            .build()
            // addSession, not just onGetSession: this service is STARTED (never bound), and
            // onGetSession only runs on a controller bind. Without registering the session here,
            // media3's notification manager never attaches — no media notification, no foreground
            // promotion, ever (observed on device: startForegroundCount stayed 0 while playing).
            .also(::addSession)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        // the player is process-scoped and outlives this service; only the session wrapper dies
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    /**
     * Adds a Close (×) action to the media notification — media3 gives play/pause/skip for free,
     * but nothing that fully stops playback, unlike NewPipe's notification × button.
     *
     * Verified against the media3-session 1.9.4 .aar bytecode (no API guessed from memory):
     * - [MediaNotificationManager] builds the actual notification action list from the connected
     *   controller's `mediaButtonPreferences`, not `setCustomLayout` (the latter is not deprecated
     *   in 1.9.4 either — no `@Deprecated` on it — but it isn't what the phone notification reads).
     * - [CommandButton.ICON_STOP] is the closest built-in glyph to an X/close; there is no
     *   ICON_CLOSE in this version.
     * - An unslotted button defaults to `SLOT_OVERFLOW` (`CommandButton.Builder.build()` calls
     *   `getDefaultSlot`, and `ICON_STOP` isn't in the back/forward icon lists there).
     *   `DefaultMediaNotificationProvider.getMediaButtons()` appends `SLOT_OVERFLOW` buttons AFTER
     *   the play/pause/skip buttons it synthesizes from the player's own commands — i.e. Close
     *   lands last for free; no explicit slot needed.
     */
    private inner class CloseButtonCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val closeButton = CommandButton.Builder(CommandButton.ICON_STOP)
                .setSessionCommand(CLOSE_COMMAND)
                .setDisplayName("Close")
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(CLOSE_COMMAND)
                        .build()
                )
                .setMediaButtonPreferences(listOf(closeButton))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != CLOSE_ACTION) {
                return super.onCustomCommand(session, controller, customCommand, args)
            }
            // stops the player, drops the queue, stops this service -> notification goes with it
            PlaybackSession.clear()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private companion object {
        const val CLOSE_ACTION = "com.fyiplayer.CLOSE"
        val CLOSE_COMMAND = SessionCommand(CLOSE_ACTION, Bundle.EMPTY)
    }
}
