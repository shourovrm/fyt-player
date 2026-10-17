package com.fyiplayer.app.settings

import android.webkit.CookieManager
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fyiplayer.app.R
import com.fyiplayer.app.source.newpipe.YoutubeAuth
import com.fyiplayer.app.ui.ConfirmDialog
import com.fyiplayer.app.ui.showToast
import com.fyiplayer.app.ui.theme.Sizes
import com.fyiplayer.app.ui.theme.Spacing

/** First-party YouTube sign-in (own Google account) for content the account can access --
 *  age-restricted videos, memberships. See [YoutubeLoginActivity] for the login flow. */
@Composable
fun AccountSettings() {
    val context = LocalContext.current
    val loggedIn by YoutubeAuth.isLoggedIn.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    val signedInMessage = stringResource(R.string.settings_signed_in_toast)
    val signedOutMessage = stringResource(R.string.settings_signed_out_toast)

    val signIn = rememberYoutubeSignIn(onSignedIn = { showToast(context, signedInMessage) })

    SettingsSection(null) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Sizes.listOneLine).padding(horizontal = Spacing.pageMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loggedIn) {
                SettingsLabel(
                    stringResource(R.string.settings_youtube_account),
                    stringResource(R.string.settings_summary_signed_in),
                    Modifier.weight(1f),
                )
                // For an expired or rejected session.
                TextButton(onClick = { signIn(true) }) { Text(stringResource(R.string.settings_sign_in_again)) }
                TextButton(onClick = { showSignOutConfirm = true }) {
                    Text(stringResource(R.string.settings_sign_out), color = MaterialTheme.colorScheme.error)
                }
            } else {
                SettingsLabel(
                    stringResource(R.string.settings_youtube_account),
                    stringResource(R.string.settings_summary_signed_out),
                    Modifier.weight(1f),
                )
                TextButton(onClick = { showConfirm = true }) { Text(stringResource(R.string.settings_sign_in)) }
            }
        }
    }

    if (showSignOutConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.settings_sign_out_title),
            confirmLabel = stringResource(R.string.settings_sign_out),
            onConfirm = {
                showSignOutConfirm = false
                YoutubeAuth.clear()
                CookieManager.getInstance().removeAllCookies(null)
                showToast(context, signedOutMessage)
            },
            onDismiss = { showSignOutConfirm = false },
        )
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.settings_sign_in_title)) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    signIn(false)
                }) { Text(stringResource(R.string.settings_sign_in)) }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
