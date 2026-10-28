package com.fyiplayer.app.source.newpipe

import android.content.Context
import androidx.annotation.MainThread
import org.schabi.newpipe.extractor.NewPipe

/** One-time registration of the in-app PO-token minter that SABR playback needs. */
internal object SabrRuntime {
    private var isInitialized = false

    @MainThread
    fun initialize(context: Context) {
        if (isInitialized) {
            return
        }
        LocalDomPoTokenProvider.initialize(context.applicationContext)
        NewPipe.setYoutubePoTokenResolver { videoId -> LocalDomPoTokenProvider.getPlayerPoToken(videoId) }
        isInitialized = true
    }
}
