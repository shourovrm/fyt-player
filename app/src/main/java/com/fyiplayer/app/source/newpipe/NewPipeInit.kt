package com.fyiplayer.app.source.newpipe

import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

/**
 * `NewPipe.init` is a bare static field write with no internal locking, so it must run exactly
 * once, lazily, on first resolve rather than at process start. Double-checked locking is the
 * whole mechanism needed for that.
 */
internal object NewPipeInit {
    @Volatile private var initialized = false

    // Prefs load long before the first extractor call, so the settings values are latched here and
    // init reads them. Passing them at the ensure() call site instead would let a late init
    // overwrite the user's choice with the defaults.
    @Volatile private var language = "en"
    @Volatile private var country = "US"

    fun ensure(client: OkHttpClient) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(NewPipeDownloader(client), Localization(language), ContentCountry(country))
            applyAuthState()
            initialized = true
        }
    }

    /** Settings changes apply live; before init they only latch, since init itself applies them. */
    fun updateLocalization(language: String, country: String) {
        this.language = language
        this.country = country
        if (initialized) NewPipe.setupLocalization(Localization(language), ContentCountry(country))
    }

    /** PipePipe's own login wiring, in two parts (PipePipeClient `App.reconcileYoutubePlayerClient`
     *  + `ServiceHelper.initServices`): the extractor reads the session from
     *  `ServiceList.YouTube.setTokens` (its `addLoggedInHeaders` builds Cookie/SAPISIDHASH from
     *  it), and the signed-in TVHTML5 player client is the one whose player responses honour the
     *  account's age verification. Called on init and again by [YoutubeAuth] on every
     *  login/logout, so both always match the session. */
    fun updatePlayerClient() {
        if (initialized) applyAuthState()
    }

    private fun applyAuthState() {
        val cookie = YoutubeAuth.cookieHeader()
        org.schabi.newpipe.extractor.ServiceList.YouTube.setTokens(cookie)
        NewPipe.setYoutubePlayerClient(if (cookie != null) "tv_downgraded" else "visionos")
    }
}
