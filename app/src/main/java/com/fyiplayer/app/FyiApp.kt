package com.fyiplayer.app

import android.app.Application
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import com.fyiplayer.app.data.db.AppDatabase
import com.fyiplayer.app.data.prefs.Prefs
import com.fyiplayer.app.engine.ChainResolver
import com.fyiplayer.app.engine.EngineGate
import com.fyiplayer.app.engine.EngineResolver
import com.fyiplayer.app.engine.WebViewResolver
import com.fyiplayer.app.player.PlaybackSession
import com.fyiplayer.app.source.newpipe.NewPipeInit
import com.fyiplayer.app.source.newpipe.NewPipeResolver
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
        ChainResolver(EngineResolver(), WebViewResolver(this), NewPipeResolver(newPipeHttpClient))
    }

    // Format selection runs on the player thread and cannot suspend, so the two preference flows
    // are mirrored into plain fields and the ceiling is picked per call by connection type.
    @Volatile private var maxHeightWifi = 1080
    @Volatile private var maxHeightMobile = 720

    override fun onCreate() {
        super.onCreate()

        prefs.maxResolutionWifi.onEach { maxHeightWifi = it }.launchIn(appScope)
        prefs.maxResolutionMobile.onEach { maxHeightMobile = it }.launchIn(appScope)

        // Latches into NewPipeInit before the first extractor call and re-applies on every
        // settings change, so language/country needs no app restart.
        combine(prefs.contentLanguage, prefs.contentCountry) { lang, country -> lang to country }
            .onEach { (lang, country) -> NewPipeInit.updateLocalization(lang, country) }
            .launchIn(appScope)

        YoutubeAuth.init(this)
        PlaybackSession.init(this, resolver, maxHeight = ::currentMaxHeight)

        // Native init takes seconds on a cold install; resolves await EngineGate rather than
        // blocking startup on it.
        appScope.launch { EngineGate.init(this@FyiApp) }
    }

    /** Metered is the question that matters, not the radio: a metered hotspot must not burn data. */
    private fun currentMaxHeight(): Int {
        val metered = getSystemService<ConnectivityManager>()?.isActiveNetworkMetered ?: false
        return if (metered) maxHeightMobile else maxHeightWifi
    }
}
