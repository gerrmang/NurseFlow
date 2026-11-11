package com.nurseflow.app.feature.invite

import org.junit.Assert.*
import org.junit.Test

class PendingInvitationStoreTest {
    private fun token(c: Char) = InviteToken.of(c.toString().repeat(43))!!

    @Test fun sameTokenKeepsFlowAndNewTokenReplacesIt() {
        val store = PendingInvitationStore()
        val first = store.offer(token('a'))
        assertSame(first, store.offer(token('a')))
        val second = store.offer(token('b'))
        assertTrue(second.seq > first.seq)
        assertEquals(second, store.current.value)
    }

    @Test fun clearForStaleSeqDoesNotDropNewerInvitation() {
        val store = PendingInvitationStore()
        val first = store.offer(token('a'))
        val second = store.offer(token('b'))
        store.clear(first.seq)
        assertEquals(second, store.current.value)
        store.clear(second.seq)
        assertNull(store.current.value)
    }

    @Test fun invalidLinkFlagIsResetByValidLinkOrClear() {
        val store = PendingInvitationStore()
        store.reportInvalidLink()
        assertTrue(store.invalidLink.value)
        store.offer(token('a'))
        assertFalse(store.invalidLink.value)
        store.reportInvalidLink()
        store.clear()
        assertFalse(store.invalidLink.value)
        assertNull(store.current.value)
    }
}
