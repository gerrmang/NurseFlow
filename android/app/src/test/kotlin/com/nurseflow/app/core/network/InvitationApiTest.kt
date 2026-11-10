package com.nurseflow.app.core.network

import com.nurseflow.app.core.session.MemoryTokenStore
import com.nurseflow.app.core.session.SessionManager
import com.nurseflow.app.feature.invite.InviteFailure
import com.nurseflow.app.feature.invite.toInviteFailure
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class InvitationApiTest {
    private val token = "T".repeat(43) // fictional
    private val invitation = """{"invitationId":"inv-1","wardId":"ward-1","role":"WARD_MEMBER","nurseId":"nurse-1","status":"PENDING","createdAt":"2026-11-10T00:00:00Z","expiresAt":"2026-11-11T00:00:00Z"}"""
    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
    private fun api(server: MockWebServer): InvitationApi {
        val base = server.url("/").toString()
        return ApiFactory.invitationApi(base, SessionManager(ApiFactory.authApi(base), MemoryTokenStore()))
    }
    private fun body(server: MockWebServer): Pair<String, JsonObject?> {
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        val text = request.body.readUtf8()
        return "${request.method} ${request.path}" to text.takeIf { it.isNotEmpty() }?.let { Json.parseToJsonElement(it).jsonObject }
    }

    @Test fun adminPathsMethodsAndBodies() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json("""{"invitation":$invitation,"token":"$token","inviteUrl":"nurseflow://invite#token=$token"}""", 201))
            server.enqueue(json("""{"invitation":${invitation.replace("nurse-1", "x").replace("\"nurseId\":\"x\",", "").replace("WARD_MEMBER", "WARD_ADMIN")},"token":"$token"}""", 201))
            server.enqueue(json("""{"items":[$invitation]}"""))
            server.enqueue(json("""{"invitation":$invitation,"token":"$token"}""", 201))
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(json("""{"items":[{"nurseId":"nurse-1","displayName":"가상 간호사","qualifications":[],"active":true,"linkedAccount":false,"profile":{"status":"NOT_SUBMITTED"}}],"page":1,"pageSize":1,"totalItems":1}"""))
            val api = api(server)
            val created = api.create("ward-1", CreateInvitationBody(InvitationRoles.MEMBER, "nurse-1"))
            assertEquals("nurseflow://invite#token=$token", created.inviteUrl)
            assertFalse(created.toString().contains(token))
            assertNull(api.create("ward-1", CreateInvitationBody(InvitationRoles.ADMIN)).inviteUrl)
            assertEquals("PENDING", api.list("ward-1").items.single().status)
            api.reissue("ward-1", "inv-1")
            api.revoke("ward-1", "inv-1")
            assertFalse(api.staff("ward-1").items.single().linkedAccount)

            val (memberLine, member) = body(server)
            assertEquals("POST /api/v1/wards/ward-1/invitations", memberLine)
            assertEquals(setOf("role", "nurseId"), member!!.keys)
            val (_, admin) = body(server)
            assertEquals(setOf("role"), admin!!.keys) // WARD_ADMIN never sends nurseId, and no expiresInHours
            assertEquals("GET /api/v1/wards/ward-1/invitations" to null, body(server))
            assertEquals("POST /api/v1/wards/ward-1/invitations/inv-1/reissue", body(server).first)
            assertEquals("DELETE /api/v1/wards/ward-1/invitations/inv-1" to null, body(server))
            assertEquals("GET /api/v1/wards/ward-1/staff?includeInactive=false" to null, body(server))
        }
    }

    @Test fun previewAndRedeemSendTokenOnlyInBody() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json("""{"wardId":"ward-1","wardName":"가상 병동","role":"WARD_MEMBER","expiresAt":"2026-11-11T00:00:00Z","linksExistingNurse":true}"""))
            server.enqueue(json("""{"wardId":"ward-1","wardName":"가상 병동","roles":["WARD_MEMBER"],"canViewWardCalendar":true}""", 201))
            server.enqueue(json("""{"wardId":"ward-2","wardName":"가상 병동2","roles":["WARD_ADMIN"],"canViewWardCalendar":true}""", 201))
            val api = api(server)
            assertTrue(api.preview(InvitationTokenBody(token)).linksExistingNurse)
            assertEquals("ward-1", api.redeem(RedeemInvitationBody(token, "2020-03")).wardId)
            assertEquals("ward-2", api.redeem(RedeemInvitationBody(token)).wardId)
            val (previewLine, preview) = body(server)
            assertEquals("POST /api/v1/invitations/preview", previewLine)
            assertEquals(setOf("token"), preview!!.keys)
            val (redeemLine, member) = body(server)
            assertEquals("POST /api/v1/invitations/redeem", redeemLine)
            assertFalse(redeemLine.contains(token))
            assertEquals(setOf("token", "hospitalJoinedOn"), member!!.keys)
            assertEquals(setOf("token"), body(server).second!!.keys) // WARD_ADMIN: no joined month, rank or career fields
            assertFalse(RedeemInvitationBody(token, "2020-03").toString().contains(token))
            assertFalse(InvitationTokenBody(token).toString().contains(token))
        }
    }

    @Test fun lostWriteResponseIsNotRetriedAutomatically() = runBlocking {
        MockWebServer().use { server ->
            // A prior call leaves a connection a retrying client would reuse and then replay on.
            server.enqueue(json("""{"wardId":"ward-1","wardName":"가상 병동","role":"WARD_MEMBER","expiresAt":"2026-11-11T00:00:00Z","linksExistingNurse":false}"""))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(json("""{"wardId":"ward-1","wardName":"가상 병동","roles":["WARD_MEMBER"],"canViewWardCalendar":true}""", 201))
            val api = api(server)
            api.preview(InvitationTokenBody(token))
            val failure = try {
                apiCall { api.redeem(RedeemInvitationBody(token, "2020-03")) }
                null
            } catch (e: ApiException) { e.toInviteFailure() }
            assertEquals(InviteFailure.NETWORK, failure)
            assertEquals("The join request must be sent exactly once", 2, server.requestCount)
            server.takeRequest(1, TimeUnit.SECONDS)
            assertEquals("Writes use a fresh connection", 0, server.takeRequest(1, TimeUnit.SECONDS)!!.sequenceNumber)
        }
    }

    @Test fun errorsAreClassifiedByCodeNotOnlyStatus() = runBlocking {
        val cases = listOf(
            Triple(410, "INVITATION_EXPIRED", InviteFailure.EXPIRED),
            Triple(410, "INVITATION_REVOKED", InviteFailure.REVOKED),
            Triple(409, "INVITATION_ALREADY_REDEEMED", InviteFailure.ALREADY_REDEEMED),
            Triple(404, "INVITATION_NOT_FOUND", InviteFailure.NOT_FOUND),
            Triple(409, "ALREADY_WARD_MEMBER", InviteFailure.ALREADY_MEMBER),
            Triple(409, "NURSE_ALREADY_LINKED", InviteFailure.NURSE_ALREADY_LINKED),
            Triple(409, "ADMIN_NURSE_RECORD_CONFLICT", InviteFailure.ADMIN_NURSE_CONFLICT),
            Triple(409, "INVITATION_ALREADY_PENDING", InviteFailure.ALREADY_PENDING),
            Triple(400, "HOSPITAL_JOIN_MONTH_REQUIRED", InviteFailure.JOIN_MONTH_REQUIRED),
            Triple(400, "INVALID_HOSPITAL_JOIN_MONTH", InviteFailure.INVALID_JOIN_MONTH),
            Triple(403, "WARD_ADMIN_REQUIRED", InviteFailure.NOT_ADMIN),
            Triple(404, "WARD_NOT_FOUND", InviteFailure.NOT_ADMIN),
            Triple(404, "NURSE_NOT_FOUND", InviteFailure.NURSE_NOT_FOUND),
            Triple(503, null, InviteFailure.SERVER),
        )
        MockWebServer().use { server ->
            cases.forEach { (status, code, _) ->
                server.enqueue(json(code?.let { """{"code":"$it","detail":"untrusted detail"}""" } ?: "{}", status))
            }
            val api = api(server)
            cases.forEach { (_, code, expected) ->
                val failure = try { apiCall { api.preview(InvitationTokenBody(token)) }; null } catch (e: Exception) { e.toInviteFailure() }
                assertEquals(code, expected, failure)
                assertFalse(failure!!.message.contains("untrusted"))
            }
        }
    }
}
