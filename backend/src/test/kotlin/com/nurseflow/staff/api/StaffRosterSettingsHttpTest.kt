package com.nurseflow.staff.api

import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** A5: admin drag order inside a team, senior eligibility, stage override and their history. Fictional staff only. */
class StaffRosterSettingsHttpTest : ApiTestBase() {
    private lateinit var wardId: UUID
    private lateinit var admin: String
    private lateinit var adminId: UUID

    @BeforeEach
    fun setup() {
        wardId = insertWard("편성 병동")
        val (id, token) = userToken("가상 수간호사")
        grant(wardId, id, "WARD_ADMIN")
        admin = token
        adminId = id
    }

    private fun base() = "/api/v1/wards/$wardId"

    private fun team(name: String): String = http.post("${base()}/teams", mapOf("name" to name), admin).json!!.get("teamId").asString()

    private fun nurse(name: String, teamId: String?): String {
        val created = http.post("${base()}/staff", mapOf("displayName" to name, "teamId" to teamId), admin)
        assertEquals(201, created.status, created.body)
        return created.json!!.get("nurseId").asString()
    }

    private fun staff(): List<JsonNode> = http.get("${base()}/staff", admin).json!!.get("items").toList()

    private fun order(teamId: String, vararg nurseIds: String) =
        http.put("${base()}/teams/$teamId/order", mapOf("nurseIds" to nurseIds.toList()), admin)

    private fun profile(nurseId: String, careerStartedOn: String) =
        assertEquals(200, http.put("${base()}/staff/$nurseId/profile", mapOf("careerStartedOn" to careerStartedOn, "rank" to "과장"), admin).status)

    @Test
    fun `unordered members sort by career date and the admin order beats it`() {
        val a = team("A팀")
        val senior = nurse("가상 선배", a)
        val junior = nurse("가상 후배", a)
        // The junior has the longer career on paper, but the admin says otherwise.
        profile(junior, "2010-03")
        profile(senior, "2022-03")
        assertEquals(listOf("가상 후배", "가상 선배"), staff().map { it.get("displayName").asString() })
        assertTrue(staff().all { it.get("teamOrder").isNull })

        val saved = order(a, senior, junior)

        assertEquals(200, saved.status, saved.body)
        assertEquals(listOf(senior, junior), saved.json!!.get("items").toList().map { it.get("nurseId").asString() })
        assertEquals(listOf("가상 선배", "가상 후배"), staff().map { it.get("displayName").asString() })
        assertEquals(listOf(1, 2), staff().map { it.get("teamOrder").asInt() })
    }

    @Test
    fun `the order must list exactly the team's active nurses`() {
        val a = team("A팀")
        val b = team("B팀")
        val one = nurse("가상 1", a)
        val two = nurse("가상 2", a)
        val other = nurse("가상 다른팀", b)

        assertEquals("TEAM_ORDER_STALE", order(a, one).code)
        assertEquals("TEAM_ORDER_STALE", order(a, one, two, other).code)
        assertEquals("INVALID_REQUEST", order(a, one, one).code)
        assertEquals("TEAM_NOT_FOUND", order(UUID.randomUUID().toString(), one).code)

        // Deactivated nurses drop out of the list to send.
        http.put("${base()}/staff/$two", mapOf("displayName" to "가상 2", "active" to false), admin)
        assertEquals(200, order(a, one).status)
    }

    @Test
    fun `a moved nurse is listed below the ordered members until placed`() {
        val a = team("A팀")
        val b = team("B팀")
        val b1 = nurse("가상 B1", b)
        val b2 = nurse("가상 B2", b)
        order(b, b2, b1)
        val mover = nurse("가상 이동", a)
        profile(mover, "2000-01")
        order(a, mover)

        val moved = http.put("${base()}/staff/$mover/team", mapOf("teamId" to b), admin).json!!

        // Longest career, yet below the members the admin already ordered; the old position is gone.
        assertTrue(moved.get("teamOrder").isNull)
        assertEquals(listOf("가상 B2", "가상 B1", "가상 이동"), staff().map { it.get("displayName").asString() })
        assertEquals(200, order(b, mover, b2, b1).status)
        assertEquals(listOf("가상 이동", "가상 B2", "가상 B1"), staff().map { it.get("displayName").asString() })
    }

    @Test
    fun `senior eligibility and stage override are set by the admin and drive the roster stage`() {
        val a = team("A팀")
        val n = nurse("가상 간호사", a)
        profile(n, "2025-01")
        val computed = staff().single().get("profile").get("scoringStage").asInt()
        assertEquals(computed, staff().single().get("rosterStage").asInt())

        val updated = http.put("${base()}/staff/$n/roster-settings", mapOf("seniorEligible" to true, "stageOverride" to 4), admin).json!!

        assertTrue(updated.get("seniorEligible").asBoolean())
        assertEquals(4, updated.get("stageOverride").asInt())
        assertEquals(4, updated.get("rosterStage").asInt())
        val cleared = http.put("${base()}/staff/$n/roster-settings", mapOf("seniorEligible" to false, "stageOverride" to null), admin).json!!
        assertFalse(cleared.get("seniorEligible").asBoolean())
        assertTrue(cleared.get("stageOverride").isNull)
        assertEquals(computed, cleared.get("rosterStage").asInt())

        assertEquals(400, http.put("${base()}/staff/$n/roster-settings", mapOf("seniorEligible" to true, "stageOverride" to 6), admin).status)
        assertEquals(400, http.put("${base()}/staff/$n/roster-settings", mapOf("stageOverride" to 2), admin).status)
        // Leaving stageOverride out must not silently clear an existing override.
        http.put("${base()}/staff/$n/roster-settings", mapOf("seniorEligible" to false, "stageOverride" to 3), admin)
        assertEquals(400, http.put("${base()}/staff/$n/roster-settings", mapOf("seniorEligible" to true), admin).status)
        assertEquals(3, staff().single().get("stageOverride").asInt())
    }

    @Test
    fun `every adjustment is recorded with who changed what, newest first`() {
        val a = team("A팀")
        val b = team("B팀")
        val first = nurse("가상 1", a)
        val second = nurse("가상 2", a)
        profile(first, "2020-03")
        order(a, second, first)
        http.put("${base()}/staff/$first/roster-settings", mapOf("seniorEligible" to true, "stageOverride" to 3), admin)
        http.put("${base()}/staff/$first/team", mapOf("teamId" to b), admin)

        val history = http.get("${base()}/staff/$first/adjustments", admin)

        assertEquals(200, history.status, history.body)
        assertEquals("private, no-store", history.header("Cache-Control"))
        val items = history.json!!.get("items").toList()
        assertEquals(
            setOf("PROFILE_SET_BY_ADMIN", "TEAM_ORDER", "SENIOR_ELIGIBLE", "STAGE_OVERRIDE", "TEAM_ASSIGNED"),
            items.map { it.get("kind").asString() }.toSet(),
        )
        // Placed at 2 (never ordered before), then the move to B cleared the position.
        val teamOrder = items.filter { it.get("kind").asString() == "TEAM_ORDER" }.map {
            it.get("before").takeIf { n -> !n.isNull }?.asString() to it.get("after").takeIf { n -> !n.isNull }?.asString()
        }
        assertEquals(listOf("2" to null, null to "2"), teamOrder)
        val moved = items.single { it.get("kind").asString() == "TEAM_ASSIGNED" }
        assertEquals("A팀", moved.get("before").asString())
        assertEquals("B팀", moved.get("after").asString())
        assertEquals("가상 수간호사", moved.get("actorName").asString())
        assertEquals(adminId.toString(), moved.get("actorUserId").asString())
        assertTrue(items.single { it.get("kind").asString() == "PROFILE_SET_BY_ADMIN" }.get("before").isNull)
        assertEquals("2020-03 / 과장", items.single { it.get("kind").asString() == "PROFILE_SET_BY_ADMIN" }.get("after").asString())
        val times = items.map { it.get("at").asString() }
        assertEquals(times.sortedDescending(), times)
    }

    @Test
    fun `a deactivated nurse leaves the order and is placed again after reactivation`() {
        val a = team("A팀")
        val first = nurse("가상 1", a)
        val second = nurse("가상 2", a)
        order(a, first, second)

        http.put("${base()}/staff/$first", mapOf("displayName" to "가상 1", "active" to false), admin)
        assertEquals(200, order(a, second).status)
        http.put("${base()}/staff/$first", mapOf("displayName" to "가상 1", "active" to true), admin)

        val listed = staff()
        assertEquals(listOf("가상 2", "가상 1"), listed.map { it.get("displayName").asString() })
        assertEquals(1, listed[0].get("teamOrder").asInt())
        assertTrue(listed[1].get("teamOrder").isNull)
        val history = http.get("${base()}/staff/$first/adjustments", admin).json!!.get("items").toList()
        assertTrue(history.any { it.get("kind").asString() == "TEAM_ORDER" && it.get("after").isNull })
    }

    @Test
    fun `positions stay contiguous after a nurse leaves the order`() {
        val a = team("A팀")
        val ids = (1..3).map { nurse("가상 $it", a) }
        order(a, *ids.toTypedArray())

        http.put("${base()}/staff/${ids[1]}", mapOf("displayName" to "가상 2", "active" to false), admin)

        assertEquals(listOf(1, 2), staff().map { it.get("teamOrder").asInt() })
        assertEquals(listOf("가상 1", "가상 3"), staff().map { it.get("displayName").asString() })
    }

    @Test
    fun `moving or unassigning an ordered nurse keeps the old team's positions contiguous`() {
        val a = team("A팀")
        val b = team("B팀")
        val ids = (1..4).map { nurse("가상 $it", a) }
        order(a, *ids.toTypedArray())
        fun teamOrders(teamName: String) = staff().filter { !it.get("team").isNull && it.get("team").get("name").asString() == teamName }
            .map { it.get("displayName").asString() to it.get("teamOrder").asInt() }

        http.put("${base()}/staff/${ids[1]}/team", mapOf("teamId" to b), admin)
        assertEquals(listOf("가상 1" to 1, "가상 3" to 2, "가상 4" to 3), teamOrders("A팀"))

        // The most senior one leaves: everyone moves up instead of starting at 2.
        http.put("${base()}/staff/${ids[0]}/team", mapOf("teamId" to b), admin)
        assertEquals(listOf("가상 3" to 1, "가상 4" to 2), teamOrders("A팀"))

        http.put("${base()}/staff/${ids[2]}/team", mapOf("teamId" to null), admin)
        assertEquals(listOf("가상 4" to 1), teamOrders("A팀"))
        val history = http.get("${base()}/staff/${ids[3]}/adjustments", admin).json!!.get("items").toList()
            .filter { it.get("kind").asString() == "TEAM_ORDER" }.map { it.get("before").asString() to it.get("after").asString() }
        assertEquals(listOf("2" to "1", "3" to "2", "4" to "3"), history.take(3))
    }

    @Test
    fun `deleting a team releases its inactive members so they never come back into a deleted team`() {
        val a = team("A팀")
        val n = nurse("가상 휴직", a)
        http.put("${base()}/staff/$n", mapOf("displayName" to "가상 휴직", "active" to false), admin)

        assertEquals(204, http.delete("${base()}/teams/$a", admin).status)
        http.put("${base()}/staff/$n", mapOf("displayName" to "가상 휴직", "active" to true), admin)

        val back = staff().single()
        assertTrue(back.get("team").isNull)
        assertEquals(null, jdbc.queryForObject("SELECT team_id FROM nurses WHERE nurse_id=?", UUID::class.java, UUID.fromString(n)))
        assertEquals("TEAM_NOT_FOUND", order(a).code)
        val moved = http.get("${base()}/staff/$n/adjustments", admin).json!!.get("items").toList()
            .first { it.get("kind").asString() == "TEAM_ASSIGNED" }
        assertEquals("A팀", moved.get("before").asString())
        assertTrue(moved.get("after").isNull)
    }

    @Test
    fun `changes written in one request are listed in reverse write order`() {
        val n = nurse("가상 간호사", team("A팀"))

        http.put("${base()}/staff/$n/roster-settings", mapOf("seniorEligible" to true, "stageOverride" to 2), admin)

        val kinds = http.get("${base()}/staff/$n/adjustments", admin).json!!.get("items").toList().map { it.get("kind").asString() }
        assertEquals(listOf("STAGE_OVERRIDE", "SENIOR_ELIGIBLE"), kinds)
    }

    @Test
    fun `a nurse's own profile does not show the admin's roster judgements`() {
        val a = team("A팀")
        val n = nurse("가상 간호사", a)
        val (userId, token) = userToken("가상 간호사")
        grant(wardId, userId, "WARD_MEMBER")
        jdbc.update("UPDATE nurses SET subject=? WHERE nurse_id=?", userId.toString(), UUID.fromString(n))
        http.put("${base()}/staff/$n/roster-settings", mapOf("seniorEligible" to true, "stageOverride" to 4), admin)

        val mine = http.get("${base()}/my-profile", token)

        assertEquals(200, mine.status, mine.body)
        listOf("teamOrder", "seniorEligible", "stageOverride", "rosterStage").forEach {
            assertFalse(mine.json!!.has(it), "$it must not reach the nurse")
        }
    }

    @Test
    fun `saving the same career profile again records nothing`() {
        val a = team("A팀")
        val n = nurse("가상 간호사", a)
        profile(n, "2020-03")
        profile(n, "2020-03")

        val kinds = http.get("${base()}/staff/$n/adjustments", admin).json!!.get("items").toList().map { it.get("kind").asString() }
        assertEquals(listOf("PROFILE_SET_BY_ADMIN"), kinds)
    }

    @Test
    fun `members cannot change or read roster settings`() {
        val a = team("A팀")
        val n = nurse("가상 간호사", a)
        val (memberId, member) = userToken("가상 구성원")
        grant(wardId, memberId, "WARD_MEMBER")

        assertEquals("WARD_ADMIN_REQUIRED", http.put("${base()}/teams/$a/order", mapOf("nurseIds" to listOf(n)), member).code)
        assertEquals("WARD_ADMIN_REQUIRED", http.put("${base()}/staff/$n/roster-settings", mapOf("seniorEligible" to true, "stageOverride" to null), member).code)
        assertEquals("WARD_ADMIN_REQUIRED", http.get("${base()}/staff/$n/adjustments", member).code)
        val (_, outsider) = userToken("가상 외부인")
        assertEquals(404, http.get("${base()}/staff/$n/adjustments", outsider).status)
        assertEquals(404, http.put("${base()}/teams/$a/order", mapOf("nurseIds" to listOf(n)), outsider).status)
        assertEquals(404, http.put("${base()}/staff/$n/roster-settings", mapOf("seniorEligible" to true, "stageOverride" to null), outsider).status)
    }

    @Test
    fun `concurrent reorders of one team leave a consistent order`() {
        val a = team("A팀")
        val ids = (1..5).map { nurse("가상 $it", a) }
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val orders = listOf(ids, ids.reversed(), ids.shuffled(), ids.shuffled())
            val results = orders.map { o -> executor.submit<Int> { start.await(); order(a, *o.toTypedArray()).status } }
            start.countDown()
            assertEquals(List(4) { 200 }, results.map { it.get(10, TimeUnit.SECONDS) })
        } finally {
            executor.shutdownNow()
        }

        assertEquals((1..5).toList(), staff().map { it.get("teamOrder").asInt() })
    }
}
