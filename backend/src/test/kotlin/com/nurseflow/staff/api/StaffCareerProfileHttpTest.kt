package com.nurseflow.staff.api

import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

class StaffCareerProfileHttpTest : ApiTestBase() {
    private lateinit var wardId: UUID
    private lateinit var admin: String
    private val today = YearMonth.now(ZoneId.of("Asia/Seoul"))

    @BeforeEach
    fun setup() {
        wardId = insertWard("경력 병동")
        val (adminId, token) = userToken("가상 수간호사")
        grant(wardId, adminId, "WARD_ADMIN")
        admin = token
    }

    private fun base() = "/api/v1/wards/$wardId"

    private fun team(name: String): String {
        val created = http.post("${base()}/teams", mapOf("name" to name), admin)
        assertEquals(201, created.status, created.body)
        return created.json!!.get("teamId").asString()
    }

    /** A nurse who joined through an invitation, optionally submitting the hospital joining month. */
    private fun joinedNurse(name: String, hospitalJoinedOn: YearMonth = YearMonth.of(2020, 3)): Pair<UUID, String> {
        val (userId, token) = userToken(name)
        val invite = http.post("${base()}/invitations", mapOf("role" to "WARD_MEMBER"), admin).json!!.get("token").asString()
        val body = buildMap {
            put("token", invite)
            put("hospitalJoinedOn", hospitalJoinedOn.toString())
        }
        val joined = http.post("/api/v1/invitations/redeem", body, token)
        assertEquals(201, joined.status, joined.body)
        return userId to token
    }

    private fun nurseIdOf(userId: UUID): String =
        jdbc.queryForObject("SELECT nurse_id FROM nurses WHERE subject=?", UUID::class.java, userId.toString()).toString()

    private fun staffList(query: String = ""): List<JsonNode> =
        http.get("${base()}/staff$query", admin).json!!.get("items").values().toList()

    @Test
    fun `admin manages teams and members can only read them`() {
        val a = team("A팀")
        team("B팀")
        val (_, nurse) = joinedNurse("가상 간호사")

        assertEquals(listOf("A팀", "B팀"), http.get("${base()}/teams", nurse).json!!.get("items").values().map { it.get("name").asString() })
        assertEquals("WARD_ADMIN_REQUIRED", http.post("${base()}/teams", mapOf("name" to "C팀"), nurse).code)
        assertEquals("TEAM_NAME_DUPLICATE", http.post("${base()}/teams", mapOf("name" to "A팀"), admin).code)
        assertEquals("B-1팀", http.put("${base()}/teams/$a", mapOf("name" to "B-1팀", "sortOrder" to 5), admin).json!!.get("name").asString())
        assertEquals(listOf("B팀", "B-1팀"), http.get("${base()}/teams", admin).json!!.get("items").values().map { it.get("name").asString() })
    }

    @Test
    fun `team with active nurses cannot be deleted`() {
        val a = team("A팀")
        val (userId, _) = joinedNurse("가상 간호사")
        http.put("${base()}/staff/${nurseIdOf(userId)}/team", mapOf("teamId" to a), admin)

        assertEquals("TEAM_NOT_EMPTY", http.delete("${base()}/teams/$a", admin).code)
        http.put("${base()}/staff/${nurseIdOf(userId)}/team", mapOf("teamId" to null), admin)
        assertEquals(204, http.delete("${base()}/teams/$a", admin).status)
        assertEquals(0, http.get("${base()}/teams", admin).json!!.get("items").size())
    }

    @Test
    fun `career policy defaults and admin changes`() {
        val (_, nurse) = joinedNurse("가상 간호사")
        val defaults = http.get("${base()}/career-policy", nurse).json!!
        assertEquals(listOf(12, 36, 72, 120), defaults.get("stageThresholdMonths").values().map { it.asInt() })
        assertEquals(listOf("팀장", "과장", "신규"), defaults.get("ranks").values().map { it.asString() })
        assertTrue(defaults.get("isDefault").asBoolean())

        val bad = http.put("${base()}/career-policy", mapOf("stageThresholdMonths" to listOf(12, 6, 72, 120), "ranks" to listOf("신규")), admin)
        val short = http.put("${base()}/career-policy", mapOf("stageThresholdMonths" to listOf(12, 36), "ranks" to listOf("신규")), admin)
        val member = http.put("${base()}/career-policy", mapOf("stageThresholdMonths" to listOf(12, 36, 72, 120), "ranks" to listOf("신규")), nurse)
        assertEquals("INVALID_CAREER_POLICY", bad.code)
        assertEquals("INVALID_CAREER_POLICY", short.code)
        assertEquals("WARD_ADMIN_REQUIRED", member.code)

        val saved = http.put("${base()}/career-policy",
            mapOf("stageThresholdMonths" to listOf(6, 24, 60, 120), "ranks" to listOf("수간호사", "책임", "일반")), admin)
        assertEquals(200, saved.status, saved.body)
        assertEquals(false, http.get("${base()}/career-policy", nurse).json!!.get("isDefault").asBoolean())
    }

    @Test
    fun `hospital joining month stays separate from actual career start`() {
        val hospitalJoinedOn = today.minusMonths(2)
        val actualCareerStart = today.minusMonths(40)
        val (userId, nurse) = joinedNurse("가상 간호사", hospitalJoinedOn)
        val nurseId = nurseIdOf(userId)

        val joined = http.get("${base()}/my-profile", nurse).json!!.get("profile")
        assertEquals(hospitalJoinedOn.toString(), joined.get("hospitalJoinedOn").asString())
        assertTrue(joined.get("careerStartedOn").isNull)
        assertEquals("MISSING", joined.get("status").asString())

        val actualCareer = http.put("${base()}/staff/$nurseId/profile", mapOf(
            "careerStartedOn" to actualCareerStart.toString(), "rank" to "과장",
        ), admin).json!!.get("profile")
        assertEquals("CONFIRMED", actualCareer.get("status").asString())
        assertEquals(actualCareerStart.toString(), actualCareer.get("careerStartedOn").asString())
        assertEquals(hospitalJoinedOn.toString(), actualCareer.get("hospitalJoinedOn").asString())
        assertEquals(3, actualCareer.get("scoringStage").asInt())

        val correctedHospitalMonth = today.minusMonths(1)
        val updated = http.put("${base()}/my-profile", mapOf("hospitalJoinedOn" to correctedHospitalMonth.toString()), nurse)
            .json!!.get("profile")
        assertEquals(correctedHospitalMonth.toString(), updated.get("hospitalJoinedOn").asString())
        assertEquals(actualCareerStart.toString(), updated.get("careerStartedOn").asString())
        assertEquals("CONFIRMED", updated.get("status").asString())
        assertEquals(3, updated.get("scoringStage").asInt())
    }

    @Test
    fun `profile input is validated against the ward policy`() {
        val (userId, nurse) = joinedNurse("가상 간호사")
        val nurseId = nurseIdOf(userId)

        assertEquals("NURSE_IDENTITY_REQUIRED", http.get("${base()}/my-profile", admin).code)
        val rankByNurse = http.put("${base()}/my-profile", mapOf("hospitalJoinedOn" to "2020-03", "rank" to "원장"), nurse)
        assertEquals(400, rankByNurse.status)
        assertEquals(listOf("rank"), rankByNurse.fieldNames)
        assertEquals("RANK_NOT_IN_POLICY", http.put("${base()}/staff/$nurseId/profile",
            mapOf("careerStartedOn" to "2020-03", "rank" to "원장"), admin).code)
        assertEquals("INVALID_CAREER_START", http.put("${base()}/staff/$nurseId/profile",
            mapOf("careerStartedOn" to today.plusMonths(1).toString(), "rank" to "신규"), admin).code)
        assertEquals(listOf("careerStartedOn"),
            http.put("${base()}/staff/$nurseId/profile", mapOf("careerStartedOn" to "2020-13", "rank" to "신규"), admin).fieldNames)
        assertEquals("INVALID_HOSPITAL_JOIN_MONTH", http.put(
            "${base()}/my-profile", mapOf("hospitalJoinedOn" to today.plusMonths(1).toString()), nurse,
        ).code)
        assertEquals(listOf("hospitalJoinedOn"), http.put(
            "${base()}/my-profile", mapOf("hospitalJoinedOn" to "2020-13"), nurse,
        ).fieldNames)
        assertEquals("MISSING", http.get("${base()}/my-profile", nurse).json!!.get("profile").get("status").asString())
        assertEquals("PROFILE_NOT_SUBMITTED", http.post("${base()}/staff/$nurseId/profile/confirm", null, admin).code)
    }

    @Test
    fun `confirmation rejects a submitted rank removed from the current ward policy`() {
        val (userId, _) = joinedNurse("가상 간호사")
        val nurseId = nurseIdOf(userId)
        jdbc.update("UPDATE nurses SET career_started_on=?, rank_name=?, profile_submitted_at=? WHERE nurse_id=?",
            "2020-03", "과장", Instant.now().toString(), UUID.fromString(nurseId))
        assertEquals(200, http.put("${base()}/career-policy", mapOf(
            "stageThresholdMonths" to listOf(12, 36, 72, 120), "ranks" to listOf("신규"),
        ), admin).status)

        assertEquals("RANK_NOT_IN_POLICY", http.post("${base()}/staff/$nurseId/profile/confirm", null, admin).code)
        assertEquals(null, jdbc.queryForObject(
            "SELECT confirmed_rank_name FROM nurses WHERE nurse_id=?", String::class.java, UUID.fromString(nurseId),
        ))
    }

    @Test
    fun `join records hospital month without setting actual career`() {
        val (userId, token) = userToken("가상 간호사")
        val invite = http.post("${base()}/invitations", mapOf("role" to "WARD_MEMBER"), admin).json!!.get("token").asString()

        val oldProfile = http.post("/api/v1/invitations/redeem",
            mapOf("token" to invite, "hospitalJoinedOn" to "2020-03", "rank" to "원장"), token)
        assertEquals(listOf("rank"), oldProfile.fieldNames)
        val oldField = http.post("/api/v1/invitations/redeem",
            mapOf("token" to invite, "careerStartedOn" to "2020-03"), token)
        assertEquals(listOf("careerStartedOn"), oldField.fieldNames)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM nurses", Int::class.java))

        val joined = http.post("/api/v1/invitations/redeem", mapOf("token" to invite, "hospitalJoinedOn" to "2020-03"), token)

        assertEquals(201, joined.status, joined.body)
        val profile = http.get("${base()}/my-profile", token).json!!.get("profile")
        assertEquals("2020-03", profile.get("hospitalJoinedOn").asString())
        assertTrue(profile.get("careerStartedOn").isNull)
        assertTrue(profile.get("rank").isNull)
        assertEquals("MISSING", profile.get("status").asString())

        val actual = http.put("${base()}/staff/${nurseIdOf(userId)}/profile", mapOf(
            "careerStartedOn" to "2015-05", "rank" to "과장",
        ), admin).json!!.get("profile")
        assertEquals("2015-05", actual.get("careerStartedOn").asString())
        assertEquals("2020-03", actual.get("hospitalJoinedOn").asString())
        assertEquals("CONFIRMED", actual.get("status").asString())
    }

    @Test
    fun `staff list is grouped by team and sorted by experience`() {
        val a = team("A팀")
        val b = team("B팀")
        val people = listOf(
            Triple("A-신규", a, 3L), Triple("A-고참", a, 150L), Triple("A-중간", a, 50L),
            Triple("B-중간", b, 40L), Triple("B-고참", b, 100L), Triple("무소속", null, 200L),
        )
        people.forEach { (name, teamId, months) ->
            val (userId, _) = joinedNurse(name, today.minusMonths(months))
            val nurseId = nurseIdOf(userId)
            teamId?.let { http.put("${base()}/staff/$nurseId/team", mapOf("teamId" to it), admin) }
            http.put("${base()}/staff/$nurseId/profile", mapOf(
                "careerStartedOn" to today.minusMonths(months).toString(), "rank" to "신규",
            ), admin)
        }

        val all = staffList()
        assertEquals(listOf("A-고참", "A-중간", "A-신규", "B-고참", "B-중간", "무소속"), all.map { it.get("displayName").asString() })
        assertEquals(listOf(5, 3, 1, 4, 3, 5), all.map { it.get("profile").get("scoringStage").asInt() })
        assertEquals(listOf("B-고참", "B-중간"), staffList("?teamId=$b").map { it.get("displayName").asString() })
    }

    @Test
    fun `admin can create a fictional nurse, set a confirmed profile, deactivate it`() {
        val a = team("A팀")
        val created = http.post("${base()}/staff", mapOf("displayName" to "가상 간호사 Z", "teamId" to a), admin)
        assertEquals(201, created.status, created.body)
        val nurseId = created.json!!.get("nurseId").asString()

        val set = http.put("${base()}/staff/$nurseId/profile", mapOf("careerStartedOn" to today.minusMonths(80).toString(), "rank" to "과장"), admin)
        assertEquals("CONFIRMED", set.json!!.get("profile").get("status").asString())
        assertEquals(4, set.json!!.get("profile").get("scoringStage").asInt())

        http.put("${base()}/staff/$nurseId", mapOf("displayName" to "가상 간호사 Z", "active" to false), admin)
        assertTrue(staffList().none { it.get("nurseId").asString() == nurseId })
        assertEquals(1, staffList("?includeInactive=true").size)
    }

    @Test
    fun `nurses cannot see the staff list or change teams and profiles of others`() {
        val (userId, nurse) = joinedNurse("가상 간호사")
        val nurseId = nurseIdOf(userId)
        val a = team("A팀")

        assertEquals("WARD_ADMIN_REQUIRED", http.get("${base()}/staff", nurse).code)
        assertEquals("WARD_ADMIN_REQUIRED", http.put("${base()}/staff/$nurseId/team", mapOf("teamId" to a), nurse).code)
        assertEquals("WARD_ADMIN_REQUIRED", http.post("${base()}/staff/$nurseId/profile/confirm", null, nurse).code)
        val (_, outsider) = userToken("외부인")
        assertEquals("WARD_NOT_FOUND", http.get("${base()}/teams", outsider).code)
        assertEquals("WARD_NOT_FOUND", http.get("${base()}/my-profile", outsider).code)
    }
}
