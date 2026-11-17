package com.nurseflow.roster.api

import com.nurseflow.roster.persistence.repository.RosterJdbcRepository
import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

class RosterEligibilityHttpTest : ApiTestBase() {
    @Autowired lateinit var roster: RosterJdbcRepository

    @Test
    fun `only active joined members are rostered by default and demo mode explicitly includes fictional staff`() {
        val ward = insertWard()
        val (admin, adminToken) = userToken("가상 관리자")
        val (member, memberToken) = userToken("가상 간호사")
        val outsider = userToken("가상 외부인")
        grant(ward, admin, "WARD_ADMIN")
        grant(ward, member, "WARD_MEMBER")
        val linked = insertNurse(ward, "가입 간호사", member.toString())
        val fictional = insertNurse(ward, "미가입 가상 간호사")
        val inactive = insertNurse(ward, "비활성 간호사", active = false)
        val legacyAdmin = insertNurse(ward, "관리자 기록", admin.toString())
        val path = "/api/v1/wards/$ward/roster-eligibility"

        val initial = http.get(path, adminToken)
        assertEquals(200, initial.status, initial.body)
        assertFalse(initial.json!!.get("demoMode").asBoolean())
        assertEquals("\"policy-0\"", initial.header("ETag"))
        val byId = initial.json.get("items").toList().associateBy { it.get("nurseId").asString() }
        assertEquals("ELIGIBLE", byId.getValue(linked.toString()).get("reason").asString())
        assertEquals("DEMO_MODE_DISABLED", byId.getValue(fictional.toString()).get("reason").asString())
        assertEquals("STAFF_INACTIVE", byId.getValue(inactive.toString()).get("reason").asString())
        assertEquals("ADMIN_EXCLUDED", byId.getValue(legacyAdmin.toString()).get("reason").asString())
        assertEquals(setOf(linked), roster.activeNurses(ward).map { it.id }.toSet())
        assertFalse(roster.findNurse(fictional, ward)!!.active)

        val draft = UUID.randomUUID()
        jdbc.update("INSERT INTO rosters(roster_id,ward_id,roster_month,status,active,revision) VALUES(?,?,?,'DRAFT',FALSE,1)", draft, ward, "2026-11")
        val enabled = http.put(path, mapOf("demoMode" to true), adminToken, mapOf("If-Match" to "\"policy-0\""))
        assertEquals(200, enabled.status, enabled.body)
        assertEquals("\"policy-1\"", enabled.header("ETag"))
        assertEquals(setOf(linked, fictional), roster.activeNurses(ward).map { it.id }.toSet())
        assertEquals(2, jdbc.queryForObject("SELECT revision FROM rosters WHERE roster_id=?", Int::class.java, draft))
        assertTrue(jdbc.queryForObject("SELECT needs_revalidation FROM rosters WHERE roster_id=?", Boolean::class.java, draft)!!)
        assertEquals(412, http.put(path, mapOf("demoMode" to false), adminToken, mapOf("If-Match" to "\"policy-0\"")).status)
        assertEquals(403, http.get(path, memberToken).status)
        assertEquals(404, http.get(path, outsider.second).status)
        assertEquals(401, http.get(path).status)
    }

    @Test
    fun `deactivated linked member stays excluded even when its nurse record is active`() {
        val ward = insertWard()
        val (admin, token) = userToken("가상 관리자")
        val (member, _) = userToken("가상 간호사")
        grant(ward, admin, "WARD_ADMIN")
        grant(ward, member, "WARD_MEMBER")
        val nurse = insertNurse(ward, subject = member.toString())
        jdbc.update("UPDATE ward_memberships SET active=FALSE WHERE ward_id=? AND subject=?", ward, member.toString())

        assertEquals(0, roster.activeNurseCount(ward))
        assertFalse(roster.findNurse(nurse, ward)!!.active)
        val items = http.get("/api/v1/wards/$ward/roster-eligibility", token).json!!.get("items").toList()
        assertEquals("MEMBERSHIP_INACTIVE_OR_MISSING", items.single().get("reason").asString())
    }
}
