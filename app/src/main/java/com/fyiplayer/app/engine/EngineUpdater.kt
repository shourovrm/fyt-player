package com.fyiplayer.app.engine

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.write
import kotlinx.coroutines.withContext

// Own store, not Prefs.kt -- this is one timestamp read only by EngineSettings, not app-start
// config every screen shares.
private val Context.engineUpdaterStore by preferencesDataStore(name = "engine_updater")
private val LAST_CHECKED = longPreferencesKey("last_checked_ms")

enum class EngineChannel(val label: String) { STABLE("Stable"), NIGHTLY("Nightly"), MASTER("Master") }

// Confirmed against the artifact's bytecode (DECISIONS.md Gotchas): the leading underscore on
// _STABLE/_NIGHTLY/_MASTER is the real field name, not a typo.
internal fun EngineChannel.toSdkChannel(): YoutubeDL.UpdateChannel = when (this) {
    EngineChannel.STABLE -> YoutubeDL.UpdateChannel._STABLE
    EngineChannel.NIGHTLY -> YoutubeDL.UpdateChannel._NIGHTLY
    EngineChannel.MASTER -> YoutubeDL.UpdateChannel._MASTER
}

sealed interface UpdateResult {
    data class Updated(val version: String?) : UpdateResult
    data object AlreadyCurrent : UpdateResult
    data class Failed(val reason: String) : UpdateResult
}

// No URL scrubbing needed here (unlike ErrorMapping) -- an update call carries no page URL, so
// there is nothing signed/tokenised in its exception text. Still never surface the raw message:
// engine stderr can be long and is not meant for a settings row.
internal fun engineUpdateFailureReason(e: Throwable): String {
    val m = (e.message ?: "").lowercase()
    return when {
        listOf("timeout", "timed out", "unable to resolve host", "connection", "network").any { it in m } ->
            "Network error -- check your connection and try again."
        else -> "Update failed. Try again later."
    }
}

/**
 * Wraps youtubedl-android's blocking update call. [update] is Mutex-guarded so a double tap
 * can't race two updates against the same binary on disk, and it never throws -- every failure
 * mode, including a thrown exception, comes back as [UpdateResult.Failed].
 */
object EngineUpdater {
    private val mutex = Mutex()

    suspend fun installedVersion(context: Context): String? =
        withContext(Dispatchers.IO) { YoutubeDL.getInstance().version(context) }

    fun lastChecked(context: Context): Flow<Long?> =
        context.engineUpdaterStore.data.map { it[LAST_CHECKED] }

    /** [mutex] serialises update calls; [engineLock]'s write side keeps the binary swap from
     *  landing while an extraction is running against the old executable. */
    suspend fun update(context: Context, channel: EngineChannel): UpdateResult = mutex.withLock {
        EngineGate.await() // never race init; updating the binary mid-init would corrupt it
        withContext(Dispatchers.IO) {
            try {
                // Nullable: the SDK call is Java, so its result is a platform type. Treat an
                // unexpected null as a failure rather than force-unwrapping it into a crash.
                val status: YoutubeDL.UpdateStatus? = engineLock.write {
                    YoutubeDL.getInstance().updateYoutubeDL(context, channel.toSdkChannel())
                }
                context.engineUpdaterStore.edit { it[LAST_CHECKED] = System.currentTimeMillis() }
                when (status) {
                    YoutubeDL.UpdateStatus.DONE -> UpdateResult.Updated(YoutubeDL.getInstance().version(context))
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> UpdateResult.AlreadyCurrent
                    null -> UpdateResult.Failed("The engine did not report a result.")
                }
            } catch (e: Exception) {
                UpdateResult.Failed(engineUpdateFailureReason(e))
            }
        }
    }
}
