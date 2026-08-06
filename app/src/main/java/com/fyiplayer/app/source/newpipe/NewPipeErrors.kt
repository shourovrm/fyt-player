package com.fyiplayer.app.source.newpipe

import com.fyiplayer.app.core.ExtractionError
import java.io.IOException
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException

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
    is SignInConfirmNotBotException,
    -> ExtractionError.AccessChallenge("access challenge")

    is PrivateContentException,
    is ContentNotAvailableException,
    -> ExtractionError.ContentUnavailable("content unavailable")

    is IOException -> ExtractionError.Network("network error", e)

    is ParsingException,
    is ExtractionException,
    -> ExtractionError.Unsupported("platform changed", e)

    else -> ExtractionError.Unsupported("unknown newpipe failure", e)
}
