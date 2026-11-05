package com.fyiplayer.app.player

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MimeTypes
import com.fyiplayer.app.core.CaptionTrack
import com.fyiplayer.app.core.MediaFormat
import com.fyiplayer.app.core.Protocol
import java.io.File

/** [com.fyiplayer.app.core.ExtractionError.Unsupported] message prefix: the downloaded file is gone
 *  and there is no connection to stream instead. PlayerScreen shows its own line for it. */
const val DOWNLOAD_MISSING_PREFIX = "download-missing: "

/** Language of the downloaded subtitle track. Captions are selected by language, so the
 *  downloaded track needs a code no online track uses: "qaa" is ISO 639-2's reserved local-use code. */
internal const val DOWNLOADED_CAPTION_LANGUAGE = "qaa"

/** The format id marking a [MediaFormat] built from a downloaded file. */
internal const val DOWNLOADED_FORMAT_ID = "downloaded"

/**
 * A finished download as the one format of its item. Height and video presence come from the
 * file's own header (local I/O, no network): call off the main thread. Throws if the file
 * cannot be read as media.
 */
internal fun downloadedFormat(file: File): MediaFormat {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.path)
        val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
        // "720p" names the short side, so a portrait short reads the same as on YouTube.
        val shortSide = if (width != null && height != null) minOf(width, height) else height
        return MediaFormat(
            formatId = DOWNLOADED_FORMAT_ID,
            url = Uri.fromFile(file).toString(),
            container = file.extension,
            protocol = Protocol.PROGRESSIVE,
            height = if (hasVideo) shortSide else null,
            // Codec names are unknown and unused here; non-null marks which streams the file has.
            videoCodec = if (hasVideo) DOWNLOADED_FORMAT_ID else null,
            audioCodec = DOWNLOADED_FORMAT_ID,
        )
    } finally {
        retriever.release()
    }
}

/** The .srt that "Download subtitles" saved next to [videoFile] (same base name), if any. */
internal fun downloadedSubtitleOf(videoFile: File): File? =
    File(videoFile.parentFile, videoFile.nameWithoutExtension + ".srt").takeIf { it.isFile }

internal fun downloadedCaption(subtitle: File, label: String): CaptionTrack = CaptionTrack(
    url = Uri.fromFile(subtitle).toString(),
    mimeType = MimeTypes.APPLICATION_SUBRIP,
    languageCode = DOWNLOADED_CAPTION_LANGUAGE,
    label = label,
)
