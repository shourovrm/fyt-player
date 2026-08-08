package com.fyiplayer.app

import android.content.Context
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Last-resort crash recorder. Writes a redacted summary to app-private storage so Settings can
 * show *something* about the last crash, then always hands off to Android's own default handler
 * -- this must never replace the platform's own crash flow, only observe it.
 *
 * Redaction is the whole point: an exception message can carry a signed media URL or a token
 * (CLAUDE.md's hard rule), so only class names and stack frames are ever written, never
 * `getMessage()`.
 */
object CrashLog {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable) // always delegate: real crash flow must still run
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"
        val sb = StringBuilder()
            .append(DateFormat.getDateTimeInstance().format(Date())).append('\n')
            .append("version ").append(versionName).append('\n')
            .append("thread ").append(thread.name).append('\n')
        var cause: Throwable? = throwable
        var depth = 0
        // depth cap, not just a self-reference check: an A->B->A cause cycle would otherwise
        // hang the crash handler itself and the delegation below would never run
        while (cause != null && depth++ < 8) {
            sb.append(cause.javaClass.name).append('\n') // class name only -- message may carry a URL
            cause.stackTrace.forEach { sb.append("\tat ").append(it).append('\n') }
            cause = cause.cause.takeIf { it !== cause }
        }
        File(context.filesDir, FILE_NAME).writeText(sb.toString())
    }

    fun read(context: Context): String? =
        File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
