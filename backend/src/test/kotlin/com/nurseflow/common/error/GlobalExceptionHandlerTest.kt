package com.nurseflow.common.error

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()

    @Test
    fun `business error retains public status code and revision`() {
        val response = handler.handle(BusinessException(ErrorCode.STALE_ROSTER_REVISION, currentRevision = 7))

        assertEquals(412, response.statusCode.value())
        assertEquals("application/problem+json", response.headers.contentType.toString())
        assertEquals("STALE_ROSTER_REVISION", response.body?.code)
        assertEquals(7, response.body?.currentRevision)
    }

    @Test
    fun `unexpected error exposes only safe detail`() {
        val response = handler.unexpected()

        assertEquals(500, response.statusCode.value())
        assertEquals("INTERNAL_ERROR", response.body?.code)
        assertEquals(ErrorCode.INTERNAL_ERROR.safeMessage, response.body?.detail)
    }
}
