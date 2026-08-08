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

    /** The extractor reads the session from `ServiceList.YouTube.setTokens` (its
     *  `addLoggedInHeaders` builds Cookie/SAPISIDHASH from it). The player client stays
     *  `visionos` even when signed in: signed-in `tv_downgraded` (TVHTML5) returns
     *  ciphered-signature URLs that googlevideo 403s for popular videos (device-verified live --
     *  the whole "No connection a lot of the time" report), while visionos URLs are unciphered
     *  and play. `tv_downgraded` is used only as the per-resolve age-wall retry
     *  ([withSignedInPlayerClient]). Called on init and again by [YoutubeAuth] on every
     *  login/logout. */
    fun updatePlayerClient() {
        if (initialized) applyAuthState()
    }

    private fun applyAuthState() {
        org.schabi.newpipe.extractor.ServiceList.YouTube.setTokens(YoutubeAuth.cookieHeader())
        NewPipe.setYoutubePlayerClient("visionos")
    }

    /** Runs [block] with the signed-in TVHTML5 player client, restoring visionos after. Only
     *  worth calling when a session exists -- anonymous tv_downgraded gains nothing. The client
     *  field is process-global in the fork, so this serializes on this object to keep a parallel
     *  prefetch resolve from riding the swapped client. */
    fun <T> withSignedInPlayerClient(block: () -> T): T = synchronized(this) {
        NewPipe.setYoutubePlayerClient("tv_downgraded")
        try {
            block()
        } finally {
            NewPipe.setYoutubePlayerClient("visionos")
        }
    }
}
