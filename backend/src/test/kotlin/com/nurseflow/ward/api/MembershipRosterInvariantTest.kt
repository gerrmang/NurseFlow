package com.nurseflow.ward.api

import com.nurseflow.roster.persistence.repository.RosterJdbcRepository
import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.random.Random

/**
 * Random sequences of every membership and staff operation that can change who is rostered, checking after each
 * step that admins and members without ward access are never rostered and the ward keeps an active admin.
 * Example tests only cover the paths someone thought of; this covers their combinations. Fictional data only.
 */
class MembershipRosterInvariantTest : ApiTestBase() {
    @Autowired lateinit var rosterRepo: RosterJdbcRepository

    private lateinit var wardId: UUID
    private val tokens = mutableMapOf<UUID, String>()
    private val members = mutableListOf<UUID>()

    private fun base() = "/api/v1/wards/$wardId"

    private data class Nurse(val id: UUID, val subject: String?, val active: Boolean, val team: UUID?, val order: Int?)
    private data class Role(val subject: String, val role: String, val active: Boolean)

    private fun nurses() = jdbc.query(
        "SELECT nurse_id, subject, active, team_id, team_order FROM nurses WHERE ward_id=?",
        { rs, _ -> Nurse(rs.getObject(1, UUID::class.java), rs.getString(2), rs.getBoolean(3), rs.getObject(4, UUID::class.java), rs.getObject(5) as Int?) },
        wardId,
    )

    private fun roles() = jdbc.query(
        "SELECT subject, role, active FROM ward_memberships WHERE ward_id=?",
        { rs, _ -> Role(rs.getString(1), rs.getString(2), rs.getBoolean(3)) }, wardId,
    )

    /** Any active admin's token; the operations themselves may revoke whoever acted before. */
    private fun actor(): String {
        val admins = roles().filter { it.role == "WARD_ADMIN" && it.active }.map { UUID.fromString(it.subject) }
        return tokens.getValue(admins.first { it in tokens })
    }

    private fun checkInvariants(step: String) {
        val all = nurses()
        val rows = roles()
        fun rolesOf(subject: String) = rows.filter { it.subject == subject }

        // 1. A linked record is active only for an active plain member: active WARD_MEMBER, no admin role at all.
        all.filter { it.active && it.subject != null }.forEach { n ->
            val r = rolesOf(n.subject!!)
            assertTrue(r.any { it.role == "WARD_MEMBER" && it.active }, "$step: active record ${n.id} of a member without access")
            assertTrue(r.none { it.role == "WARD_ADMIN" }, "$step: active record ${n.id} of an admin account")
        }
        // 2. Every roster query agrees with that rule.
        val expected = all.filter { n ->
            n.active && (n.subject == null || rolesOf(n.subject).let { r ->
                r.any { it.role == "WARD_MEMBER" && it.active } && r.none { it.role == "WARD_ADMIN" && it.active }
            })
        }.map { it.id }.toSet()
        assertEquals(expected, rosterRepo.activeNurses(wardId).map { it.id }.toSet(), "$step: activeNurses")
        assertEquals(expected.size, rosterRepo.activeNurseCount(wardId), "$step: activeNurseCount")
        all.forEach { n ->
            assertEquals(n.id in expected, rosterRepo.findNurse(n.id, wardId)!!.active, "$step: findNurse ${n.id}")
            n.subject?.let { s ->
                assertEquals(n.id.takeIf { it in expected }, rosterRepo.findNurseIdBySubject(wardId, s), "$step: personal calendar of $s")
            }
        }
        // 3. The ward always keeps an active admin.
        assertTrue(rows.any { it.role == "WARD_ADMIN" && it.active }, "$step: no active admin left")
        // 4. Team order stays 1..n among active members of each team, and only they hold a position.
        all.filter { !it.active || it.team == null }.forEach { assertEquals(null, it.order, "$step: ${it.id} keeps position") }
        all.filter { it.active && it.team != null }.groupBy { it.team }.forEach { (team, m) ->
            val positions = m.mapNotNull { it.order }.sorted()
            assertEquals((1..positions.size).toList(), positions, "$step: team $team positions")
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4, 5, 6, 7, 8])
    fun `admins and members without access are never rostered under any sequence of operations`(seed: Int) {
        val random = Random(seed)
        wardId = insertWard("불변 조건 병동")
        tokens.clear()
        members.clear()
        val (founder, founderToken) = userToken("가상 수간호사")
        grant(wardId, founder, "WARD_ADMIN")
        tokens[founder] = founderToken
        members += founder
        val teams = (1..2).map { http.post("${base()}/teams", mapOf("name" to "팀$it"), founderToken).json!!.get("teamId").asString() }
        repeat(5) {
            // Like an accepted nurse invitation: a member account with its linked nurse record.
            val (user, token) = userToken("가상 간호사 $it")
            grant(wardId, user, "WARD_MEMBER")
            insertNurse(wardId, "가상 간호사 $it", subject = user.toString())
            tokens[user] = token
            members += user
        }
        repeat(2) { insertNurse(wardId, "가상 미연결 $it") }
        checkInvariants("seed $seed setup")

        repeat(200) { step ->
            val label = "seed $seed step $step"
            val user = members.random(random)
            val nurse = nurses().random(random)
            val response = when (random.nextInt(8)) {
                0 -> patch(user, mapOf("admin" to random.nextBoolean()))
                1 -> patch(user, mapOf("active" to random.nextBoolean()))
                2 -> patch(user, mapOf("admin" to true, "active" to false))
                3 -> patch(user, mapOf("admin" to random.nextBoolean(), "active" to true))
                4, 5 -> http.put("${base()}/staff/${nurse.id}", mapOf("displayName" to "가상", "active" to !nurse.active), actor())
                6 -> http.put("${base()}/staff/${nurse.id}/team", mapOf("teamId" to teams.random(random)), actor())
                else -> teams.random(random).let { team ->
                    val ids = nurses().filter { it.active && it.team.toString() == team }.map { it.id.toString() }.shuffled(random)
                    http.put("${base()}/teams/$team/order", mapOf("nurseIds" to ids), actor())
                }
            }
            assertTrue(response.status in setOf(200, 204, 409), "$label: unexpected ${response.status} ${response.body}")
            checkInvariants(label)
        }
    }

    private fun patch(user: UUID, body: Map<String, Any>) =
        http.send("PATCH", "${base()}/memberships/$user", body, actor(), emptyMap())
}
