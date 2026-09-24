package com.fyiplayer.app.settings

import android.content.Intent
import android.webkit.CookieManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.YoutubeLoginActivity
import com.fyiplayer.app.source.newpipe.YoutubeAuth

/**
 * Returns a function that opens the YouTube sign-in screen and calls [onSignedIn] once a session
 * is stored. With [fresh] the WebView's own cookies are dropped first: the login screen finishes
 * the moment it sees a session cookie, so an expired one would be handed straight back. The
 * stored session is left alone until a new one lands, so backing out never signs anyone out.
 */
@Composable
fun rememberYoutubeSignIn(onSignedIn: () -> Unit): (fresh: Boolean) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) onSignedIn()
    }
    return { fresh ->
        val intent = Intent(context, YoutubeLoginActivity::class.java)
        if (fresh) {
            CookieManager.getInstance().removeAllCookies { launcher.launch(intent) }
        } else {
            launcher.launch(intent)
        }
    }
}

/** The way forward from a login/age wall on the player: the user's own sign-in, never a retry
 *  of the walled request. Signed in already means the session was rejected, so it asks for a
 *  fresh one. */
@Composable
fun WallSignInButton(onSignedIn: () -> Unit, modifier: Modifier = Modifier) {
    val loggedIn by YoutubeAuth.isLoggedIn.collectAsState()
    val signIn = rememberYoutubeSignIn(onSignedIn)
    OutlinedButton(
        onClick = { signIn(loggedIn) },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
        modifier = modifier,
    ) { Text(if (loggedIn) "Sign in again" else "Sign in") }
}
