package com.nurseflow.app.feature.invite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteCodeTest {
    private val code = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde"

    @Test
    fun `accepts a raw code, a link and a share message`() {
        assertEquals(43, code.length)
        assertEquals(code, InviteCode.extract("  $code\n"))
        assertEquals(code, InviteCode.extract("http://localhost:5173/invite#token=$code"))
        assertEquals(code, InviteCode.extract("https://x.test/invite?token=$code&utm=1"))
        assertEquals(code, InviteCode.extract("NurseFlow 병동 초대\nhttp://localhost:5173/invite#token=$code\n앱에 붙여 넣어 주세요"))
    }

    @Test
    fun `rejects short, long and ambiguous input`() {
        assertNull(InviteCode.extract("short"))
        assertNull(InviteCode.extract(code + "X"))
        assertNull(InviteCode.extract("$code ${code.reversed()}"))
    }
}
