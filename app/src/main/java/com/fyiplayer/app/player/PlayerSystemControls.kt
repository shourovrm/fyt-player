package com.fyiplayer.app.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Walks the wrapped-context chain Compose hands us down to the real [Activity]. */
internal tailrec fun Context.asActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.asActivity()
    else -> null
}

/** Manual window brightness (0..100); does not touch the system brightness setting. */
fun setWindowBrightness(activity: Activity, percent: Int) {
    val attrs = activity.window.attributes
    attrs.screenBrightness = percent.coerceIn(0, 100) / 100f
    activity.window.attributes = attrs
}

fun currentBrightnessPercent(activity: Activity): Int {
    val b = activity.window.attributes.screenBrightness
    return if (b in 0f..1f) (b * 100).toInt() else 50
}

/** Sets STREAM_MUSIC volume to a 0..100 percent of the stream's max. */
fun setStreamVolumePercent(context: Context, percent: Int) {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    am.setStreamVolume(AudioManager.STREAM_MUSIC, percent.coerceIn(0, 100) * max / 100, 0)
}

fun currentStreamVolumePercent(context: Context): Int {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    return (am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max).coerceIn(0, 100)
}

/**
 * Hides system bars for fullscreen, restores them otherwise. The screen that flips [fullscreen]
 * says so explicitly, on purpose — inferring it from inset visibility is what CLAUDE.md's
 * pitfalls call out as always getting it wrong.
 *
 * Orientation starts at the sensor default (either rotation) on entry and returns to unspecified
 * on exit; [applyAspectOrientation] narrows it to match the stream once the decoder reports real
 * dimensions. `AndroidManifest.xml`'s `configChanges` on the activity is what keeps that rotation
 * from recreating the Activity mid-fullscreen (which would reset the composable's fullscreen
 * state and undo this).
 */
fun setFullscreen(activity: Activity, fullscreen: Boolean) {
    activity.requestedOrientation = if (fullscreen) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR
    } else {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (fullscreen) insetsController.hide(WindowInsetsCompat.Type.systemBars()) else insetsController.show(WindowInsetsCompat.Type.systemBars())
    // Exit path: the rotation back is handled in-process, and this OEM does not re-deliver the
    // window's insets afterwards -- every app-side cache (Compose's holder AND
    // getRootWindowInsets) then keeps serving landscape values to a portrait layout, shifting
    // the page right. requestApplyInsets alone re-dispatches the same stale cache; reassigning
    // window attributes forces a WindowManager relayout round-trip, which makes the server
    // recompute and re-send the real insets.
    activity.window.decorView.post {
        activity.window.attributes = activity.window.attributes
        activity.window.decorView.requestApplyInsets()
    }
}


/**
 * While fullscreen, system bars track the in-app chrome: a tap that reveals the control row
 * reveals the status/nav bars with it, auto-hide takes both away. [setFullscreen] stays the
 * entry/exit authority. The stale-inset wedge this churn can trigger on this OEM is covered by
 * SystemBarInsetsState.restoreLatched() on exit -- PipePipe's manual-reset trick.
 */
fun setSystemBarsVisible(activity: Activity, visible: Boolean) {
    val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    if (visible) insetsController.show(WindowInsetsCompat.Type.systemBars()) else insetsController.hide(WindowInsetsCompat.Type.systemBars())
}

/**
 * Narrows fullscreen orientation to the stream's own aspect ratio: a portrait video (taller than
 * wide) locks portrait so it fills the screen edge to edge instead of letterboxing inside a
 * forced landscape frame; a landscape video locks landscape; unknown or square (0 or equal
 * dimensions — the decoder hasn't reported yet) leaves [setFullscreen]'s sensor default alone.
 * No-op outside fullscreen.
 */
fun applyAspectOrientation(activity: Activity, fullscreen: Boolean, videoWidth: Int, videoHeight: Int) {
    if (!fullscreen || videoWidth <= 0 || videoHeight <= 0 || videoWidth == videoHeight) return
    activity.requestedOrientation = if (videoHeight > videoWidth) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
}

/** Keeps the screen on while [keepOn] (i.e. while actually playing). Callers clear this on
 *  dispose so a paused or closed player never pins the screen awake behind it. */
fun setKeepScreenOn(activity: Activity, keepOn: Boolean) {
    if (keepOn) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}
