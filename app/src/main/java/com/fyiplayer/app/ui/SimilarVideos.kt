package com.fyiplayer.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fyiplayer.app.core.ExtractionError
import com.fyiplayer.app.core.VideoRef

/**
 * The Similar tab (DECISIONS.md: the extraction engine exposes no related/recommended list, and
 * mix/radio playlists are rejected -- confirmed live). "Similar" is a search on the current
 * video's own topic, never the platform's own recommendation feed, so [buildSimilarQuery] is the
 * whole quality of this feature -- kept pure and unit-tested on purpose.
 */

// A handful of words beats a long, over-specific query -- search degrades past this.
private const val MAX_QUERY_WORDS = 6

// Loose on purpose: doesn't pair bracket types, just strips "(...)"/"[...]"/"{...}" noise blocks.
private val BRACKETED = Regex("""[(\[{][^)\]}]*[)\]}]""")

// Common emoji blocks. Java regex's \x{h..h} form reaches supplementary code points directly, no
// surrogate-pair juggling needed.
private val EMOJI = Regex(
    "[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2190}-\\x{21FF}\\x{2B00}-\\x{2BFF}" +
        "\\x{FE00}-\\x{FE0F}\\x{1F1E6}-\\x{1F1FF}]+",
)

private val DECORATIONS = Regex(
    """\b(official\s+music\s+video|official\s+lyric\s+video|official\s+video|official\s+audio|""" +
        """official\s+trailer|lyric\s+video|lyrics|music\s+video|full\s+video|full\s+song|""" +
        """trailer|vevo|hq|hd|4k|8k|1080p|720p|remastered|extended)\b""",
    RegexOption.IGNORE_CASE,
)

private val NUMBERING = Regex(
    """\b(ep(?:isode)?\.?\s*\d+|part\s*\d+|pt\.?\s*\d+|#\d+)\b""",
    RegexOption.IGNORE_CASE,
)

private val NON_WORD = Regex("""[^\p{L}\p{N}\s']""")
private val WHITESPACE = Regex("""\s+""")
private val STOPWORDS = setOf(
    "a", "an", "the", "of", "in", "on", "and", "ft", "feat", "featuring", "with", "vs",
    "video", "official", "new",
)

/**
 * Turns a video's title into a search query for finding similar videos. Strips what makes a bad
 * query -- bracketed noise, emoji, "Official Video"-style decoration, episode/part numbering --
 * and caps the result to [maxWords]. Degrades in stages and never returns a blank string: an
 * empty query has no defined behaviour against [com.fyiplayer.app.core.VideoSource.search].
 */
internal fun buildSimilarQuery(title: String, maxWords: Int = MAX_QUERY_WORDS): String {
    val cleaned = title
        .replace(BRACKETED, " ")
        .replace(EMOJI, " ")
        .replace(DECORATIONS, " ")
        .replace(NUMBERING, " ")
        .replace(NON_WORD, " ")
        .replace(WHITESPACE, " ")
        .trim()
    val words = cleaned.split(" ").filter { it.isNotBlank() && it.lowercase() !in STOPWORDS }.take(maxWords)
    if (words.isNotEmpty()) return words.joinToString(" ")

    // Title was pure decoration/emoji/stopwords (e.g. "(Official Video) [HD]"). Fall back to the
    // raw words before the decoration strip -- sane beats empty.
    val fallback = title.replace(NON_WORD, " ").replace(WHITESPACE, " ").trim()
        .split(" ").filter { it.isNotBlank() }.take(maxWords)
    if (fallback.isNotEmpty()) return fallback.joinToString(" ")

    // Title had no alphanumeric content at all (e.g. pure emoji). Last resort, never blank.
    return title.replace(EMOJI, " ").trim().ifBlank { "video" }
}

/** The current video is never its own "similar" result. Matched on [VideoRef.pageUrl] -- the one
 *  persisted identity (core/Contracts.kt) -- not full equality. */
internal fun excludeCurrent(results: List<VideoRef>, current: VideoRef): List<VideoRef> =
    results.filterNot { it.pageUrl == current.pageUrl }

/**
 * Emits the Similar tab directly into the caller's [LazyListScope] -- same shape the old "more
 * from this channel" section used, so it scrolls as part of the one detail page instead of
 * nesting a second scrollable list.
 */
internal fun LazyListScope.similarVideosSection(
    results: List<VideoRef>,
    loading: Boolean,
    error: ExtractionError?,
    retryEnabled: Boolean,
    onRetry: () -> Unit,
    onClick: (VideoRef) -> Unit,
    onLongPress: (VideoRef) -> Unit,
) {
    // No permanent explainer caption here: the tab label already says "Similar", and honesty
    // about what this list actually is lives in the empty/error states below, not a standing
    // banner above every result.
    when {
        loading && results.isEmpty() -> item {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
        // Access wall -> honest unavailable state, no retry affordance (never implies a workaround).
        error != null -> item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(error.userMessage(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (retryEnabled) {
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }
        results.isEmpty() -> item {
            Text(
                "No similar videos found.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        else -> items(results, key = { it.pageUrl }) { rel ->
            ResultRow(rel, onClick = { onClick(rel) }, onLongPress = { onLongPress(rel) })
        }
    }
}
