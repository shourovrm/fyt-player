package com.fyiplayer.app.source.newpipe

import com.fyiplayer.app.core.AccessChallengeReason
import com.fyiplayer.app.core.ExtractionError
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.ParsingException

// Runs on runBlocking, not runTest: kotlinx-coroutines-test is not a dependency here. The delay
// is injected and recorded, so the waits are asserted without sleeping.
class TransportRetryTest {

    private fun recordingDelay(): Pair<MutableList<Long>, suspend (Long) -> Unit> {
        val recorded = mutableListOf<Long>()
        val delayBefore: suspend (Long) -> Unit = { millis -> recorded += millis }
        return recorded to delayBefore
    }

    @Test
    fun transportFailureIsRetriedThenSucceeds() = runBlocking {
        val (delays, delayBefore) = recordingDelay()
        var attempts = 0
        val result = withTransportRetry(TRANSPORT_RETRY_DELAYS_MILLIS, delayBefore) {
            attempts++
            if (attempts == 1) throw ExtractionError.Network("blip")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, attempts)
        assertEquals(listOf(1_000L), delays)
    }

    @Test
    fun accessChallengeIsNeverRetried() = runBlocking {
        val (delays, delayBefore) = recordingDelay()
        var attempts = 0
        assertThrows(ExtractionError.AccessChallenge::class.java) {
            runBlocking {
                withTransportRetry(TRANSPORT_RETRY_DELAYS_MILLIS, delayBefore) {
                    attempts++
                    throw ExtractionError.AccessChallenge("wall", AccessChallengeReason.RATE_LIMIT)
                }
            }
        }
        assertEquals(1, attempts)
        assertTrue(delays.isEmpty())
    }

    @Test
    fun givesUpAfterThreeAttemptsWithOneAndTwoSecondWaits() = runBlocking {
        val (delays, delayBefore) = recordingDelay()
        var attempts = 0
        assertThrows(ExtractionError.Network::class.java) {
            runBlocking {
                withTransportRetry(TRANSPORT_RETRY_DELAYS_MILLIS, delayBefore) {
                    attempts++
                    throw ExtractionError.Network("still down")
                }
            }
        }
        assertEquals(3, attempts)
        assertEquals(listOf(1_000L, 2_000L), delays)
    }

    @Test
    fun cancellationPropagatesWithoutRetry() = runBlocking {
        val (delays, delayBefore) = recordingDelay()
        var attempts = 0
        assertThrows(CancellationException::class.java) {
            runBlocking {
                withTransportRetry(TRANSPORT_RETRY_DELAYS_MILLIS, delayBefore) {
                    attempts++
                    throw CancellationException("cancelled")
                }
            }
        }
        assertEquals(1, attempts)
        assertTrue(delays.isEmpty())
    }

    @Test
    fun socketTimeoutFromExtractorIsRetriedThroughGuarded() = runBlocking {
        val (delays, delayBefore) = recordingDelay()
        var attempts = 0
        val thrown = assertThrows(ExtractionError.Network::class.java) {
            runBlocking {
                withTransportRetry(TRANSPORT_RETRY_DELAYS_MILLIS, delayBefore) {
                    guarded {
                        attempts++
                        throw SocketTimeoutException("timed out")
                    }
                }
            }
        }
        assertEquals(3, attempts)
        assertEquals(2, delays.size)
        assertTrue(thrown.cause is SocketTimeoutException)
    }

    @Test
    fun parseFailureIsNotRetried() = runBlocking {
        val (delays, delayBefore) = recordingDelay()
        var attempts = 0
        assertThrows(ExtractionError.Unsupported::class.java) {
            runBlocking {
                withTransportRetry(TRANSPORT_RETRY_DELAYS_MILLIS, delayBefore) {
                    guarded {
                        attempts++
                        throw ParsingException("layout changed")
                    }
                }
            }
        }
        assertEquals(1, attempts)
        assertTrue(delays.isEmpty())
    }
}
