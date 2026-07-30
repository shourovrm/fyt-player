package com.fyiplayer.app.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
 * ponytail: no orientation lock to the stream's own aspect ratio here, unlike the reference this
 * was ported from. That needs `android:configChanges` declared on the Activity so a rotation
 * doesn't recreate it mid-fullscreen (which would immediately re-run this with the composable's
 * now-reset `fullscreen` state and undo itself) — `AndroidManifest.xml` is out of this task's
 * scope to edit, and guessing at that interaction with no device to verify it on is worse than
 * just hiding the bars. Add the orientation lock once the manifest change lands alongside it.
 */
fun setFullscreen(activity: Activity, fullscreen: Boolean) {
    val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (fullscreen) insetsController.hide(WindowInsetsCompat.Type.systemBars()) else insetsController.show(WindowInsetsCompat.Type.systemBars())
}

/** Keeps the screen on while [keepOn] (i.e. while actually playing). Callers clear this on
 *  dispose so a paused or closed player never pins the screen awake behind it. */
fun setKeepScreenOn(activity: Activity, keepOn: Boolean) {
    if (keepOn) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}
