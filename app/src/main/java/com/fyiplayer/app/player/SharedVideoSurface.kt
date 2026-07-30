package com.fyiplayer.app.player

import android.content.Context
import android.util.AttributeSet
import android.util.Xml
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import java.io.StringReader

/**
 * ONE [PlayerView] for the whole process, handed between hosts (full player, mini bar) instead of
 * destroyed and rebuilt — rebuilding it per screen is exactly what makes minimising flicker.
 * Held on the application context on purpose: it outlives any Activity, so an Activity context
 * here would leak one.
 */
private object SharedSurface {
    private var view: PlayerView? = null

    private fun view(context: Context): PlayerView =
        view ?: PlayerView(context.applicationContext, textureSurfaceAttrs()).apply {
            useController = false // this app's own chrome drives transport, not media3's overlay
        }.also { view = it }

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

    // PlayerView only reads surface_type from an AttributeSet at construction time, and its code
    // default is a plain SurfaceView. player/ owns no res/layout file to declare texture_view in,
    // so this builds a one-attribute AttributeSet in memory instead of adding one.
    private fun textureSurfaceAttrs(): AttributeSet {
        val xml = """<PlayerView xmlns:app="http://schemas.android.com/apk/res-auto" app:surface_type="texture_view" />"""
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        parser.next()
        return Xml.asAttributeSet(parser)
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
