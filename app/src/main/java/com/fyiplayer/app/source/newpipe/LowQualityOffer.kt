package com.fyiplayer.app.source.newpipe

import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Spots an extraction that "succeeded" with nothing better than 360p. YouTube answers VisionOS
 * with "This video is not available" for made-for-kids videos, and the extractor then quietly
 * keeps the Android client's single muxed 360p stream (itag 18) instead of failing, while mweb
 * (SABR) still offers up to 1080p for the same video.
 */
internal object LowQualityOffer {
    private const val LOW_QUALITY_MAX_HEIGHT = 360

    fun isLowQualityOnly(hasMasterPlaylist: Boolean, videoHeights: List<Int>): Boolean {
        if (hasMasterPlaylist || videoHeights.isEmpty()) return false
        return videoHeights.max() <= LOW_QUALITY_MAX_HEIGHT
    }

    fun isLowQualityOnly(streamInfo: StreamInfo): Boolean {
        val heights = (streamInfo.videoStreams.orEmpty() + streamInfo.videoOnlyStreams.orEmpty())
            .map { stream -> stream.height }
        return isLowQualityOnly(hasMasterPlaylist = !streamInfo.hlsUrl.isNullOrEmpty(), videoHeights = heights)
    }
}
