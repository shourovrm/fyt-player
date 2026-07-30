package com.fyiplayer.app.engine

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Engine native-lib init unpacks a Python runtime and is slow on first run. A resolve landing
 * before it finishes throws "instance not initialized" -- callers must [await] first.
 *
 * The Application class owns calling [init]; nothing in this package calls it.
 */
object EngineGate {
    @Volatile var ready: Boolean = false
        private set

    private val initMutex = Mutex()

    suspend fun await(timeoutMs: Long = 15_000L) {
        var waited = 0L
        while (!ready && waited < timeoutMs) {
            delay(300)
            waited += 300
        }
    }

    /** Idempotent under [initMutex] so a second concurrent caller just waits, never double-inits. */
    suspend fun init(context: Context) {
        if (ready) return
        initMutex.withLock {
            if (ready) return@withLock
            withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().init(context)
                FFmpeg.getInstance().init(context)
            }
            ready = true
        }
    }
}
