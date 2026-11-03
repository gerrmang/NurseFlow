package com.nurseflow.app.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class SessionModuleTest {
    @Test
    fun `replacing developer credentials starts a new opaque session and sign out clears it`() {
        val source = createSessionSource()

        source.useDeveloperToken("test-token-one")
        val first = source.activeSession.value
        assertNotNull(first)
        assertEquals("test-token-one", source.currentToken())

        source.useDeveloperToken("test-token-two")
        val second = source.activeSession.value
        assertNotNull(second)
        assertNotEquals(first!!.id, second!!.id)
        assertEquals("test-token-two", source.currentToken())

        source.signOut()
        assertNull(source.activeSession.value)
        assertNull(source.currentToken())
    }
}
