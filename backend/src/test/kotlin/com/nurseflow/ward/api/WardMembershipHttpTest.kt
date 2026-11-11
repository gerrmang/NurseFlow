package com.nurseflow.ward.api

import com.nurseflow.roster.persistence.repository.RosterJdbcRepository
import com.nurseflow.support.ApiTestBase
import com.nurseflow.support.HttpTestClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Member administration (A6). All accounts, names and wards are fictional. */
class WardMembershipHttpTest : ApiTestBase() {
    @Autowired lateinit var rosterRepo: RosterJdbcRepository

    private class Setup(val ward: UUID, val admin: Pair<UUID, String>, val member: Pair<UUID, String>)

    private fun setup(): Setup {
        val ward = insertWard()
        val admin = userToken("가상 관리자")
        val member = userToken("가상 간호사")
        grant(ward, admin.first, "WARD_ADMIN")
        grant(ward, member.first, "WARD_MEMBER")
        return Setup(ward, admin, member)
    }

    private fun patch(ward: UUID, user: UUID, token: String?, body: Map<String, Any?>) =
        http.send("PATCH", "/api/v1/wards/$ward/memberships/$user", body, token, emptyMap())

    private fun members(ward: UUID, token: String?) = http.get("/api/v1/wards/$ward/members", token)

    private fun activeAdmins(ward: UUID) = jdbc.queryForObject(
        "SELECT COUNT(DISTINCT subject) FROM ward_memberships WHERE ward_id=? AND role='WARD_ADMIN' AND active=TRUE",
        Int::class.java, ward,
    )!!

    @Test
    fun `permission table for list and update`() {
        val s = setup()
        val outsider = userToken("가상 외부인")
        val target = s.member.first

        val list = members(s.ward, s.admin.second)
        assertEquals(200, list.status, list.body)
        assertEquals("private, no-store", list.header("Cache-Control"))
        assertEquals(200, patch(s.ward, target, s.admin.second, mapOf("admin" to false)).status)

        assertEquals(403, members(s.ward, s.member.second).status)
        assertEquals("WARD_ADMIN_REQUIRED", members(s.ward, s.member.second).code)
        assertEquals(403, patch(s.ward, target, s.member.second, mapOf("active" to false)).status)
        assertEquals(404, members(s.ward, outsider.second).status)
        assertEquals("WARD_NOT_FOUND", patch(s.ward, target, outsider.second, mapOf("active" to false)).code)
        assertEquals(401, members(s.ward, null).status)
        assertEquals(401, patch(s.ward, target, null, mapOf("active" to false)).status)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='MEMBER_DEACTIVATED'", Int::class.java))
    }

    @Test
    fun `list shows roles activity calendar access and the linked nurse`() {
        val s = setup()
        val nurse = insertNurse(s.ward, "가상 간호사", subject = s.member.first.toString())
        jdbc.update("UPDATE ward_memberships SET can_view_ward_calendar=FALSE WHERE subject=?", s.member.first.toString())

        val items = members(s.ward, s.admin.second).json!!.get("items").toList()

        assertEquals(listOf("가상 간호사", "가상 관리자"), items.map { it.get("displayName").asString() })
        val m = items[0]
        assertEquals(s.member.first.toString(), m.get("userId").asString())
        assertEquals(listOf("WARD_MEMBER"), m.get("roles").toList().map { it.asString() })
        assertTrue(m.get("active").asBoolean())
        assertFalse(m.get("canViewWardCalendar").asBoolean())
        assertEquals(nurse.toString(), m.get("nurseId").asString())
        assertTrue(items[1].get("nurseId").isNull)
    }

    @Test
    fun `admin role is granted next to the member role and revoked again`() {
        val s = setup()

        val granted = patch(s.ward, s.member.first, s.admin.second, mapOf("admin" to true))
        assertEquals(200, granted.status, granted.body)
        assertEquals(listOf("WARD_ADMIN", "WARD_MEMBER"), granted.json!!.get("roles").toList().map { it.asString() }.sorted())
        assertEquals(200, members(s.ward, s.member.second).status)
        // Same request again changes nothing and records nothing more.
        assertEquals(200, patch(s.ward, s.member.first, s.admin.second, mapOf("admin" to true)).status)

        val revoked = patch(s.ward, s.member.first, s.admin.second, mapOf("admin" to false))
        assertEquals(listOf("WARD_MEMBER"), revoked.json!!.get("roles").toList().map { it.asString() })
        assertEquals(403, members(s.ward, s.member.second).status)
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='MEMBER_ADMIN_GRANTED'", Int::class.java))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='MEMBER_ADMIN_REVOKED'", Int::class.java))
    }

    @Test
    fun `revoking the admin role of an admin-only account is refused and the body needs a field`() {
        val s = setup()
        val second = userToken("가상 부관리자")
        grant(s.ward, second.first, "WARD_ADMIN")

        val refused = patch(s.ward, second.first, s.admin.second, mapOf("admin" to false))
        assertEquals(409, refused.status)
        assertEquals("MEMBER_ROLE_REQUIRED", refused.code)
        assertEquals(400, patch(s.ward, second.first, s.admin.second, emptyMap()).status)
        assertEquals("INVALID_REQUEST", patch(s.ward, second.first, s.admin.second, emptyMap()).code)
        assertEquals(404, patch(s.ward, UUID.randomUUID(), s.admin.second, mapOf("active" to false)).status)
        assertEquals("MEMBER_NOT_FOUND", patch(s.ward, UUID.randomUUID(), s.admin.second, mapOf("active" to false)).code)
    }

    @Test
    fun `the last active admin cannot be revoked or deactivated but one of two can`() {
        val s = setup()
        grant(s.ward, s.admin.first, "WARD_MEMBER") // admin who is also a member, so revoking is structurally allowed

        val revoke = patch(s.ward, s.admin.first, s.admin.second, mapOf("admin" to false))
        assertEquals(409, revoke.status)
        assertEquals("LAST_WARD_ADMIN", revoke.code)
        val deactivate = patch(s.ward, s.admin.first, s.admin.second, mapOf("active" to false))
        assertEquals("LAST_WARD_ADMIN", deactivate.code)
        assertEquals("LAST_WARD_ADMIN", patch(s.ward, s.admin.first, s.admin.second, mapOf("admin" to false, "active" to false)).code)
        assertEquals(1, activeAdmins(s.ward))

        // A deactivated second admin does not count as a backup.
        val backup = userToken("가상 부관리자")
        grant(s.ward, backup.first, "WARD_ADMIN")
        assertEquals(200, patch(s.ward, backup.first, s.admin.second, mapOf("active" to false)).status)
        assertEquals("LAST_WARD_ADMIN", patch(s.ward, s.admin.first, s.admin.second, mapOf("active" to false)).code)
        assertEquals(200, patch(s.ward, backup.first, s.admin.second, mapOf("active" to true)).status)

        val done = patch(s.ward, s.admin.first, s.admin.second, mapOf("admin" to false))
        assertEquals(200, done.status, done.body)
        assertEquals(1, activeAdmins(s.ward))
    }

    @Test
    fun `a deactivated member loses access at once and its nurse record leaves rostering`() {
        val s = setup()
        val nurse = insertNurse(s.ward, subject = s.member.first.toString())
        val teams = "/api/v1/wards/${s.ward}/teams"
        assertEquals(200, http.get(teams, s.member.second).status)

        val off = patch(s.ward, s.member.first, s.admin.second, mapOf("active" to false))
        assertEquals(200, off.status, off.body)
        assertFalse(off.json!!.get("active").asBoolean())

        assertEquals(404, http.get(teams, s.member.second).status)
        assertEquals("WARD_NOT_FOUND", http.get(teams, s.member.second).code)
        assertEquals(0, http.get("/api/v1/wards", s.member.second).json!!.get("items").size())
        assertEquals(0, http.get("/api/v1/me", s.member.second).json!!.get("memberships").size())
        assertFalse(jdbc.queryForObject("SELECT active FROM nurses WHERE nurse_id=?", Boolean::class.java, nurse)!!)
        // Still listed for the admin, marked inactive.
        val listed = members(s.ward, s.admin.second).json!!.get("items").toList().first { it.get("userId").asString() == s.member.first.toString() }
        assertFalse(listed.get("active").asBoolean())

        val on = patch(s.ward, s.member.first, s.admin.second, mapOf("active" to true))
        assertTrue(on.json!!.get("active").asBoolean())
        assertEquals(200, http.get(teams, s.member.second).status)
        assertEquals(1, http.get("/api/v1/wards", s.member.second).json!!.get("items").size())
        // Reactivating the member leaves the nurse record to an explicit staff action.
        assertFalse(jdbc.queryForObject("SELECT active FROM nurses WHERE nurse_id=?", Boolean::class.java, nurse)!!)
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='MEMBER_DEACTIVATED'", Int::class.java))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='MEMBER_REACTIVATED'", Int::class.java))
    }

    @Test
    fun `deactivating a member takes its nurse out of the team order without leaving a gap`() {
        val s = setup()
        val staff = "/api/v1/wards/${s.ward}"
        val team = http.post("$staff/teams", mapOf("name" to "가상 A팀"), s.admin.second).json!!.get("teamId").asString()
        val first = insertNurse(s.ward, "가상 간호사 1")
        val linked = insertNurse(s.ward, "가상 간호사 2", subject = s.member.first.toString())
        val third = insertNurse(s.ward, "가상 간호사 3")
        listOf(first, linked, third).forEach { http.put("$staff/staff/$it/team", mapOf("teamId" to team), s.admin.second) }
        assertEquals(200, http.put("$staff/teams/$team/order", mapOf("nurseIds" to listOf(first, linked, third).map { it.toString() }), s.admin.second).status)

        assertEquals(200, patch(s.ward, s.member.first, s.admin.second, mapOf("active" to false)).status)

        val order = { id: UUID -> jdbc.queryForObject("SELECT team_order FROM nurses WHERE nurse_id=?", Int::class.javaObjectType, id) }
        assertEquals(null, order(linked))
        assertEquals(1, order(first))
        assertEquals(2, order(third))
        val history = http.get("$staff/staff/$third/adjustments", s.admin.second).json!!.get("items").toList()
        assertEquals("3", history.first { it.get("kind").asString() == "TEAM_ORDER" }.get("before").asString())
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='STAFF_DEACTIVATED'", Int::class.java))
    }

    @Test
    fun `an account with an active nurse record cannot become admin and an admin's nurse record stays off the roster`() {
        val s = setup()
        val nurse = insertNurse(s.ward, "가상 간호사", subject = s.member.first.toString())
        val staff = "/api/v1/wards/${s.ward}/staff/$nurse"

        val refused = patch(s.ward, s.member.first, s.admin.second, mapOf("admin" to true))
        assertEquals(409, refused.status, refused.body)
        assertEquals("ADMIN_NURSE_RECORD_CONFLICT", refused.code)
        assertEquals(setOf("WARD_MEMBER"), roles(s.ward, s.member.first))

        // Taking the nurse off the roster first allows the promotion.
        assertEquals(200, http.put(staff, mapOf("displayName" to "가상 간호사", "active" to false), s.admin.second).status)
        assertEquals(200, patch(s.ward, s.member.first, s.admin.second, mapOf("admin" to true)).status)

        // An admin's nurse record cannot be put back on the roster.
        val reactivate = http.put(staff, mapOf("displayName" to "가상 간호사", "active" to true), s.admin.second)
        assertEquals(409, reactivate.status, reactivate.body)
        assertEquals("ADMIN_NURSE_RECORD_CONFLICT", reactivate.code)
        assertFalse(jdbc.queryForObject("SELECT active FROM nurses WHERE nurse_id=?", Boolean::class.java, nurse)!!)

        // Back to a plain member, the record can be reactivated again.
        assertEquals(200, patch(s.ward, s.member.first, s.admin.second, mapOf("admin" to false)).status)
        assertEquals(200, http.put(staff, mapOf("displayName" to "가상 간호사", "active" to true), s.admin.second).status)
    }

    @Test
    fun `granting admin while inactive and reactivating later cannot leave an active admin with an active nurse`() {
        val s = setup()
        val nurse = insertNurse(s.ward, "가상 간호사", subject = s.member.first.toString())
        val staff = "/api/v1/wards/${s.ward}/staff/$nurse"
        assertEquals(200, patch(s.ward, s.member.first, s.admin.second, mapOf("active" to false)).status)
        // The membership is inactive, but its admin role would come back on reactivation.
        assertEquals(200, patch(s.ward, s.member.first, s.admin.second, mapOf("admin" to true, "active" to false)).status)
        assertEquals("ADMIN_NURSE_RECORD_CONFLICT",
            http.put(staff, mapOf("displayName" to "가상 간호사", "active" to true), s.admin.second).code)

        // Data from before the rules: the record is active anyway. Reactivating the admin is refused…
        jdbc.update("UPDATE nurses SET active=TRUE WHERE nurse_id=?", nurse)
        val reactivate = patch(s.ward, s.member.first, s.admin.second, mapOf("active" to true))
        assertEquals(409, reactivate.status, reactivate.body)
        assertEquals("ADMIN_NURSE_RECORD_CONFLICT", reactivate.code)
    }

    @Test
    fun `an admin's active nurse record from before the rules is never rostered`() {
        val s = setup()
        val nurse = insertNurse(s.ward, "가상 간호사", subject = s.member.first.toString())
        grant(s.ward, s.member.first, "WARD_ADMIN") // legacy state the API no longer produces
        assertTrue(jdbc.queryForObject("SELECT active FROM nurses WHERE nurse_id=?", Boolean::class.java, nurse)!!)
        assertEquals(0, rosterRepo.activeNurses(s.ward).size)
        assertEquals(0, rosterRepo.activeNurseCount(s.ward))
        assertFalse(rosterRepo.findNurse(nurse, s.ward)!!.active)
    }

    @Test
    fun `a deactivated member's nurse record cannot be put back on the roster through staff management`() {
        val s = setup()
        val nurse = insertNurse(s.ward, "가상 간호사", subject = s.member.first.toString())
        val staff = "/api/v1/wards/${s.ward}/staff/$nurse"
        assertEquals(1, rosterRepo.activeNurseCount(s.ward))
        assertEquals(200, patch(s.ward, s.member.first, s.admin.second, mapOf("active" to false)).status)

        val reactivate = http.put(staff, mapOf("displayName" to "가상 간호사", "active" to true), s.admin.second)
        assertEquals(409, reactivate.status, reactivate.body)
        assertEquals("MEMBER_INACTIVE", reactivate.code)

        // Even if the record is active anyway (data from before the rule), it is not rostered while the member is inactive.
        jdbc.update("UPDATE nurses SET active=TRUE WHERE nurse_id=?", nurse)
        assertEquals(0, rosterRepo.activeNurses(s.ward).size)
        assertFalse(rosterRepo.findNurse(nurse, s.ward)!!.active)

        // Reactivating the member first, then the record, brings the nurse back.
        jdbc.update("UPDATE nurses SET active=FALSE WHERE nurse_id=?", nurse)
        assertEquals(200, patch(s.ward, s.member.first, s.admin.second, mapOf("active" to true)).status)
        assertEquals(200, http.put(staff, mapOf("displayName" to "가상 간호사", "active" to true), s.admin.second).status)
        assertEquals(1, rosterRepo.activeNurseCount(s.ward))
    }

    private fun roles(ward: UUID, user: UUID): Set<String> = jdbc.queryForList(
        "SELECT role FROM ward_memberships WHERE ward_id=? AND subject=?", String::class.java, ward, user.toString(),
    ).filterNotNull().toSet()

    @Test
    fun `a deactivated admin can no longer administer the ward`() {
        val s = setup()
        val other = userToken("가상 부관리자")
        grant(s.ward, other.first, "WARD_ADMIN")
        assertEquals(200, patch(s.ward, other.first, s.admin.second, mapOf("active" to false)).status)

        assertEquals(404, members(s.ward, other.second).status)
        assertEquals(404, patch(s.ward, s.member.first, other.second, mapOf("active" to false)).status)
    }

    private fun race(s: Setup, a: Map<String, Any?>, b: Map<String, Any?>, otherAdmin: Pair<UUID, String>): List<HttpTestClient.Response> {
        val start = CountDownLatch(1)
        val first = CompletableFuture.supplyAsync { start.await(); patch(s.ward, otherAdmin.first, s.admin.second, a) }
        val second = CompletableFuture.supplyAsync { start.await(); patch(s.ward, s.admin.first, otherAdmin.second, b) }
        start.countDown()
        return listOf(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))
    }

    private fun twoAdmins(): Pair<Setup, Pair<UUID, String>> {
        val s = setup()
        val other = userToken("가상 부관리자")
        grant(s.ward, other.first, "WARD_ADMIN")
        grant(s.ward, other.first, "WARD_MEMBER")
        grant(s.ward, s.admin.first, "WARD_MEMBER")
        return s to other
    }

    @RepeatedTest(5)
    fun `two admins revoking each other leave exactly one active admin`() {
        val (s, other) = twoAdmins()
        val results = race(s, mapOf("admin" to false), mapOf("admin" to false), other)

        assertEquals(1, results.count { it.status == 200 }, results.joinToString { it.body })
        // The loser either lost its own admin role first (403) or was stopped by the last-admin rule (409).
        assertTrue(results.single { it.status != 200 }.status in setOf(403, 409))
        assertEquals(1, activeAdmins(s.ward))
    }

    @RepeatedTest(5)
    fun `two admins deactivating each other leave exactly one active admin`() {
        val (s, other) = twoAdmins()
        val results = race(s, mapOf("active" to false), mapOf("active" to false), other)

        assertEquals(1, results.count { it.status == 200 }, results.joinToString { it.body })
        assertTrue(results.single { it.status != 200 }.status in setOf(404, 409))
        assertEquals(1, activeAdmins(s.ward))
    }

    @RepeatedTest(5)
    fun `two admins revoking themselves leave exactly one admin and the other gets LAST_WARD_ADMIN`() {
        val (s, other) = twoAdmins()
        val start = CountDownLatch(1)
        val first = CompletableFuture.supplyAsync { start.await(); patch(s.ward, s.admin.first, s.admin.second, mapOf("admin" to false)) }
        val second = CompletableFuture.supplyAsync { start.await(); patch(s.ward, other.first, other.second, mapOf("admin" to false)) }
        start.countDown()
        val results = listOf(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))

        assertEquals(1, results.count { it.status == 200 }, results.joinToString { it.body })
        assertEquals("LAST_WARD_ADMIN", results.single { it.status != 200 }.code)
        assertEquals(1, activeAdmins(s.ward))
    }
}
