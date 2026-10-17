package com.fyiplayer.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Design-system gate (docs/design-system.md, "Gates"). Every size and colour outside `ui/theme`
 * must come from [Spacing], [Sizes], [Radius] or the colour scheme. Fails on the literal and
 * its line so the fix is one hop away.
 */
class DesignTokensGateTest {

    private val mainSources = File("src/main/java/com/fyiplayer/app")
    private val themeDir = File(mainSources, "ui/theme")

    // Material nested units and hairlines: the only dp values allowed inline.
    private val allowedInlineDp = setOf("0", "1", "2", "4", "6", "10")

    private val dpLiteral = Regex("""(?<![\w.])(\d+(?:\.\d+)?)\.dp\b""")
    private val spLiteral = Regex("""(?<![\w.])\d+(?:\.\d+)?\.sp\b""")
    private val fontSizeOverride = Regex("""\.copy\([^)]*fontSize\s*=""")
    private val rawColour = Regex("""Color\(0x[0-9A-Fa-f]+\)|Color\.(White|Black|Red|Gray|LightGray|DarkGray|Yellow|Green|Blue|Cyan|Magenta)\b""")

    private fun uiSources(): List<File> = mainSources.walkTopDown()
        .filter { it.isFile && it.extension == "kt" && !it.startsWith(themeDir) }
        .toList()

    private fun violations(pattern: Regex, keep: (MatchResult) -> Boolean = { true }): List<String> =
        uiSources().flatMap { file ->
            file.readLines().withIndex().flatMap { (index, line) ->
                val code = line.substringBefore("//")
                pattern.findAll(code).filter(keep).map { "${file.path}:${index + 1}: ${it.value}" }.toList()
            }
        }

    @Test fun `dp literals outside the theme are the Material nested units only`() {
        val hits = violations(dpLiteral) { it.groupValues[1] !in allowedInlineDp }
        assertTrue("Raw dp outside ui/theme (use Spacing/Sizes/Radius):\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    @Test fun `no sp literal or fontSize override outside the theme`() {
        val hits = violations(spLiteral) + violations(fontSizeOverride)
        assertTrue("Type roles only, no raw sp or fontSize override:\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    @Test fun `no raw colour outside the theme`() {
        val hits = violations(rawColour)
        assertTrue("Raw colour outside ui/theme (use colorScheme or fyiExtras):\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    @Test fun `touch targets are never shrunk below 48 dp`() {
        val hits = violations(Regex("""\.size\((\d+)\.dp\)""")) { it.groupValues[1].toInt() < 48 } +
            violations(Regex("""LocalMinimumInteractiveComponentSize\s+provides\s+0"""))
        // Any `.size(N.dp)` with N < 48 that is a literal already fails the dp gate; this catches
        // the same mistake expressed through a token name on an IconButton.
        val tokenHits = violations(Regex("""IconButton\([^)]*modifier\s*=\s*Modifier\.size\(Sizes\.(icon|iconSmall|iconLarge|transportGlyph)\)"""))
        val all = hits + tokenHits
        assertTrue("Interactive element under 48 dp:\n" + all.joinToString("\n"), all.isEmpty())
    }
}
