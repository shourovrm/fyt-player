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

/** One row of the audio-language picker: [trackId] is what [FormatSelector.select] takes back. */
data class AudioTrackOption(val trackId: String, val label: String)

/** The distinct audio language tracks a format list offers, in the order the platform listed them.
 *  Empty when the list has no track ids. Used as the dub picker's rows, and shown only when there
 *  is more than one row (a single-language video has nothing to switch to). */
fun audioTrackOptionsOf(formats: List<MediaFormat>): List<AudioTrackOption> =
    formats.filter { it.isAudioOnly }
        .mapNotNull { format -> format.audioTrackId?.let { id -> AudioTrackOption(id, format.audioLabel ?: id) } }
        .distinctBy { it.trackId }
        .let(::numberDuplicateLabels)

/** Two tracks can share a language name (regional variants), and a picker with two identical rows
 *  cannot be used: number the repeats ("Chinese 1", "Chinese 2"). */
private fun numberDuplicateLabels(options: List<AudioTrackOption>): List<AudioTrackOption> {
    val labelCounts = options.groupingBy { it.label }.eachCount()
    val nextNumber = mutableMapOf<String, Int>()
    return options.map { option ->
        if (labelCounts.getValue(option.label) == 1) return@map option
        val number = (nextNumber[option.label] ?: 0) + 1
        nextNumber[option.label] = number
        option.copy(label = "${option.label} $number")
    }
}

/** The track id of the audio a pair plays, or null when the selection carries no separate audio
 *  track (a muxed or manifest stream). */
fun audioTrackIdOf(selection: FormatSelection): String? = when (selection) {
    is FormatSelection.Paired -> selection.audio.audioTrackId
    is FormatSelection.Single -> null
}

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
        formats.filter { it.isVideoOnly && !it.isManifest() && it.fitsCeiling(ceiling) }.maxByOrNull { it.height ?: 0 }

    /** An explicit track wins; otherwise the original track; otherwise every track. Bitrate only
     *  ranks streams inside the chosen track. The original is the default because a bitrate-only
     *  pick can land on a dub in the wrong language. An unknown [audioTrackId] falls back to the
     *  default rather than failing playback. */
    private fun bestAudioOnly(formats: List<MediaFormat>, audioTrackId: String?): MediaFormat? {
        val audio = formats.filter { it.isAudioOnly }
        val requested = audioTrackId?.let { id -> audio.filter { it.audioTrackId == id } }.orEmpty()
        val candidates = requested.ifEmpty { audio.filter { it.isOriginalAudio }.ifEmpty { audio } }
        return candidates.maxByOrNull { it.bitrate ?: 0 }
    }

    private fun bestMuxed(formats: List<MediaFormat>, ceiling: Int) =
        formats.filter { it.isMuxed && !it.isManifest() && it.fitsCeiling(ceiling) }.maxByOrNull { it.height ?: 0 }

    private fun bestManifest(formats: List<MediaFormat>, ceiling: Int) =
        formats.filter { it.isManifest() && it.fitsCeiling(ceiling) }.maxByOrNull { it.height ?: 0 }

    fun select(
        formats: List<MediaFormat>,
        maxHeight: Int,
        audioOnly: Boolean = false,
        audioTrackId: String? = null,
    ): SelectionResult {
        if (audioOnly) {
            val audio = bestAudioOnly(formats, audioTrackId)
                ?: return SelectionResult(null, "no audio-only stream available")
            return SelectionResult(FormatSelection.Single(audio))
        }

        val video = bestVideoOnly(formats, maxHeight)
        val audio = bestAudioOnly(formats, audioTrackId)
        // A muxed stream carries only the original audio, so an explicit dub pick must use a
        // video+audio pair even when a muxed stream is taller.
        val muxed = if (audioTrackId == null) bestMuxed(formats, maxHeight) else null
        // Progressive first, manifest only as the fallback (live streams, HLS-only sources). An
        // HLS start costs master + variant playlist round trips before the first media byte and
        // bypasses ChunkedRangeDataSource; device-measured 2026-08-23: tap-to-audio 4.5s -> 3.2s
        // cold, 3.5s -> 2.2s warm. Fixed quality under the user's cap, same as PipePipe.
        if ((video == null || audio == null) && muxed == null) {
            bestManifest(formats, maxHeight)?.let { return SelectionResult(FormatSelection.Single(it)) }
        }

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
