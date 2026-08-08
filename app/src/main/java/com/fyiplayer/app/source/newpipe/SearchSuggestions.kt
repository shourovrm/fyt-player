package com.fyiplayer.app.source.newpipe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.ServiceList

/**
 * Autocomplete strings for a partial query, via NewPipe's YouTube suggestion endpoint (the only
 * suggestion source wired up). Failures collapse to an empty list -- a dead suggestion endpoint
 * must never surface as a search error.
 */
object SearchSuggestions {
    // ponytail: own OkHttpClient, same as NewPipeYoutubeSource -- NewPipeInit.ensure() is a
    // one-shot global singleton, so whichever client calls it first wins; this one goes idle
    // otherwise. Harmless duplication, not worth threading through DI for one lazy field.
    private val client = OkHttpClient()

    suspend fun fetch(query: String): List<String> = withContext(Dispatchers.IO) {
        NewPipeInit.ensure(client)
        runCatching { ServiceList.YouTube.suggestionExtractor.suggestionList(query) }.getOrDefault(emptyList())
    }
}
