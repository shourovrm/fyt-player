package com.fyiplayer.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Pure text -> DownloadProgress parsing, no Android, no network.
class ProgressParsingTest {

    @Test
    fun parsesNormalProgressLine() {
        val line = """{"downloaded":5000000,"total":10000000,"eta":30,"speed":123456.5}"""
        val progress = parseProgressLine(line)

        assertEquals(5_000_000L, progress?.downloadedBytes)
        assertEquals(10_000_000L, progress?.totalBytes)
        assertEquals(50f, progress?.percent)
        assertEquals(30L, progress?.etaSeconds)
        assertEquals(123456.5, progress?.speedBytesPerSecond)
    }

    @Test
    fun missingTotalLeavesPercentNullButKeepsDownloaded() {
        val line = """{"downloaded":2048,"total":null,"eta":null,"speed":null}"""
        val progress = parseProgressLine(line)

        assertEquals(2048L, progress?.downloadedBytes)
        assertNull(progress?.totalBytes)
        assertNull(progress?.percent)
        assertNull(progress?.etaSeconds)
        assertNull(progress?.speedBytesPerSecond)
    }

    @Test
    fun unknownFieldsInLineAreIgnoredNotFatal() {
        val line = """{"downloaded":10,"total":100,"eta":1,"speed":1.0,"filename":"secret.mp4"}"""
        val progress = parseProgressLine(line)

        assertEquals(10L, progress?.downloadedBytes)
        assertEquals(10f, progress?.percent)
    }

    @Test
    fun completionLineReports100Percent() {
        val line = """{"downloaded":10000000,"total":10000000,"eta":0,"speed":0.0}"""
        val progress = parseProgressLine(line)

        assertEquals(100f, progress?.percent)
        assertEquals(10_000_000L, progress?.downloadedBytes)
    }

    @Test
    fun zeroTotalIsTreatedAsUnknownNotDivisionByZero() {
        val line = """{"downloaded":500,"total":0,"eta":null,"speed":null}"""
        val progress = parseProgressLine(line)

        assertNull(progress?.totalBytes)
        assertNull(progress?.percent)
    }

    @Test
    fun ordinaryEngineChatterLineIsNotProgress() {
        assertNull(parseProgressLine("[youtube] Extracting URL: https://example.invalid/watch"))
    }

    @Test
    fun blankLineIsNotProgress() {
        assertNull(parseProgressLine(""))
        assertNull(parseProgressLine("   "))
    }

    @Test
    fun malformedJsonDoesNotThrow() {
        assertNull(parseProgressLine("{not valid json at all}"))
        assertNull(parseProgressLine("{\"downloaded\": }"))
        assertNull(parseProgressLine("{"))
    }

    @Test
    fun missingDownloadedFieldYieldsNull() {
        // downloaded is the one field this parser treats as mandatory -- no meaningful progress
        // without it, so the whole line is discarded rather than reporting bytes=0.
        assertNull(parseProgressLine("""{"total":100,"eta":1,"speed":1.0}"""))
    }

    @Test
    fun percentMathsRoundsToNearestWholePercentFriendlyValue() {
        val progress = parseProgressLine("""{"downloaded":1,"total":3,"eta":null,"speed":null}""")
        assertEquals(33.333332f, progress?.percent!!, 0.001f)
    }

    // The engine writes a bare, non-JSON `NA` for eta/speed on the first tick of every download --
    // NA_TOKEN rewrites `: NA` (before a comma or closing brace) to `: null` ahead of decoding.

    @Test
    fun bareNaTokensOnFirstTickParseAsNullEtaAndSpeed() {
        val line = """{"downloaded":1000,"total":5000,"eta":NA,"speed":NA}"""
        val progress = parseProgressLine(line)

        assertEquals(1000L, progress?.downloadedBytes)
        assertEquals(5000L, progress?.totalBytes)
        assertNull(progress?.etaSeconds)
        assertNull(progress?.speedBytesPerSecond)
    }

    @Test
    fun realNumbersAfterFirstTickStillParseAlongsideNaHandling() {
        val line = """{"downloaded":2000,"total":8000,"eta":12,"speed":45678.9}"""
        val progress = parseProgressLine(line)

        assertEquals(12L, progress?.etaSeconds)
        assertEquals(45678.9, progress?.speedBytesPerSecond)
    }

    @Test
    fun naSubstringInsideAQuotedValueIsNotCorrupted() {
        // NA_TOKEN only matches a bare `NA` value immediately after a colon -- a quoted string
        // that happens to contain the letters "NA" (e.g. echoing part of a title) must decode
        // untouched, not get mangled into invalid JSON by the same regex.
        val line = """{"downloaded":10,"total":20,"eta":1,"speed":1.0,"info":"NASA rover landing NA"}"""
        val progress = parseProgressLine(line)

        assertEquals(10L, progress?.downloadedBytes)
        assertEquals(1L, progress?.etaSeconds)
        assertEquals(1.0, progress?.speedBytesPerSecond)
    }

    @Test
    fun malformedLineWithNaTokensStillReturnsNullNotThrows() {
        // "NA_BROKEN" isn't a bare NA value (nothing after it is a comma or brace), so NA_TOKEN
        // leaves it alone and the line stays invalid JSON -- must fail closed, not throw.
        assertNull(parseProgressLine("""{"downloaded":NA_BROKEN,"eta":NA}"""))
    }
}
