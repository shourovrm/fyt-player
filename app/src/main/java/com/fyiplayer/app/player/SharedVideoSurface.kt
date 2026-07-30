package com.fyiplayer.app.player

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import com.fyiplayer.app.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView

/**
 * ONE [PlayerView] for the whole process, handed between hosts (full player, mini bar) instead of
 * destroyed and rebuilt — rebuilding it per screen is exactly what makes minimising flicker.
 * Held on the application context on purpose: it outlives any Activity, so an Activity context
 * here would leak one.
 */
private object SharedSurface {
    private var view: PlayerView? = null

    // Inflated rather than constructed: surface_type is only read from compiled XML.
    private fun view(context: Context): PlayerView =
        view ?: (
            LayoutInflater.from(context.applicationContext)
                .inflate(R.layout.shared_player_view, null) as PlayerView
            ).also { view = it }

    /** Moves the shared view into [host] (no-op if it's already there — `update` runs on every
     *  recomposition, and a remove/add round trip per frame would flicker). Last attacher wins. */
    fun attach(host: FrameLayout, resizeMode: Int) {
        val v = view(host.context)
        v.resizeMode = resizeMode
        if (v.parent !== host) {
            (v.parent as? ViewGroup)?.removeView(v)
            host.addView(
                v,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }
    }

    /** Only detaches if this host still owns it — the other host may already have claimed it, and
     *  disposal order between an outgoing and incoming host is not guaranteed. */
    fun detach(host: FrameLayout) {
        view?.let { if (it.parent === host) host.removeView(it) }
    }

    fun bind(player: Player?) {
        view?.player = player
    }

}

/**
 * Renders the shared surface here. [resizeMode] is per-host ([androidx.media3.ui.AspectRatioFrameLayout]
 * constants): the full player letterboxes (`RESIZE_MODE_FIT`), a mini bar fills its box (`RESIZE_MODE_ZOOM`).
 */
@Composable
fun SharedVideoSurface(player: Player?, resizeMode: Int, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx -> FrameLayout(ctx) },
        update = { host ->
            SharedSurface.attach(host, resizeMode)
            SharedSurface.bind(player)
        },
        onRelease = { host -> SharedSurface.detach(host) },
        modifier = modifier,
    )
}
