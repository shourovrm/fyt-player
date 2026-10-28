package com.fyiplayer.app.settings

import com.fyiplayer.app.update.UpdateCheck
import com.fyiplayer.app.update.UpdateInfo

/** Outcome of one manual app update check, shown as the status chip under the app version. */
internal sealed interface CheckState {
    data object Idle : CheckState
    data object Checking : CheckState
    data object UpToDate : CheckState
    data object Failed : CheckState
    data class Found(val info: UpdateInfo) : CheckState
}

/** Maps a fetch result to a chip state. A null update means the installed build is the newest. */
internal fun checkStateOf(fetched: Result<UpdateInfo?>): CheckState = fetched.fold(
    onSuccess = { info -> info?.let(CheckState::Found) ?: CheckState.UpToDate },
    onFailure = { CheckState.Failed },
)

/** One check against GitHub releases. Runs only when the user taps Update or retries a failed chip. */
internal suspend fun checkForAppUpdate(): CheckState = checkStateOf(runCatching { UpdateCheck.fetchNewer() })
