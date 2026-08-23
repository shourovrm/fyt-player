package com.fyiplayer.app

import android.app.Application
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import com.fyiplayer.app.core.SourceRegistry
import com.fyiplayer.app.core.VideoRef
import com.fyiplayer.app.data.db.AppDatabase
import com.fyiplayer.app.data.prefs.Prefs
import com.fyiplayer.app.data.repo.PositionsRepository
import com.fyiplayer.app.engine.ChainResolver
import com.fyiplayer.app.engine.EngineGate
import com.fyiplayer.app.engine.EngineResolver
import com.fyiplayer.app.engine.WebViewResolver
import com.fyiplayer.app.player.PlaybackSession
import com.fyiplayer.app.source.newpipe.NewPipeInit
import com.fyiplayer.app.source.newpipe.NewPipeResolver
import com.fyiplayer.app.source.newpipe.WebViewJsDecoder
import com.fyiplayer.app.source.newpipe.YoutubeAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Process-scoped wiring. Everything here outlives every screen: playback must survive navigation,
 * and the extraction engine unpacks its native payload once per process, not once per Activity.
 */
class FyiApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Lazy: cheap until first real use, but constructed once so DataStore and Room state is shared.
    val prefs: Prefs by lazy { Prefs(this) }
    val database: AppDatabase by lazy { AppDatabase.get(this) }

    // Shared by NewPipeResolver only -- distinct from MediaItemFactory's playback client, a
    // different concern (metadata fetch vs. media datasource).
    private val newPipeHttpClient: OkHttpClient by lazy { OkHttpClient() }

    /** The one resolver seam: NewPipeExtractor fast path for YouTube watch/shorts, engine next,
     *  read-only headless capture last. */
    val resolver: ChainResolver by lazy {
        ChainResolver(
            // Cookies reach the engine for youtube.com pages only -- same isolation rule as
            // NewPipeDownloader's header injection.
            EngineResolver(cookieFor = { url ->
                if (isYoutubeHost(url)) YoutubeAuth.cookieHeader() else null
            }),
            WebViewResolver(this), NewPipeResolver(newPipeHttpClient),
        )
    }

    // Format selection runs on the player thread and cannot suspend, so the two preference flows
    // are mirrored into plain fields and the ceiling is picked per call by connection type.
    @Volatile private var maxHeightWifi = 1080
    @Volatile private var maxHeightMobile = 720
    // Mirrored the same way; DownloadQueue is a plain singleton with no DataStore access of its
    // own, so it reads this through a lambda handed to it in DownloadQueue.get.
    @Volatile private var downloadTreeUri: String? = null
    // Mirrored the same way; PlaybackSession's skip check runs on the player thread and cannot
    // suspend on a DataStore read.
    @Volatile private var sponsorBlockEnabled = false
    // Mirrored the same way; read synchronously inside autoplayNextFor before it does any work.
    @Volatile private var autoplayNextEnabled = false
    // Mirrored the same way; position load/save check it before touching the database.
    @Volatile private var savePlayPositionEnabled = true

    private val positionsRepo by lazy { PositionsRepository(database.playbackPositionDao()) }

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this) // first thing: must observe every crash from here on

        prefs.maxResolutionWifi.onEach { maxHeightWifi = it }.launchIn(appScope)
        prefs.maxResolutionMobile.onEach { maxHeightMobile = it }.launchIn(appScope)
        prefs.downloadTreeUri.onEach { downloadTreeUri = it }.launchIn(appScope)
        prefs.sponsorBlock.onEach { sponsorBlockEnabled = it }.launchIn(appScope)
        prefs.autoplayNext.onEach { autoplayNextEnabled = it }.launchIn(appScope)
        prefs.savePlayPosition.onEach { savePlayPositionEnabled = it }.launchIn(appScope)

        // Latches into NewPipeInit before the first extractor call and re-applies on every
        // settings change, so language/country needs no app restart.
        combine(prefs.contentLanguage, prefs.contentCountry) { lang, country -> lang to country }
            .onEach { (lang, country) -> NewPipeInit.updateLocalization(lang, country) }
            .launchIn(appScope)

        // Local sig/n decoder: without it every ciphered-signature video depends on the remote
        // api.pipepipe.dev decoder, which this device's network cannot always resolve -- an
        // undecoded sig/n is a guaranteed googlevideo 403. Full prewarm (player fetch + WebView
        // create + upload + compile) 1s after first resume, off the main thread, so it never
        // competes with first-frame rendering and the first ciphered video pays nothing for it.
        val jsDecoder = WebViewJsDecoder(this, newPipeHttpClient)
        org.schabi.newpipe.extractor.services.youtube.YoutubeApiDecoder.setLocalDecoder(jsDecoder)
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            private var scheduled = false
            override fun onActivityResumed(activity: android.app.Activity) {
                if (scheduled) return
                scheduled = true
                unregisterActivityLifecycleCallbacks(this)
                activity.window.decorView.postDelayed({
                    appScope.launch(Dispatchers.IO) { runCatching { jsDecoder.prewarm() } }
                }, 1_000)
            }
            override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) {}
            override fun onActivityStarted(a: android.app.Activity) {}
            override fun onActivityPaused(a: android.app.Activity) {}
            override fun onActivityStopped(a: android.app.Activity) {}
            override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: android.app.Activity) {}
        })

        YoutubeAuth.init(this)
        PlaybackSession.init(
            this, resolver, maxHeight = ::currentMaxHeight,
            sponsorBlockEnabled = { sponsorBlockEnabled }, autoplayNext = ::autoplayNextFor,
            loadPosition = ::loadPositionFor, savePosition = ::savePositionFor,
        )

        // Native init takes seconds on a cold install; resolves await EngineGate rather than
        // blocking startup on it.
        appScope.launch { EngineGate.init(this@FyiApp) }
    }

    /** Metered is the question that matters, not the radio: a metered hotspot must not burn data. */
    private fun currentMaxHeight(): Int {
        val metered = getSystemService<ConnectivityManager>()?.isActiveNetworkMetered ?: false
        return if (metered) maxHeightMobile else maxHeightWifi
    }

    /** Not private, unlike [currentMaxHeight]: [com.fyiplayer.app.download.DownloadQueue]'s
     *  companion reads this cross-package via a method reference. */
    fun currentDownloadTreeUri(): String? = downloadTreeUri

    /** [PlaybackSession]'s autoplay-on-end lookup: a title search against [ref]'s own source, the
     *  first result that is a plain video (not a channel/playlist stopgap ref, not live/upcoming,
     *  not a short) and isn't the video that just ended. There is no real recommendation system
     *  behind this -- PlaybackSettings' subtitle says so plainly. Any failure means null, same as
     *  the pref being off: autoplay must never surface an error of its own. */
    private suspend fun autoplayNextFor(ref: VideoRef): VideoRef? {
        if (!autoplayNextEnabled) return null
        return try {
            val source = SourceRegistry.bySourceId(ref.sourceId) ?: return null
            source.search(ref.title).items.firstOrNull { isAutoplayCandidate(it, ref) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // cancellation is control flow, not an autoplay failure
        } catch (e: Exception) {
            null
        }
    }

    /** Resume point for [pageUrl], or null when the pref is off, nothing useful is saved, or the
     *  video was (nearly) finished -- restarting a done video at 95% would be worse than at 0. */
    private suspend fun loadPositionFor(pageUrl: String): Long? {
        if (!savePlayPositionEnabled) return null
        val p = positionsRepo.get(pageUrl) ?: return null
        if (p.durationMs <= 0) return null
        return p.positionMs.takeIf { it > RESUME_MIN_MS && it < p.durationMs * 9 / 10 }
    }

    /** Near the end counts as watched: the row is cleared so no stale resume bar lingers --
     *  same behaviour as PipePipe. Sub-[RESUME_MIN_MS] positions aren't worth a database row. */
    private suspend fun savePositionFor(pageUrl: String, positionMs: Long, durationMs: Long) {
        if (!savePlayPositionEnabled || durationMs <= 0) return
        when {
            positionMs >= durationMs * 9 / 10 -> positionsRepo.clear(pageUrl)
            positionMs > RESUME_MIN_MS -> positionsRepo.save(pageUrl, positionMs, durationMs)
        }
    }
}

private const val RESUME_MIN_MS = 5_000L

/** A search hit [autoplayNextFor] may play next: a plain video page (not a channel/playlist
 *  stopgap ref), not live, not an unstarted premiere, not a short, and not the video that just
 *  ended. Pure and top-level so it's unit-testable without an Application instance. */
internal fun isAutoplayCandidate(candidate: VideoRef, current: VideoRef): Boolean =
    candidate.pageUrl != current.pageUrl &&
        candidate.pageUrl.contains("watch") &&
        !candidate.isShort && !candidate.isLive && !candidate.isUpcoming

/** Same host rule as NewPipeDownloader's header injection: youtube.com only, never CDNs. */
private fun isYoutubeHost(url: String): Boolean {
    val host = android.net.Uri.parse(url).host ?: return false
    return host == "youtube.com" || host.endsWith(".youtube.com")
}
