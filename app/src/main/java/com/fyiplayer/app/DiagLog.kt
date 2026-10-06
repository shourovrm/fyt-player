package com.fyiplayer.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Persistent playback trace. logcat's ring buffer on the test device holds ~20 minutes, so an
 * intermittent "slow start, then an error" report was gone long before anyone could read it.
 * This keeps the last few hundred events in app-private storage for Settings to show.
 *
 * Callers pass only redaction-safe text: tags, tier names, exception CLASS names, HTTP status
 * codes, durations, queue indices. Never a URL, a title, a cookie, or an exception message --
 * same hard rule as [CrashLog].
 */
object DiagLog {
    private const val FILE_NAME = "playback_trace.txt"
    private const val MAX_BYTES = 64 * 1024L

    @Volatile private var file: File? = null

    // File appends happen on playback paths (some on the main thread); one background thread
    // keeps them off it and keeps the lines in call order.
    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DiagLog").apply { isDaemon = true }
    }
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        file = File(context.applicationContext.filesDir, FILE_NAME)
    }

    /** Before [init] (plain JUnit) this only tries logcat, and swallows that being unmocked. */
    fun log(tag: String, line: String) {
        try {
            android.util.Log.d(tag, line)
        } catch (logError: Throwable) {
            // android.util.Log is unmocked under plain JUnit (no Robolectric here)
        }
        val target = file ?: return
        val now = Date()
        writer.execute {
            runCatching {
                if (target.length() > MAX_BYTES) dropOlderHalf(target)
                target.appendText("${timeFormat.format(now)} $tag: $line\n")
            }
        }
    }

    private fun dropOlderHalf(target: File) {
        val lines = target.readLines()
        target.writeText(lines.drop(lines.size / 2).joinToString("\n", postfix = "\n"))
    }

    fun read(context: Context): String? =
        File(context.applicationContext.filesDir, FILE_NAME).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        val target = File(context.applicationContext.filesDir, FILE_NAME)
        writer.execute { target.delete() }
    }
}
