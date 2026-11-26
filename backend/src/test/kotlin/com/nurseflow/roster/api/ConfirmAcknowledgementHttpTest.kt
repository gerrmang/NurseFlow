package com.nurseflow.roster.api

import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/** A16 over HTTP: the check, the per-item confirm body, its error codes and the stored record. */
class ConfirmAcknowledgementHttpTest : ApiTestBase() {
    private lateinit var admin: String
    private lateinit var nurse: String
    private lateinit var wardId: UUID

    @BeforeEach
    fun setup() {
        val (adminId, adminToken) = userToken("가상 관리자")
        val (nurseUserId, nurseToken) = userToken("가상 간호사")
        admin = adminToken
        nurse = nurseToken
        wardId = insertWard("확인 병동")
        grant(wardId, adminId, "WARD_ADMIN")
        grant(wardId, nurseUserId, "WARD_MEMBER")
        insertNurse(wardId, "가상 간호사", nurseUserId.toString())
        jdbc.update("""INSERT INTO shift_types(shift_type_id,ward_id,code,display_name,kind,start_time,end_time,spans_midnight)
            VALUES(?,?,'DAY','데이','DAY','07:00:00','15:00:00',FALSE)""", UUID.randomUUID(), wardId)
    }

    @Test
    fun `admin reads the check, confirms with every item acknowledged and reads the record`() {
        val created = http.post("/api/v1/wards/$wardId/rosters", mapOf("month" to "2026-12"), admin, idem())
        val rosterId = created.json!!.get("rosterId").asString()
        val shiftTypeId = jdbc.queryForObject("SELECT shift_type_id FROM shift_types WHERE ward_id=?", String::class.java, wardId)
        val requirements = http.put("/api/v1/rosters/$rosterId/requirements",
            mapOf("items" to listOf(mapOf("date" to "2026-12-01", "shiftTypeId" to shiftTypeId, "requiredCount" to 1, "requiredQualifications" to emptyList<String>()))),
            admin, mapOf("If-Match" to created.header("ETag")!!))
        assertTrue(requirements.status in 200..299, requirements.body)
        val etag = http.get("/api/v1/rosters/$rosterId", admin).header("ETag")!!

        val check = http.get("/api/v1/rosters/$rosterId/confirmation-check", admin)
        assertEquals(200, check.status, check.body)
        assertEquals("private, no-store", check.header("Cache-Control"))
        val body = check.json!!
        assertTrue(body.get("confirmable").asBoolean())
        assertEquals(1, body.get("generalUnfilled").get("count").asInt())
        assertEquals(0, body.get("seniorUnfilled").get("count").asInt())
        val shortfall = body.get("hourShortfalls").get("items").toList().single()
        assertEquals("BELOW_MONTHLY_TARGET", shortfall.get("reason").asString())
        assertEquals(403, http.get("/api/v1/rosters/$rosterId/confirmation-check", nurse).status)

        val missing = http.post("/api/v1/rosters/$rosterId/confirm", emptyMap<String, Any>(), admin, idem() + ("If-Match" to etag))
        assertEquals(422, missing.status, missing.body)
        assertEquals("UNFILLED_ACK_REQUIRED", missing.code)

        val acknowledgements = mapOf(
            "generalUnfilledSlotIds" to body.get("generalUnfilled").get("items").toList().map { it.get("slotId").asString() },
            "hourShortfalls" to listOf(mapOf(
                "nurseId" to shortfall.get("nurseId").asString(), "shortfallMinutes" to shortfall.get("shortfallMinutes").asInt() + 1,
            )),
        )
        val mismatch = http.post("/api/v1/rosters/$rosterId/confirm", mapOf("acknowledgements" to acknowledgements), admin,
            idem() + ("If-Match" to etag))
        assertEquals(409, mismatch.status, mismatch.body)
        assertEquals("CONFIRM_ACK_MISMATCH", mismatch.code)

        val exact = acknowledgements + ("hourShortfalls" to listOf(mapOf(
            "nurseId" to shortfall.get("nurseId").asString(), "shortfallMinutes" to shortfall.get("shortfallMinutes").asInt(),
        )))
        val confirmed = http.post("/api/v1/rosters/$rosterId/confirm", mapOf("acknowledgements" to exact), admin,
            idem() + ("If-Match" to etag))
        assertEquals(200, confirmed.status, confirmed.body)

        val record = http.get("/api/v1/rosters/$rosterId/confirmation", admin)
        assertEquals(200, record.status, record.body)
        assertEquals(confirmed.json!!.get("revision").asInt(), record.json!!.get("revision").asInt())
        assertEquals(1, record.json!!.get("generalUnfilled").get("count").asInt())
        assertEquals(shortfall.get("shortfallMinutes").asInt(), record.json!!.get("hourShortfalls").get("totalShortfallMinutes").asInt())
        assertEquals(403, http.get("/api/v1/rosters/$rosterId/confirmation", nurse).status)
        // The nurse's published calendar carries no acknowledgement or hours data.
        val calendar = http.get("/api/v1/wards/$wardId/published-rosters?month=2026-12", nurse)
        assertTrue(!calendar.body.contains("shortfall") && !calendar.body.contains("acknowledg"), calendar.body)
    }
}
