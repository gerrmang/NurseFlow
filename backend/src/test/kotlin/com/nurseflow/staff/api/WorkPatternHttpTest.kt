package com.nurseflow.staff.api

import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class WorkPatternHttpTest : ApiTestBase() {
    @Test
    fun `only ward administrator may set pattern and changed drafts are invalidated`() {
        val ward = insertWard()
        val (admin, token) = userToken()
        val (member, memberToken) = userToken()
        grant(ward, admin, "WARD_ADMIN")
        grant(ward, member, "WARD_MEMBER")
        val nurse = insertNurse(ward, subject = member.toString())
        val draft = UUID.randomUUID()
        jdbc.update("INSERT INTO rosters(roster_id,ward_id,roster_month,status,active,revision) VALUES(?,?,?,'DRAFT',FALSE,1)", draft, ward, "2026-11")
        val path = "/api/v1/wards/$ward/staff/$nurse/work-pattern"
        assertEquals("GENERAL", http.get(path, token).json!!.get("workPattern").asString())
        assertEquals(403, http.put(path, mapOf("workPattern" to "NIGHT_ONLY"), memberToken).status)
        assertEquals(400, http.put(path, emptyMap<String, String>(), token).status)
        assertEquals(400, http.put(path, mapOf("workPattern" to "INVALID"), token).status)
        assertEquals(400, http.put(path, mapOf("workPattern" to 2), token).status)
        assertEquals("GENERAL", http.get(path, token).json!!.get("workPattern").asString())
        assertEquals(200, http.put(path, mapOf("workPattern" to "NIGHT_ONLY"), token).status)
        assertEquals("NIGHT_ONLY", http.get(path, token).json!!.get("workPattern").asString())
        assertEquals(2, jdbc.queryForObject("SELECT revision FROM rosters WHERE roster_id=?", Int::class.java, draft))
        assertTrue(jdbc.queryForObject("SELECT needs_revalidation FROM rosters WHERE roster_id=?", Boolean::class.java, draft)!!)
        assertEquals(200, http.put(path, mapOf("workPattern" to "NIGHT_ONLY"), token).status)
        assertEquals(2, jdbc.queryForObject("SELECT revision FROM rosters WHERE roster_id=?", Int::class.java, draft))
        val other = insertNurse(insertWard())
        assertEquals(404, http.put("/api/v1/wards/$ward/staff/$other/work-pattern", mapOf("workPattern" to "GENERAL"), token).status)
    }
}
