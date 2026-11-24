package com.nurseflow.app.feature.invite

import org.junit.Assert.*
import org.junit.Test

class InviteLinkTest {
    private val token = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde" // fictional, 43 chars

    @Test fun acceptsDevelopmentLinkWithFragmentToken() {
        assertEquals(43, token.length)
        assertEquals(token, parseInviteLink("nurseflow://invite#token=$token")?.value)
        assertEquals(token, parseInviteLink("  nurseflow://invite#token=$token\n")?.value)
    }

    @Test fun rejectsOtherSchemesHostsQueriesPathsAndTokens() {
        listOf(
            "https://nurseflow.test/invite#token=$token",
            "http://invite#token=$token",
            "NURSEFLOW://invite#token=$token",
            "nurseflow://other#token=$token",
            "nurseflow://invite/#token=$token",
            "nurseflow://invite/path#token=$token",
            "nurseflow://invite?token=$token",
            "nurseflow://invite?x=1#token=$token",
            "nurseflow://invite#token=$token&next=1",
            "nurseflow://invite#token=${token}x",
            "nurseflow://invite#token=${token.dropLast(1)}",
            "nurseflow://invite#token=${token.dropLast(1)}!",
            "nurseflow://invite#code=$token",
            "nurseflow://invite",
            token,
            "",
        ).forEach { assertNull(it, parseInviteLink(it)) }
    }

    @Test fun tokenNeverAppearsInStringForm() {
        val parsed = parseInviteLink("nurseflow://invite#token=$token")!!
        assertFalse(parsed.toString().contains(token))
        assertFalse(PendingInvitation(1, parsed).toString().contains(token))
    }
}
