package com.fyiplayer.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.fyiplayer.app.FyiApp

/**
 * No DI framework in this app (single Activity, sideloaded APK) -- [FyiApp] already is the
 * composition root, holding Prefs/AppDatabase/resolver as process-scoped singletons. Screens read
 * it through this instead of each re-deriving `LocalContext.current.applicationContext as FyiApp`.
 */
@Composable
fun rememberFyiApp(): FyiApp {
    val context = LocalContext.current.applicationContext
    return remember(context) { context as FyiApp }
}
