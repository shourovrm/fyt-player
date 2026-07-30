package com.fyiplayer.app.engine

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Runs one engine invocation and returns stdout. Shared by [EngineResolver] and the YouTube
 * source adapter -- both just need "await init, run the engine, map a free-text failure" and
 * nothing else, so this is the one place that boilerplate lives.
 */
internal suspend fun runEngine(url: String, vararg options: String): String {
    EngineGate.await()
    // processId lets an outside coroutine cancellation kill the subprocess instead of merely
    // abandoning the wait.
    val processId = UUID.randomUUID().toString()
    return suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { YoutubeDL.getInstance().destroyProcessById(processId) }
        try {
            val request = YoutubeDLRequest(url).apply { options.forEach { addOption(it) } }
            val response = YoutubeDL.getInstance().execute(request, processId, null)
            cont.resume(response.out)
        } catch (e: Exception) {
            cont.resumeWithException(mapEngineError(e))
        }
    }
}
