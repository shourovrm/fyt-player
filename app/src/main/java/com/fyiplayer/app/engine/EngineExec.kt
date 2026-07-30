package com.fyiplayer.app.engine

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.util.UUID
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Runs one engine invocation and returns stdout. Shared by [EngineResolver] and the YouTube
 * source adapter -- both just need "await init, run the engine, map a free-text failure" and
 * nothing else, so this is the one place that boilerplate lives.
 */
internal suspend fun runEngine(url: String, vararg options: String): String {
    // The engine appends the URL after the options with no end-of-options separator, and the
    // engine's parser accepts options anywhere in its argv. An argument starting with "-" would
    // therefore be executed as an engine option rather than fetched as a URL — and a page URL can
    // arrive from an imported backup file, which is attacker-controlled input. Nothing this app
    // legitimately passes here starts with "-": page URLs are http(s), searches are "ytsearchN:".
    require(!url.startsWith("-")) { "refusing an option-shaped engine argument" }

    EngineGate.await()
    // suspendCancellableCoroutine runs its block on the CALLING thread, and the engine call below
    // blocks on a subprocess for seconds. Without this the whole thing runs on whatever thread
    // resolved — which is the main thread for every ViewModel and for PlaybackSession — and the
    // system kills the app for not answering input.
    return withContext(Dispatchers.IO) {
        // processId lets an outside coroutine cancellation kill the subprocess instead of merely
        // abandoning the wait.
        val processId = UUID.randomUUID().toString()
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { YoutubeDL.getInstance().destroyProcessById(processId) }
            try {
                val request = YoutubeDLRequest(url).apply { options.forEach { addOption(it) } }
                // Read side of the engine lock, taken and released inside this synchronous block:
                // many extractions may overlap each other, but none may overlap a binary update,
                // which swaps the executable out from under a running process. Held here rather
                // than around the whole suspending call so acquire and release stay on one thread.
                val response = engineLock.read {
                    YoutubeDL.getInstance().execute(request, processId, null)
                }
                cont.resume(response.out)
            } catch (e: Exception) {
                cont.resumeWithException(mapEngineError(e))
            }
        }
    }
}

/**
 * Guards the engine binary. Extractions take the read side (they may run concurrently); a binary
 * update takes the write side, so it waits for in-flight extractions to finish and blocks new ones
 * while the executable is being replaced.
 */
internal val engineLock = ReentrantReadWriteLock()
