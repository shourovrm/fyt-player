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
import androidx.media3.ui.SubtitleView

/**
 * ONE [PlayerView] for the whole process, handed between hosts (full player, mini bar) instead of
 * destroyed and rebuilt — rebuilding it per screen is exactly what makes minimising flicker.
 * Held on the application context on purpose: it outlives any Activity, so an Activity context
 * here would leak one.
 */
private object SharedSurface {
    private var view: PlayerView? = null

    // Compose disposal order between an outgoing and incoming host isn't guaranteed — an
    // outgoing host's onRelease can run after the incoming host attached. "detach only if I still
    // own it" used to rely on that ordering; without it, the outgoing host's detach fired after
    // the incoming host had already reclaimed the view (see attach()'s doc), ripping it off its
    // new (correct) parent and leaving it parentless — black screen until some other host
    // happened to recompose. The registry makes ownership explicit instead of order-dependent:
    // detach() re-homes the view to whoever's next in line rather than just removing it.
    private val registry = AttachRegistry<FrameLayout, SurfaceLook>()

    // Last size written to the shared SubtitleView. update runs on every recomposition, and
    // re-setting the same text size would invalidate the captions view each time.
    private var appliedCaptionSize: CaptionSize? = null

    // Inflated rather than constructed: surface_type is only read from compiled XML.
    private fun view(context: Context): PlayerView =
        view ?: (
            LayoutInflater.from(context.applicationContext)
                .inflate(R.layout.shared_player_view, null) as PlayerView
            ).also { view = it }

    private fun applyCaptionSize(v: PlayerView, captionSize: CaptionSize) {
        if (captionSize == appliedCaptionSize) return
        val subtitles = requireNotNull(v.subtitleView) { "shared_player_view has no SubtitleView" }
        subtitles.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * captionSize.scale)
        appliedCaptionSize = captionSize
    }

    private fun parent(v: PlayerView, host: FrameLayout, look: SurfaceLook) {
        v.resizeMode = look.resizeMode
        applyCaptionSize(v, look.captionSize)
        if (v.parent !== host) {
            (v.parent as? ViewGroup)?.removeView(v)
            host.addView(
                v,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }
    }

    /** Moves the shared view into [host] (no-op if it's already there — `update` runs on every
     *  recomposition, and a remove/add round trip per frame would flicker). Most-recently
     *  attached host is the owner. */
    fun attach(host: FrameLayout, look: SurfaceLook) {
        registry.attach(host, look)
        parent(view(host.context), host, look)
    }

    /** Drops [host]'s claim. All calls happen on the main thread (Compose applier), so the
     *  registry needs no locking. */
    fun detach(host: FrameLayout) {
        val next = registry.detach(host)
        val v = view ?: return
        if (v.parent !== host && v.parent != null) return // some other host already owns it
        (v.parent as? ViewGroup)?.removeView(v)
        if (next != null) parent(v, next.key, next.value)
    }

    fun bind(player: Player?) {
        view?.player = player
    }

}

/**
 * Pure ownership bookkeeping for [SharedSurface] — no Android types, so it's unit-testable on the
 * JVM. Most-recently attached host is the owner; [detach] hands ownership to whoever's next.
 */
internal class AttachRegistry<H, V> {
    private val hosts = LinkedHashMap<H, V>() // insertion order = attach order; last = owner

    fun attach(host: H, value: V) {
        hosts.remove(host) // re-insert at the end: move-to-top
        hosts[host] = value
    }

    /** Returns the new owner to hand the view to, or null if none remains. */
    fun detach(host: H): Map.Entry<H, V>? {
        hosts.remove(host)
        return hosts.entries.lastOrNull()
    }
}

/** How the shared view looks while a given host owns it. Held per host so a hand-off to another
 *  host restores that host's look, not the previous owner's. */
internal data class SurfaceLook(val resizeMode: Int, val captionSize: CaptionSize)

/**
 * Renders the shared surface here. [resizeMode] is per-host ([androidx.media3.ui.AspectRatioFrameLayout]
 * constants): the full player letterboxes (`RESIZE_MODE_FIT`), a mini bar fills its box (`RESIZE_MODE_ZOOM`).
 * [captionSize] is the host's own caption pref: videos pass the video pref, the Shorts pager passes the Shorts pref.
 */
@Composable
fun SharedVideoSurface(player: Player?, resizeMode: Int, captionSize: CaptionSize, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx -> FrameLayout(ctx) },
        update = { host ->
            SharedSurface.attach(host, SurfaceLook(resizeMode, captionSize))
            SharedSurface.bind(player)
        },
        onRelease = { host -> SharedSurface.detach(host) },
        modifier = modifier,
    )
}
