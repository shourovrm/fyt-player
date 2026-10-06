package com.fyiplayer.app.source.newpipe

import com.fyiplayer.app.core.Chapter
import org.schabi.newpipe.extractor.stream.StreamSegment

/** The extractor's segments as chapters: untitled or negatively-timed entries are dropped and the
 *  rest sorted by start, so the list the UI shows is always in timeline order. */
internal fun toChapters(segments: List<StreamSegment>?): List<Chapter> =
    segments.orEmpty()
        .filter { !it.title.isNullOrBlank() && it.startTimeSeconds >= 0 }
        .map { Chapter(title = it.title.trim(), startSeconds = it.startTimeSeconds) }
        .sortedBy { it.startSeconds }
