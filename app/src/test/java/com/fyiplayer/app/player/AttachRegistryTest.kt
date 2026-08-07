package com.fyiplayer.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttachRegistryTest {

    @Test fun `detaching a non-owner still reports the current owner`() {
        val r = AttachRegistry<String>()
        r.attach("full", 1)
        r.attach("mini", 2)
        val owner = r.detach("full") // full wasn't on top; mini remains the real owner
        assertEquals("mini", owner?.key)
    }

    @Test fun `detaching the owner hands off to the next most recent host`() {
        val r = AttachRegistry<String>()
        r.attach("full", 1)
        r.attach("mini", 2)
        val next = r.detach("mini")
        assertEquals("full", next?.key)
        assertEquals(1, next?.value)
    }

    @Test fun `detaching the last host yields no owner`() {
        val r = AttachRegistry<String>()
        r.attach("full", 1)
        assertNull(r.detach("full"))
    }

    // Reproduces the actual bug: an outgoing host's stale recomposition re-attaches after the
    // incoming host already claimed the view, then the outgoing host's onRelease fires. The
    // registry must still resolve ownership to the host that's really left holding it.
    @Test fun `re-attach by the outgoing host then its detach still resolves to the real owner`() {
        val r = AttachRegistry<String>()
        r.attach("full", 1) // full owns it
        r.attach("mini", 2) // mini takes over
        r.attach("full", 1) // stale full update steals it back (bug trigger) -> full on top again
        val next = r.detach("full") // full's onRelease
        assertEquals("mini", next?.key)
    }

    @Test fun `re-attaching an existing host moves it to the top without duplicating`() {
        val r = AttachRegistry<String>()
        r.attach("full", 1)
        r.attach("mini", 2)
        r.attach("full", 3) // full reclaims ownership, now on top with an updated resize mode
        val afterMini = r.detach("mini") // mini wasn't on top; full remains owner
        assertEquals("full", afterMini?.key)
        assertEquals(3, afterMini?.value)
        assertNull(r.detach("full")) // registry now empty -- proves no leftover duplicate entry
    }
}
