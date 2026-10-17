package com.fyiplayer.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Copy gate (docs/copy.md). Visible text lives in res/values/strings*.xml; it uses the product
 * vocabulary, never implementation words, and never shouts or asks.
 */
class CopyGateTest {

    private val mainSources = File("src/main/java/com/fyiplayer/app")
    private val resValues = File("src/main/res/values")

    // Words the user never sees: they name how the app works, not what it does for them.
    private val bannedWords = listOf(
        "yt-dlp", "engine", "resolver", "extractor", "DASH", "manifest", "session", "token",
        "cookie", "cache", "hash", "endpoint", "regex", "null", "exception", "stack trace",
        "index", "R8", "codec", "mux", "segment index", "PATH",
    ).map { Regex("""(?<![\w-])${Regex.escape(it)}(?![\w-])""", RegexOption.IGNORE_CASE) }

    private val literalToText = Regex("""(?:\bText\(\s*|\b(?:contentDescription|label|placeholder|title|text|headline|supporting)\s*=\s*|showSnackbar\(\s*)"[^"$]{2,}"""")

    @Test fun `visible strings are resources not Kotlin literals`() {
        val hits = mainSources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().withIndex().filter { (_, line) ->
                    literalToText.containsMatchIn(line.substringBefore("//"))
                }.map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
            }.toList()
        assertTrue("String literal reaches the UI (move to strings*.xml):\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    @Test fun `string resources avoid implementation words exclamation marks and dashes`() {
        val entry = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        val hits = resValues.listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }
            .orEmpty()
            .flatMap { file ->
                entry.findAll(file.readText()).mapNotNull { match ->
                    val (name, value) = match.destructured
                    val problems = buildList {
                        bannedWords.filter { it.containsMatchIn(value) }.forEach { add("implementation word ${it.pattern}") }
                        if ("!" in value) add("exclamation mark")
                        if ("—" in value || "–" in value) add("dash")
                        if (value.trim().endsWith("?")) add("question")
                    }
                    if (problems.isEmpty()) null else "${file.name}: $name = \"$value\": ${problems.joinToString()}"
                }.toList()
            }
        assertTrue("Copy rule violations:\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
