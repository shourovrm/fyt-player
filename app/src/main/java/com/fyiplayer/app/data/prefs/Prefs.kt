package com.fyiplayer.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

// One store: every setting here is small and read together on app start, none big or
// write-heavy enough to earn its own file the way a Room table does.
class Prefs(private val context: Context) {
    private val data: Flow<Preferences> get() = context.settingsStore.data
    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.settingsStore.edit { it[key] = value }
    }
    private fun <T> flow(key: Preferences.Key<T>, default: T): Flow<T> = data.map { it[key] ?: default }

    private companion object {
        val ENABLED_SOURCES = stringSetPreferencesKey("enabled_sources")
        val MAX_RES_WIFI = intPreferencesKey("max_res_wifi")
        val MAX_RES_MOBILE = intPreferencesKey("max_res_mobile")
        val CONTAINER = stringPreferencesKey("container")
        val RECORD_WATCH_HISTORY = booleanPreferencesKey("record_watch_history")
        val RECORD_SEARCH_HISTORY = booleanPreferencesKey("record_search_history")
        val GESTURE_BRIGHTNESS = booleanPreferencesKey("gesture_brightness")
        val GESTURE_VOLUME = booleanPreferencesKey("gesture_volume")
        val BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
        val CONTENT_LANGUAGE = stringPreferencesKey("content_language")
        val CONTENT_COUNTRY = stringPreferencesKey("content_country")
        val DOWNLOAD_TREE_URI = stringPreferencesKey("download_tree_uri")
        val SPONSOR_BLOCK = booleanPreferencesKey("sponsor_block")
    }

    // only platform live today; default matches SourceRegistry without this file naming it
    val enabledSources: Flow<Set<String>> = flow(ENABLED_SOURCES, setOf("youtube"))
    suspend fun setEnabledSources(v: Set<String>) = set(ENABLED_SOURCES, v)

    val maxResolutionWifi: Flow<Int> = flow(MAX_RES_WIFI, 1080)
    suspend fun setMaxResolutionWifi(v: Int) = set(MAX_RES_WIFI, v)

    val maxResolutionMobile: Flow<Int> = flow(MAX_RES_MOBILE, 720)
    suspend fun setMaxResolutionMobile(v: Int) = set(MAX_RES_MOBILE, v)

    val preferredContainer: Flow<String> = flow(CONTAINER, "mp4")
    suspend fun setPreferredContainer(v: String) = set(CONTAINER, v)

    val recordWatchHistory: Flow<Boolean> = flow(RECORD_WATCH_HISTORY, true)
    suspend fun setRecordWatchHistory(v: Boolean) = set(RECORD_WATCH_HISTORY, v)

    val recordSearchHistory: Flow<Boolean> = flow(RECORD_SEARCH_HISTORY, true)
    suspend fun setRecordSearchHistory(v: Boolean) = set(RECORD_SEARCH_HISTORY, v)

    val gestureBrightness: Flow<Boolean> = flow(GESTURE_BRIGHTNESS, true)
    suspend fun setGestureBrightness(v: Boolean) = set(GESTURE_BRIGHTNESS, v)

    val gestureVolume: Flow<Boolean> = flow(GESTURE_VOLUME, true)
    suspend fun setGestureVolume(v: Boolean) = set(GESTURE_VOLUME, v)

    val backgroundPlayback: Flow<Boolean> = flow(BACKGROUND_PLAYBACK, true)
    suspend fun setBackgroundPlayback(v: Boolean) = set(BACKGROUND_PLAYBACK, v)

    // ISO 639-1 / ISO 3166-1 alpha-2, fed straight to NewPipe's Localization/ContentCountry.
    val contentLanguage: Flow<String> = flow(CONTENT_LANGUAGE, "en")
    suspend fun setContentLanguage(v: String) = set(CONTENT_LANGUAGE, v)

    val contentCountry: Flow<String> = flow(CONTENT_COUNTRY, "US")
    suspend fun setContentCountry(v: String) = set(CONTENT_COUNTRY, v)

    // Off by default: skipping content is an opt-in, and the lookup calls a third-party API.
    // (No originalTitles pref: the extractor fork forces non-localized titles unconditionally.)
    val sponsorBlock: Flow<Boolean> = flow(SPONSOR_BLOCK, false)
    suspend fun setSponsorBlock(v: Boolean) = set(SPONSOR_BLOCK, v)

    // SAF tree URI finished downloads get COPIED into; unset means app-private storage only, so
    // this has no default and bypasses the flow()/set() helpers, which require a non-null T.
    val downloadTreeUri: Flow<String?> = data.map { it[DOWNLOAD_TREE_URI] }
    suspend fun setDownloadTreeUri(v: String?) = context.settingsStore.edit {
        if (v == null) it.remove(DOWNLOAD_TREE_URI) else it[DOWNLOAD_TREE_URI] = v
    }
}
