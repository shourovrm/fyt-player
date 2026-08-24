package com.fyiplayer.app.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fyiplayer.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class UpdateInfo(val version: String, val apkUrl: String)

/**
 * "Is a newer APK out?" against GitHub releases — the app's only distribution channel (README
 * points every install at releases/latest). Unauthenticated, one request, no paging.
 */
object UpdateCheck {
    private const val LATEST_URL =
        "https://api.github.com/repos/shourovrm/fyt-player/releases/latest"

    /** Newer-than-installed release, if any check has found one; the top banner keys off this. */
    var available by mutableStateOf<UpdateInfo?>(null)
        private set

    private var autoChecked = false
    private val client by lazy { OkHttpClient() }

    /** App-start check: once per process, silent on every failure (an offline start is normal). */
    suspend fun autoCheck() {
        if (autoChecked) return
        autoChecked = true
        runCatching { fetchNewer() }
    }

    /** One live check. Returns the newer release, or null when up to date; throws when the check
     *  itself failed (offline, rate limit) so Settings can say so honestly. */
    suspend fun fetchNewer(): UpdateInfo? = withContext(Dispatchers.IO) {
        val body = client.newCall(
            Request.Builder().url(LATEST_URL).header("Accept", "application/vnd.github+json").build(),
        ).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            resp.body!!.string()
        }
        val root = Json.parseToJsonElement(body).jsonObject
        val version = root.getValue("tag_name").jsonPrimitive.content.removePrefix("v")
        val apkUrl = root["assets"]?.jsonArray.orEmpty().map { it.jsonObject }
            .firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
            ?.get("browser_download_url")?.jsonPrimitive?.content
        val info = if (apkUrl != null && isNewer(version, BuildConfig.VERSION_NAME)) {
            UpdateInfo(version, apkUrl)
        } else null
        if (info != null) available = info
        info
    }
}

/** True iff [latest] is strictly newer than [current]: dot-separated numeric segments compared
 *  left to right, the shorter side zero-padded ("0.3" == "0.3.0"). Any non-numeric segment on
 *  either side means "can't tell" -> false, so a garbage release tag never raises a bogus banner. */
fun isNewer(latest: String, current: String): Boolean {
    val l = latest.trim().split(".").map { it.toIntOrNull() }
    val c = current.trim().split(".").map { it.toIntOrNull() }
    if (l.any { it == null } || c.any { it == null }) return false
    for (i in 0 until maxOf(l.size, c.size)) {
        val lv = l.getOrNull(i) ?: 0
        val cv = c.getOrNull(i) ?: 0
        if (lv != cv) return lv > cv
    }
    return false
}
