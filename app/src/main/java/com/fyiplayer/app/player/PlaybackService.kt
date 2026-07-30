package com.fyiplayer.app.player

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

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
        mediaSession = MediaSession.Builder(this, PlaybackSession.exoPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        // the player is process-scoped and outlives this service; only the session wrapper dies
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
