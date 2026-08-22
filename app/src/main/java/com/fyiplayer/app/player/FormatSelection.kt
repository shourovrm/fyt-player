package com.fyiplayer.app.player

import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.Protocol

/** What to hand the player: one self-contained format, or a video+audio pair to merge. */
sealed class FormatSelection {
    data class Single(val format: MediaFormat) : FormatSelection()
    data class Paired(val video: MediaFormat, val audio: MediaFormat) : FormatSelection()
}

/** [selection] is null exactly when [reason] explains why nothing was selectable. */
data class SelectionResult(val selection: FormatSelection?, val reason: String? = null)

/**
 * Picks what to play from a resolved format list. Pure, no Android imports: the extraction
 * platform serves high-resolution video as separate video-only + audio-only streams, with muxed
 * formats capped low, so falling back to "best muxed" alone is a visible quality regression, not
 * an acceptable simplification.
 */
object FormatSelector {
    private fun MediaFormat.isManifest() = protocol == Protocol.HLS || protocol == Protocol.DASH
    private fun MediaFormat.fitsCeiling(ceiling: Int) = height == null || height <= ceiling

    private fun bestVideoOnly(formats: List<MediaFormat>, ceiling: Int) =
        formats.filter { it.isVideoOnly && it.fitsCeiling(ceiling) }.maxByOrNull { it.height ?: 0 }

    private fun bestAudioOnly(formats: List<MediaFormat>) =
        formats.filter { it.isAudioOnly }.maxByOrNull { it.bitrate ?: 0 }

    private fun bestMuxed(formats: List<MediaFormat>, ceiling: Int) =
        formats.filter { it.isMuxed && it.fitsCeiling(ceiling) }.maxByOrNull { it.height ?: 0 }

    private fun bestManifest(formats: List<MediaFormat>, ceiling: Int) =
        formats.filter { it.isManifest() && it.fitsCeiling(ceiling) }.maxByOrNull { it.height ?: 0 }

    fun select(formats: List<MediaFormat>, maxHeight: Int, audioOnly: Boolean = false): SelectionResult {
        if (audioOnly) {
            val audio = bestAudioOnly(formats)
                ?: return SelectionResult(null, "no audio-only stream available")
            return SelectionResult(FormatSelection.Single(audio))
        }

        // A manifest carries its own adaptive ladder; ExoPlayer's own track selection inside it
        // beats us hand-pairing two progressive streams, so it wins outright when it fits.
        bestManifest(formats, maxHeight)?.let { return SelectionResult(FormatSelection.Single(it)) }

        val video = bestVideoOnly(formats, maxHeight)
        val audio = bestAudioOnly(formats)
        val muxed = bestMuxed(formats, maxHeight)

        val pairedHeight = if (video != null && audio != null) video.height ?: 0 else -1
        // A muxed stream with no published height (Facebook sd/hd) is still a stream: absent
        // height ranks it lowest, it must not read as "no muxed stream at all".
        val muxedHeight = if (muxed == null) -1 else muxed.height ?: 0

        return when {
            pairedHeight < 0 && muxedHeight < 0 ->
                SelectionResult(null, "no playable format at or under ${maxHeight}p")
            pairedHeight >= muxedHeight && video != null && audio != null ->
                SelectionResult(FormatSelection.Paired(video, audio))
            muxed != null -> SelectionResult(FormatSelection.Single(muxed))
            else -> SelectionResult(null, "no playable format at or under ${maxHeight}p")
        }
    }
}
