package com.fyiplayer.app.source.newpipe

import com.fyiplayer.app.core.ExtractionError
import java.io.IOException
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException

// Pure helpers only -- no network, no NewPipe.init.
class NewPipeResolverTest {

    @Test
    fun resolutionStringParsesLeadingHeight() {
        assertEquals(720, resolutionToHeight("720p60"))
        assertEquals(1080, resolutionToHeight("1080p"))
        assertEquals(144, resolutionToHeight("144p"))
    }

    @Test
    fun resolutionUnknownOrNullIsNull() {
        assertNull(resolutionToHeight(null))
        assertNull(resolutionToHeight("")) // VideoStream.RESOLUTION_UNKNOWN
        assertNull(resolutionToHeight("audio only"))
    }

    @Test
    fun mimeMappingCoversPlayableFormats() {
        assertEquals("text/vtt", captionMimeType(MediaFormat.VTT))
        assertEquals("application/ttml+xml", captionMimeType(MediaFormat.TTML))
        assertEquals("application/x-subrip", captionMimeType(MediaFormat.SRT))
    }

    @Test
    fun mimeMappingSkipsFormatsMedia3CannotPlay() {
        // SRV1/2/3 are XML transcript formats with no media3 decoder.
        assertNull(captionMimeType(MediaFormat.TRANSCRIPT1))
        assertNull(captionMimeType(MediaFormat.TRANSCRIPT3))
        assertNull(captionMimeType(null))
    }

    @Test
    fun accessChallengeExceptionsMapToAccessChallenge() {
        assertTrue(mapNewPipeError(ReCaptchaException("wall", "u")) is ExtractionError.AccessChallenge)
        assertTrue(mapNewPipeError(AgeRestrictedContentException("wall")) is ExtractionError.AccessChallenge)
        assertTrue(mapNewPipeError(PaidContentException("wall")) is ExtractionError.AccessChallenge)
        assertTrue(mapNewPipeError(GeographicRestrictionException("wall")) is ExtractionError.AccessChallenge)
        assertTrue(mapNewPipeError(SignInConfirmNotBotException("bot check")) is ExtractionError.AccessChallenge)
    }

    @Test
    fun contentGoneExceptionsMapToContentUnavailable() {
        assertTrue(mapNewPipeError(PrivateContentException("gone")) is ExtractionError.ContentUnavailable)
        assertTrue(mapNewPipeError(ContentNotAvailableException("gone")) is ExtractionError.ContentUnavailable)
    }

    @Test
    fun ioExceptionMapsToNetwork() {
        assertTrue(mapNewPipeError(IOException("timeout")) is ExtractionError.Network)
    }

    @Test
    fun parsingFailuresMapToUnsupported() {
        assertTrue(mapNewPipeError(ParsingException("layout changed")) is ExtractionError.Unsupported)
        assertTrue(mapNewPipeError(ExtractionException("layout changed")) is ExtractionError.Unsupported)
    }

    @Test
    fun handlesOnlyWatchAndShortsPages() {
        val resolver = NewPipeResolver(OkHttpClient())
        assertTrue(resolver.handles("https://www.youtube.com/watch?v=abc123"))
        assertTrue(resolver.handles("https://youtu.be/abc123"))
        assertTrue(resolver.handles("https://m.youtube.com/shorts/abc123"))
        assertFalse(resolver.handles("https://www.youtube.com/channel/UCabc/videos"))
        assertFalse(resolver.handles("https://vimeo.com/123456"))
        assertFalse(resolver.handles("not a url"))
    }
}
