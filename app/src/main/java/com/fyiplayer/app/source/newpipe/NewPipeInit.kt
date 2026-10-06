package com.fyiplayer.app.source.newpipe

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
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
    // The fork's own default is ON: it asks returnyoutubedislikeapi.com about every video opened.
    // Latched OFF until the user's setting says otherwise, applied at init for the same reason as
    // language/country above.
    @Volatile private var fetchDislike = false

    fun ensure(client: OkHttpClient) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(NewPipeDownloader(client), Localization(language), ContentCountry(country))
            applyAuthState()
            ServiceList.YouTube.setFetchDislike(fetchDislike)
            initialized = true
        }
    }

    /** Settings changes apply live; before init they only latch, since init itself applies them. */
    fun updateLocalization(language: String, country: String) {
        this.language = language
        this.country = country
        if (initialized) NewPipe.setupLocalization(Localization(language), ContentCountry(country))
    }

    /** Whether opening a video also asks returnyoutubedislike.com for its dislike count. Applies
     *  to videos fetched from now on; a StreamInfo already cached keeps what it was fetched with. */
    fun updateFetchDislike(enabled: Boolean) {
        fetchDislike = enabled
        if (initialized) ServiceList.YouTube.setFetchDislike(enabled)
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
        ServiceList.YouTube.setTokens(YoutubeAuth.cookieHeader())
        NewPipe.setYoutubePlayerClient("visionos")
    }

    // Guards withSignedInPlayerClient; suspend, unlike the `synchronized` it replaced, since the
    // block now awaits StreamInfoCache.get (a suspend fetch), not a bare blocking call.
    private val playerClientLock = Mutex()

    /** Runs [block] with the signed-in TVHTML5 player client, restoring visionos after. Only
     *  worth calling when a session exists -- anonymous tv_downgraded gains nothing. The client
     *  field is process-global in the fork, so this serializes on this object to keep a parallel
     *  prefetch resolve from riding the swapped client. */
    suspend fun <T> withSignedInPlayerClient(block: suspend () -> T): T = playerClientLock.withLock {
        NewPipe.setYoutubePlayerClient("tv_downgraded")
        try {
            block()
        } finally {
            NewPipe.setYoutubePlayerClient("visionos")
        }
    }
}
