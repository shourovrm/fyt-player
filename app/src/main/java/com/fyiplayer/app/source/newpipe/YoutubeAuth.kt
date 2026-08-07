package com.fyiplayer.app.source.newpipe

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the user's own YouTube session cookie (first-party login via [YoutubeLoginActivity],
 * same mechanism NewPipe/PipePipe use -- a WebView on Google's real sign-in page; this app never
 * sees the password). SECURITY: the cookie string identifies the user's Google session. It must
 * never reach a log, a toast, an exception message, the database, an export, or a bug report --
 * it lives only in these app-private prefs and in memory.
 */
object YoutubeAuth {
    private const val PREFS_NAME = "yt_auth"
    private const val KEY_COOKIE = "cookie"

    private lateinit var prefs: SharedPreferences
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isLoggedIn.value = prefs.contains(KEY_COOKIE)
    }

    /** @throws IllegalArgumentException if [cookies] has no SAPISID -- a bad/partial cookie
     *  string would otherwise silently fail to auth every later request. */
    fun store(cookies: String) {
        require(cookies.contains("SAPISID") || cookies.contains("__Secure-3PAPISID")) {
            "missing SAPISID" // never interpolate the cookie itself into a message
        }
        prefs.edit().putString(KEY_COOKIE, cookies).apply()
        _isLoggedIn.value = true
    }

    fun clear() {
        prefs.edit().remove(KEY_COOKIE).apply()
        _isLoggedIn.value = false
    }

    // Downloader requests can race app startup; an uninitialised store means "not signed in",
    // never a crash on the request path.
    fun cookieHeader(): String? =
        if (::prefs.isInitialized) prefs.getString(KEY_COOKIE, null) else null

    /** Google's cookie-derived request-signing scheme for its own web clients. Timestamped, so
     *  computed fresh per call rather than cached. */
    fun authorizationHeader(): String? {
        val cookie = cookieHeader() ?: return null
        val sapisid = extractCookieValue(cookie, "SAPISID") ?: extractCookieValue(cookie, "__Secure-3PAPISID")
            ?: return null
        val timestamp = System.currentTimeMillis() / 1000
        val digest = sha1Hex("$timestamp $sapisid https://www.youtube.com")
        return "SAPISIDHASH ${timestamp}_$digest"
    }

    private fun extractCookieValue(cookieString: String, name: String): String? {
        val prefix = "$name="
        return cookieString.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith(prefix) }
            ?.substringAfter(prefix)
    }

    private fun sha1Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
