package com.fyiplayer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fyiplayer.app.player.MiniPlayer
import com.fyiplayer.app.player.QueueBar
import com.fyiplayer.app.ui.AppScaffold
import com.fyiplayer.app.ui.AppShell
import com.fyiplayer.app.ui.openDetail
import com.fyiplayer.app.ui.openSharedUrl
import com.fyiplayer.app.ui.theme.FyiTheme

/** First https URL found in shared/link-opened intent text -- share text is often prose with a
 *  link inside it, not a bare URL. Shared state (not an Activity field) because the NavHost that
 *  can actually consume it -- [AppShell] via [PendingSharedUrl] below -- lives one Compose frame
 *  later than [MainActivity.onCreate]. */
private val urlPattern = Regex("https?://\\S+")

private fun sharedUrlFrom(intent: Intent?): String? {
    val text = when (intent?.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        Intent.ACTION_VIEW -> intent.dataString
        else -> null
    } ?: return null
    return urlPattern.find(text)?.value
}

/** Cold start: [MainActivity.onCreate] can run before the NavHost exists in composition, so the
 *  shared URL waits here until [AppShell]'s content slot (in setContent, below) is ready to
 *  navigate. Warm start (onNewIntent) writes here too -- both paths funnel through one seam. */
private object PendingSharedUrl {
    var value by mutableStateOf<String?>(null)
}

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        PendingSharedUrl.value = sharedUrlFrom(intent)
        // Feeds SystemBarInsetsState from every REAL inset dispatch. Compose's own inset cache
        // (and any keyed re-read of the root view's cached copy) goes stale across the
        // fullscreen hide/show + in-process rotation on this OEM; a listener at the decor can't
        // -- it sees exactly what the system last delivered, nothing cached. Default handling
        // continues via ViewCompat.onApplyWindowInsets so decor + children behave as before.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            val sb = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val cut = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            android.util.Log.d("Insets", "rot=${display?.rotation} sb=[${sb.left},${sb.top},${sb.right},${sb.bottom}] cutout=[${cut.left},${cut.top},${cut.right},${cut.bottom}]")
            com.fyiplayer.app.ui.SystemBarInsetsState.update(sb.left, sb.top, sb.right, sb.bottom)
            androidx.core.view.ViewCompat.onApplyWindowInsets(v, insets)
        }
        // The media notification is technically exempt from POST_NOTIFICATIONS, but this device's
        // OEM keeps an app's notifications at importance=NONE until the permission is granted —
        // no lockscreen/notification playback controls without asking once.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            FyiTheme {
                AppScaffold(
                    queueBar = { nav -> QueueBar(onOpen = { nav.openDetail(it) }) },
                    miniPlayer = { nav -> MiniPlayer(onOpen = { nav.openDetail(it) }) },
                ) { nav ->
                    // Runs once the graph is set (same composition pass, before this effect body
                    // executes) and again on every new shared URL -- warm-start intents included.
                    LaunchedEffect(nav, PendingSharedUrl.value) {
                        PendingSharedUrl.value?.let { url ->
                            PendingSharedUrl.value = null
                            nav.openSharedUrl(url)
                        }
                    }
                    AppShell(nav)
                }
            }
        }
    }

    /** Warm start: singleTask means a share/open-with while the app is already running lands
     *  here instead of a fresh onCreate. setIntent keeps getIntent() consistent for anything else
     *  that reads it later (e.g. a future config-change re-read). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUrlFrom(intent)?.let { PendingSharedUrl.value = it }
    }

    /** Rotation is handled in-process (manifest configChanges), and this OEM does not always
     *  re-dispatch window insets after it -- the layout then keeps the OLD orientation's insets
     *  (portrait page indented by a landscape side inset after leaving fullscreen). Ask for a
     *  fresh dispatch once the new configuration has laid out. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        window.decorView.post {
            // attribute reassign = forced WindowManager relayout round-trip; the server then
            // recomputes and re-sends this window's insets, refreshing every app-side cache
            window.attributes = window.attributes
            window.decorView.requestApplyInsets()
        }
    }
}
