package com.fyiplayer.app.player.sabr

import org.schabi.newpipe.extractor.stream.AudioStream

/** The facts about an audio track that decide whether it is chosen. */
data class AudioCandidate(val trackId: String?, val trackName: String?, val bitrate: Int)

/** Prefers the original-language track, as PipePipe's AudioStreamSelector does with its default setting. */
object AudioTrackChoice {
    /**
     * Index into [candidates] of the best track, or -1 when the list is empty. A track in [preferredLanguage]
     * (a language code such as "de"; ids like "de.4" and "de-DE" both match) wins; without a match the original
     * track is chosen.
     */
    fun pickIndex(candidates: List<AudioCandidate>, preferredLanguage: String? = null): Int {
        if (candidates.isEmpty()) {
            return -1
        }
        val inPreferredLanguage = candidates.filter { candidate -> isInLanguage(candidate, preferredLanguage) }
        if (inPreferredLanguage.isNotEmpty()) {
            return candidates.indexOf(inPreferredLanguage.maxWith(compareBy { it.bitrate }))
        }
        val original = candidates.filter { candidate -> isOriginal(candidate) }
        val withoutTrackInfo = candidates.filter { candidate -> candidate.trackId == null }
        val pool = original.ifEmpty { withoutTrackInfo.ifEmpty { candidates } }
        val best = pool.maxWith(compareBy { it.bitrate })
        return candidates.indexOf(best)
    }

    /** Same choice for extractor streams; the index refers to [streams]. */
    @JvmStatic
    @JvmOverloads
    fun pickIndexOfStreams(streams: List<AudioStream>, preferredLanguage: String? = null): Int {
        val candidates = streams.map { stream ->
            AudioCandidate(stream.audioTrackId, stream.audioTrackName, stream.averageBitrate)
        }
        return pickIndex(candidates, preferredLanguage)
    }

    private fun isInLanguage(candidate: AudioCandidate, language: String?): Boolean {
        val trackId = candidate.trackId
        if (language == null || trackId == null) {
            return false
        }
        val wantedLanguage = language.substringBefore('-').lowercase()
        val candidateLanguage = trackId.substringBefore('.').substringBefore('-').lowercase()
        return wantedLanguage == candidateLanguage
    }

    /** Streams without a track name carry a single track, which is the original one. */
    private fun isOriginal(candidate: AudioCandidate): Boolean {
        val trackName = candidate.trackName?.lowercase() ?: return true
        return trackName.contains("original") || trackName.contains("default")
    }
}
