package com.fyiplayer.app.settings

import android.webkit.CookieManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.YoutubeLoginActivity
import com.fyiplayer.app.source.newpipe.YoutubeAuth
import com.fyiplayer.app.ui.showToast

/** First-party YouTube sign-in (own Google account) for content the account can access --
 *  age-restricted videos, memberships. See [YoutubeLoginActivity] for the login flow. */
@Composable
fun AccountSettings() {
    val context = LocalContext.current
    val loggedIn by YoutubeAuth.isLoggedIn.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }

    val loginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) showToast(context, "Signed in")
    }

    SettingsSection("Account") {
        if (loggedIn) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Signed in to YouTube", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    YoutubeAuth.clear()
                    CookieManager.getInstance().removeAllCookies(null)
                    showToast(context, "Signed out")
                }) { Text("Sign out") }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextButton(onClick = { showConfirm = true }) { Text("Sign in to YouTube") }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Sign in to YouTube") },
            text = { Text("You'll sign in to your Google account inside the app. The session is stored only on this device.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    loginLauncher.launch(android.content.Intent(context, YoutubeLoginActivity::class.java))
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } },
        )
    }
}
