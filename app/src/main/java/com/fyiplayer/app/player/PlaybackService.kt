package com.fyiplayer.app.player

import android.app.PendingIntent
import android.content.Intent
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
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        // the player is process-scoped and outlives this service; only the session wrapper dies
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
