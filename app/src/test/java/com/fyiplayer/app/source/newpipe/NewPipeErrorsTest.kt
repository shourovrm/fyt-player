package com.fyiplayer.app.source.newpipe

import com.fyiplayer.app.core.ExtractionError
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertTrue
import org.junit.Test

class NewPipeErrorsTest {

    @Test
    fun socketTimeoutIsNetwork() {
        val e = mapNewPipeError(SocketTimeoutException("timed out"))
        assertTrue(e is ExtractionError.Network)
    }

    @Test
    fun causeChainTransportFailureIsNetwork() {
        val e = mapNewPipeError(IOException("wrapped", SocketTimeoutException("timed out")))
        assertTrue(e is ExtractionError.Network)
    }

    @Test
    fun plainIoExceptionIsNotNetwork() {
        // A malformed-response/JSON-decode IOException from inside the extractor -- must not be
        // reported as "no connection" when the network is fine.
        val e = mapNewPipeError(IOException("parse junk"))
        assertTrue(e !is ExtractionError.Network)
        assertTrue(e is ExtractionError.Unsupported)
    }
}
