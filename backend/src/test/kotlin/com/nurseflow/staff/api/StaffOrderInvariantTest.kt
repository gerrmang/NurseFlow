package com.nurseflow.staff.api

import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID
import kotlin.random.Random

/**
 * Random sequences of every operation that can touch team membership or order, checking the team-order
 * invariants after each step. Example tests only cover the paths someone thought of; this covers their
 * combinations. Fixed seeds keep failures reproducible. Fictional staff only.
 */
class StaffOrderInvariantTest : ApiTestBase() {
    private lateinit var wardId: UUID
    private lateinit var admin: String

    @BeforeEach
    fun setup() {
        wardId = insertWard("불변 조건 병동")
        val (adminId, token) = userToken("가상 수간호사")
        grant(wardId, adminId, "WARD_ADMIN")
        admin = token
    }

    private fun base() = "/api/v1/wards/$wardId"

    private data class Nurse(val id: UUID, val team: UUID?, val order: Int?, val active: Boolean)

    private fun nurses(): List<Nurse> = jdbc.query(
        "SELECT nurse_id, team_id, team_order, active FROM nurses WHERE ward_id=?",
        { rs, _ -> Nurse(rs.getObject(1, UUID::class.java), rs.getObject(2, UUID::class.java), rs.getObject(3) as Int?, rs.getBoolean(4)) },
        wardId,
    )

    private fun activeTeams(): Set<UUID> =
        jdbc.queryForList("SELECT team_id FROM ward_teams WHERE ward_id=? AND active=TRUE", UUID::class.java, wardId).filterNotNull().toSet()

    private fun checkInvariants(step: String) {
        val all = nurses()
        val teams = activeTeams()
        all.filter { !it.active || it.team == null }.forEach {
            assertEquals(null, it.order, "$step: inactive or unassigned nurse ${it.id} keeps position ${it.order}")
        }
        all.filter { it.active && it.team != null }.forEach {
            assertTrue(it.team in teams, "$step: active nurse ${it.id} is in deleted team ${it.team}")
        }
        all.filter { it.active && it.team != null }.groupBy { it.team }.forEach { (team, members) ->
            val positions = members.mapNotNull { it.order }.sorted()
            assertEquals((1..positions.size).toList(), positions, "$step: team $team positions $positions are not 1..n")
        }
        val api = http.get("${base()}/staff?includeInactive=true", admin).json!!.get("items").toList()
            .associate { UUID.fromString(it.get("nurseId").asString()) to it.get("teamOrder").takeIf { n -> !n.isNull }?.asInt() }
        all.forEach { assertEquals(it.order.takeIf { _ -> it.team in teams }, api[it.id], "$step: API teamOrder of ${it.id}") }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5])
    fun `team order stays contiguous under any sequence of staff operations`(seed: Int) {
        val random = Random(seed)
        val teams = (1..3).map { http.post("${base()}/teams", mapOf("name" to "팀$it"), admin).json!!.get("teamId").asString() }.toMutableList()
        repeat(6) { http.post("${base()}/staff", mapOf("displayName" to "가상 $it", "teamId" to teams.random(random)), admin) }

        repeat(150) { step ->
            val all = nurses()
            val nurse = all.randomOrNull(random)
            val label = "seed $seed step $step"
            when (random.nextInt(8)) {
                0 -> http.post("${base()}/staff", mapOf("displayName" to "가상 추가 $step", "teamId" to teams.randomOrNull(random)), admin)
                1 -> nurse?.let {
                    val target = if (random.nextInt(4) == 0) null else teams.randomOrNull(random)
                    http.put("${base()}/staff/${it.id}/team", mapOf("teamId" to target), admin)
                }
                2, 3 -> teams.randomOrNull(random)?.let { team ->
                    val members = all.filter { it.active && it.team.toString() == team }.map { it.id.toString() }.shuffled(random)
                    val response = http.put("${base()}/teams/$team/order", mapOf("nurseIds" to members), admin)
                    assertEquals(200, response.status, "$label reorder: ${response.body}")
                }
                4 -> nurse?.let { http.put("${base()}/staff/${it.id}", mapOf("displayName" to "가상", "active" to !it.active), admin) }
                5 -> teams.randomOrNull(random)?.let { team ->
                    val deleted = http.delete("${base()}/teams/$team", admin)
                    if (deleted.status in 200..299) teams.remove(team)
                }
                6 -> if (teams.size < 4) teams += http.post("${base()}/teams", mapOf("name" to "새팀$step"), admin).json!!.get("teamId").asString()
                7 -> nurse?.let {
                    http.put("${base()}/staff/${it.id}/roster-settings", mapOf("seniorEligible" to random.nextBoolean(), "stageOverride" to null), admin)
                }
            }
            checkInvariants(label)
        }
    }
}
