package com.fyiplayer.app.source.newpipe

import com.fyiplayer.app.core.ExtractionError
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.AntiBotException
import org.schabi.newpipe.extractor.exceptions.NeedLoginException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * One when-chain, most specific first: several of these types extend [ContentNotAvailableException]
 * (itself a [ParsingException]), so order -- not type hierarchy -- decides the bucket. Shared by
 * every NewPipe call site in this package ([NewPipeResolver], [NewPipeYoutubeSource]) -- moved out
 * of [NewPipeResolver] once a second caller needed it.
 */
internal fun mapNewPipeError(e: Exception): ExtractionError = when (e) {
    is ReCaptchaException,
    is AgeRestrictedContentException,
    is PaidContentException,
    is GeographicRestrictionException,
    is AntiBotException, // the fork's name for the sign-in-to-confirm bot wall
    is NeedLoginException,
    -> ExtractionError.AccessChallenge("access challenge")

    is PrivateContentException,
    is ContentNotAvailableException,
    -> ExtractionError.ContentUnavailable("content unavailable")

    // The extractor throws plain IOException for malformed responses and JSON decode failures
    // too, not just transport trouble -- only a genuine transport failure (in [e] itself or its
    // cause chain, e.g. an OkHttp wrapper) is actually "no connection".
    is IOException ->
        if (isTransportFailure(e)) ExtractionError.Network("network error", e)
        else ExtractionError.Unsupported("platform changed", logged(e))

    is ParsingException,
    is ExtractionException,
    -> ExtractionError.Unsupported("platform changed", logged(e))

    else -> ExtractionError.Unsupported("unknown newpipe failure", logged(e))
}

/** Bounded cause-chain walk (8 deep, same spirit as [logged]'s 6-frame cap) so a cyclic or very
 *  deep chain can't hang this check. */
private fun isTransportFailure(e: Throwable): Boolean =
    generateSequence(e as Throwable?) { it.cause }.take(8).any {
        it is SocketTimeoutException || it is UnknownHostException || it is ConnectException || it is SSLException
    }

// Class name only -- messages can echo page URLs. Same lesson as ChainResolver's logHardStop:
// a silent Unsupported mapping cost a debugging session.
private fun logged(e: Exception): Exception {
    try {
        // Frames only, never the message -- messages can echo page URLs.
        val frames = e.stackTrace.take(6).joinToString(" | ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        android.util.Log.d("NewPipeErrors", "unsupported: ${e::class.simpleName} @ $frames")
    } catch (logError: Throwable) {
        // unmocked android.util.Log under plain JUnit
    }
    return e
}
