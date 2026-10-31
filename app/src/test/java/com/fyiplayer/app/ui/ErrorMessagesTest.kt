package com.fyiplayer.app.ui

import com.fyiplayer.app.core.AccessChallengeReason
import com.fyiplayer.app.core.ExtractionError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ErrorMessagesTest {

    @Test fun `every wall reason has its own message`() {
        val messages = AccessChallengeReason.values().map { ExtractionError.AccessChallenge("x", it).userMessage() }
        assertEquals(messages.size, messages.toSet().size)
    }

    @Test fun `sign in shows only where it can help`() {
        val signIn = AccessChallengeReason.values().filter { it.signInHelps() }.toSet()
        assertEquals(
            setOf(AccessChallengeReason.BOT_CHECK, AccessChallengeReason.AGE_RESTRICTION, AccessChallengeReason.LOGIN_REQUIRED),
            signIn,
        )
    }

    @Test fun `retry shows only for rate limit and unknown`() {
        val retry = AccessChallengeReason.values().filter { it.retryHelps() }.toSet()
        assertEquals(setOf(AccessChallengeReason.RATE_LIMIT, AccessChallengeReason.UNKNOWN), retry)
    }

    @Test fun `geo and paid walls offer neither button`() {
        for (reason in listOf(AccessChallengeReason.GEO_BLOCK, AccessChallengeReason.PAID)) {
            assertFalse(reason.signInHelps())
            assertFalse(reason.retryHelps())
        }
    }
}
