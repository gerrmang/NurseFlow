package com.nurseflow.offrequest

import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class OffRequestHttpTest : ApiTestBase() {
    // Its deadline (2031-02-15, end of day in the ward zone) is still ahead; 2026-11's has passed.
    private val openMonth = "2031-03"
    private val closedMonth = "2026-11"

    private data class Ward(val id: UUID, val adminToken: String, val nurse: UUID, val nurseToken: String)

    private fun ward(): Ward {
        val ward = insertWard()
        val (admin, adminToken) = userToken()
        val (member, memberToken) = userToken()
        grant(ward, admin, "WARD_ADMIN")
        grant(ward, member, "WARD_MEMBER")
        return Ward(ward, adminToken, insertNurse(ward, subject = member.toString()), memberToken)
    }

    private fun path(ward: Ward) = "/api/v1/wards/${ward.id}/off-requests"

    @Test
    fun `nurse submits own requests within the monthly limit and withdraws before the deadline`() {
        val w = ward()
        val first = http.post(path(w), mapOf("date" to "$openMonth-02"), w.nurseToken)
        assertEquals(201, first.status, first.body)
        assertEquals("SUBMITTED", first.json!!.get("status").asString())
        assertEquals("NURSE", first.json!!.get("source").asString())
        assertEquals(w.nurse.toString(), first.json!!.get("nurseId").asString())
        assertFalse(first.json!!.get("afterDeadline").asBoolean())
        assertFalse(first.body.contains("submittedBy"))
        assertEquals("OFF_REQUEST_DUPLICATE", http.post(path(w), mapOf("date" to "$openMonth-02"), w.nurseToken).code)
        // Another nurse's record cannot be named by a nurse.
        val other = insertNurse(w.id)
        assertEquals(403, http.post(path(w), mapOf("date" to "$openMonth-03", "nurseId" to other), w.nurseToken).status)
        (3..5).forEach { assertEquals(201, http.post(path(w), mapOf("date" to "$openMonth-0$it"), w.nurseToken).status) }
        val limited = http.post(path(w), mapOf("date" to "$openMonth-06"), w.nurseToken)
        assertEquals(409, limited.status)
        assertEquals("OFF_REQUEST_LIMIT_REACHED", limited.code)
        // The limit binds the nurse only; an admin may still enter a day for them.
        assertEquals(201, http.post(path(w), mapOf("date" to "$openMonth-06", "nurseId" to w.nurse), w.adminToken).status)

        val withdrawn = http.post("${path(w)}/${first.json!!.get("requestId").asString()}/withdraw", null, w.nurseToken)
        assertEquals("WITHDRAWN", withdrawn.json!!.get("status").asString(), withdrawn.body)
        // The withdrawn day is free again, but the admin's entry keeps the count at the limit.
        assertEquals("OFF_REQUEST_LIMIT_REACHED", http.post(path(w), mapOf("date" to "$openMonth-02"), w.nurseToken).code)

        val list = http.get("${path(w)}?month=$openMonth", w.nurseToken)
        assertEquals(200, list.status, list.body)
        assertEquals(5, list.json!!.get("items").size())
        assertEquals(4, list.json!!.get("monthlyLimit").asInt())
        assertEquals("2031-02-15T15:00:00Z", list.json!!.get("deadline").asString())
        assertEquals("private, no-store", list.header("Cache-Control"))
        // A nurse sees only their own requests; an admin sees the ward's.
        val (peer, _) = userToken()
        grant(w.id, peer, "WARD_MEMBER")
        val peerNurse = insertNurse(w.id, subject = peer.toString())
        assertEquals(201, http.post(path(w), mapOf("date" to "$openMonth-09", "nurseId" to peerNurse), w.adminToken).status)
        assertEquals(5, http.get("${path(w)}?month=$openMonth", w.nurseToken).json!!.get("items").size())
        assertEquals(6, http.get("${path(w)}?month=$openMonth", w.adminToken).json!!.get("items").size())
        assertEquals("INVALID_MONTH", http.get("${path(w)}?month=2031-3", w.adminToken).code)
    }

    @Test
    fun `after the deadline a nurse leaves an exception request and only the admin can decide it`() {
        val w = ward()
        val late = http.post(path(w), mapOf("date" to "$closedMonth-20"), w.nurseToken)
        assertEquals(201, late.status, late.body)
        assertTrue(late.json!!.get("afterDeadline").asBoolean())
        val id = late.json!!.get("requestId").asString()
        val nurseWithdraw = http.post("${path(w)}/$id/withdraw", null, w.nurseToken)
        assertEquals("OFF_REQUEST_DEADLINE_PASSED", nurseWithdraw.code)
        assertEquals(403, http.post("${path(w)}/$id/confirm", null, w.nurseToken).status)

        val confirmed = http.post("${path(w)}/$id/confirm", null, w.adminToken)
        assertEquals("CONFIRMED", confirmed.json!!.get("status").asString(), confirmed.body)
        assertTrue(confirmed.json!!.has("decidedAt"))
        assertEquals("OFF_REQUEST_STATE_CONFLICT", http.post("${path(w)}/$id/reject", null, w.adminToken).code)
        assertEquals("OFF_REQUEST_STATE_CONFLICT", http.post("${path(w)}/$id/confirm", null, w.adminToken).code)
        assertEquals("OFF_REQUEST_DUPLICATE", http.post(path(w), mapOf("date" to "$closedMonth-20", "nurseId" to w.nurse), w.adminToken).code)
        assertEquals("WITHDRAWN", http.post("${path(w)}/$id/withdraw", null, w.adminToken).json!!.get("status").asString())
        assertEquals("OFF_REQUEST_STATE_CONFLICT", http.post("${path(w)}/$id/withdraw", null, w.adminToken).code)

        val adminEntry = http.post(path(w), mapOf("date" to "$closedMonth-21", "nurseId" to w.nurse), w.adminToken)
        assertEquals("ADMIN", adminEntry.json!!.get("source").asString())
        assertTrue(adminEntry.json!!.get("afterDeadline").asBoolean())
        val rejected = http.post("${path(w)}/${adminEntry.json!!.get("requestId").asString()}/reject", null, w.adminToken)
        assertEquals("REJECTED", rejected.json!!.get("status").asString())
        // A rejected day is no longer held, so it can be requested again.
        assertEquals(201, http.post(path(w), mapOf("date" to "$closedMonth-21", "nurseId" to w.nurse), w.adminToken).status)
    }

    @Test
    fun `requests stay inside their ward and need a rosterable nurse`() {
        val w = ward()
        val other = ward()
        assertEquals(400, http.post(path(w), mapOf("date" to "$openMonth-02"), w.adminToken).status)
        assertEquals("NURSE_NOT_FOUND", http.post(path(w), mapOf("date" to "$openMonth-02", "nurseId" to other.nurse), w.adminToken).code)
        val inactive = insertNurse(w.id, active = false)
        assertEquals("NURSE_NOT_FOUND", http.post(path(w), mapOf("date" to "$openMonth-02", "nurseId" to inactive), w.adminToken).code)
        // A member account without a linked nurse record has no own requests to read.
        val (unlinked, unlinkedToken) = userToken()
        grant(w.id, unlinked, "WARD_MEMBER")
        assertEquals("NURSE_IDENTITY_REQUIRED", http.get("${path(w)}?month=$openMonth", unlinkedToken).code)
        assertEquals(404, http.get("${path(w)}?month=$openMonth", other.nurseToken).status)
        val created = http.post(path(other), mapOf("date" to "$openMonth-02"), other.nurseToken)
        val foreign = created.json!!.get("requestId").asString()
        assertEquals(404, http.post("${path(w)}/$foreign/confirm", null, w.adminToken).status)
        // Another nurse of the same ward cannot withdraw it either.
        val (peer, peerToken) = userToken()
        grant(other.id, peer, "WARD_MEMBER")
        insertNurse(other.id, subject = peer.toString())
        assertEquals("OFF_REQUEST_NOT_FOUND", http.post("${path(other)}/$foreign/withdraw", null, peerToken).code)
    }

    @Test
    fun `policy is admin only revisioned and accepts whole numbers in range`() {
        val w = ward()
        val policy = "${path(w)}/policy"
        val initial = http.get(policy, w.adminToken)
        assertEquals(200, initial.status, initial.body)
        assertEquals(4, initial.json!!.get("monthlyLimit").asInt())
        assertEquals(15, initial.json!!.get("deadlineDay").asInt())
        val tag = mapOf("If-Match" to initial.header("ETag")!!)
        assertEquals(403, http.get(policy, w.nurseToken).status)
        fun put(limit: Any, day: Any, headers: Map<String, String> = tag, token: String = w.adminToken) =
            http.put(policy, mapOf("monthlyLimit" to limit, "deadlineDay" to day), token, headers)
        assertEquals(403, put(5, 15, token = w.nurseToken).status)
        assertEquals(400, put(-1, 15).status)
        assertEquals(400, put(32, 15).status)
        assertEquals(400, put(4, 29).status)
        assertEquals(400, put(4, 0).status)
        assertEquals(400, put(4.5, 15).status)
        assertEquals(400, put("5", 15).status)
        assertEquals(initial.header("ETag"), http.get(policy, w.adminToken).header("ETag"))

        val draft = UUID.randomUUID()
        jdbc.update("INSERT INTO rosters(roster_id,ward_id,roster_month,status,active,revision) VALUES(?,?,?,'DRAFT',FALSE,1)",
            draft, w.id, openMonth)
        val changed = put(1, 10)
        assertEquals(200, changed.status, changed.body)
        assertTrue(changed.header("ETag") != initial.header("ETag"))
        assertEquals(412, put(2, 10).status)
        assertEquals(changed.header("ETag"), put(1, 10, mapOf("If-Match" to changed.header("ETag")!!)).header("ETag"))
        // The settings gate new entries only; drafts are unaffected.
        assertEquals(1, jdbc.queryForObject("SELECT revision FROM rosters WHERE roster_id=?", Int::class.java, draft))
        val list = http.get("${path(w)}?month=$openMonth", w.nurseToken)
        assertEquals(1, list.json!!.get("monthlyLimit").asInt())
        assertEquals("2031-02-10T15:00:00Z", list.json!!.get("deadline").asString())
        assertEquals(201, http.post(path(w), mapOf("date" to "$openMonth-02"), w.nurseToken).status)
        assertEquals("OFF_REQUEST_LIMIT_REACHED", http.post(path(w), mapOf("date" to "$openMonth-03"), w.nurseToken).code)
    }

    @Test
    fun `confirming or withdrawing a confirmed request invalidates only that month's drafts`() {
        val w = ward()
        fun roster(month: String, status: String) = UUID.randomUUID().also {
            jdbc.update("INSERT INTO rosters(roster_id,ward_id,roster_month,status,active,revision) VALUES(?,?,?,?,?,1)",
                it, w.id, month, status, status == "PUBLISHED")
        }
        val draft = roster(openMonth, "DRAFT")
        val otherMonth = roster("2031-04", "DRAFT")
        val published = roster(openMonth, "PUBLISHED")
        fun revision(id: UUID) = jdbc.queryForObject("SELECT revision FROM rosters WHERE roster_id=?", Int::class.java, id)
        val id = http.post(path(w), mapOf("date" to "$openMonth-02"), w.nurseToken).json!!.get("requestId").asString()
        assertEquals(1, revision(draft))
        http.post("${path(w)}/$id/confirm", null, w.adminToken)
        assertEquals(2, revision(draft))
        assertTrue(jdbc.queryForObject("SELECT needs_revalidation FROM rosters WHERE roster_id=?", Boolean::class.java, draft)!!)
        // The nurse can no longer withdraw a confirmed day; the admin can, and that is another change for drafts.
        assertEquals("OFF_REQUEST_STATE_CONFLICT", http.post("${path(w)}/$id/withdraw", null, w.nurseToken).code)
        http.post("${path(w)}/$id/withdraw", null, w.adminToken)
        assertEquals(3, revision(draft))
        assertEquals(1, revision(otherMonth))
        assertEquals(1, revision(published))
        // Rejecting an undecided request changes no hard rule.
        val rejected = http.post(path(w), mapOf("date" to "$openMonth-03"), w.nurseToken).json!!.get("requestId").asString()
        http.post("${path(w)}/$rejected/reject", null, w.adminToken)
        assertEquals(3, revision(draft))
    }
}
