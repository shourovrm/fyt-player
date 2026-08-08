package com.fyiplayer.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.fyiplayer.app.player.MiniPlayer
import com.fyiplayer.app.player.QueueBar
import com.fyiplayer.app.ui.AppScaffold
import com.fyiplayer.app.ui.AppShell
import com.fyiplayer.app.ui.openDetail
import com.fyiplayer.app.ui.theme.FyiTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Feeds SystemBarInsetsState from every REAL inset dispatch. Compose's own inset cache
        // (and any keyed re-read of the root view's cached copy) goes stale across the
        // fullscreen hide/show + in-process rotation on this OEM; a listener at the decor can't
        // -- it sees exactly what the system last delivered, nothing cached. Default handling
        // continues via ViewCompat.onApplyWindowInsets so decor + children behave as before.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { v, insets ->
            val sb = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
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
                    queueBar = { QueueBar() },
                    miniPlayer = { nav -> MiniPlayer(onOpen = { nav.openDetail(it) }) },
                ) { AppShell(it) }
            }
        }
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
