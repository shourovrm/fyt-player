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
}
