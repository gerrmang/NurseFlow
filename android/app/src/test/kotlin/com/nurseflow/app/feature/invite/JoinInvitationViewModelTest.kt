package com.nurseflow.app.feature.invite

import com.nurseflow.app.core.network.InvitationRoles
import com.nurseflow.app.core.network.Items
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class JoinInvitationViewModelTest {
    private val api = FakeInvitationApi()
    private val wards = FakeWardApi()
    private val store = PendingInvitationStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var sessionEnded = 0
    private val model = JoinInvitationViewModel(
        api, wards, store, scope,
        today = { YearMonth.of(2026, 11) },
        onSessionInvalid = { sessionEnded++; store.clear() }, // AppContainer.logout() clears the invitation
    )
    private fun token(c: Char) = InviteToken.of(c.toString().repeat(43))!!
    private fun ready(role: String = InvitationRoles.MEMBER): JoinState.Ready {
        store.offer(token('a'))
        api.previews.last().second.complete(preview(role = role))
        return model.state.value as JoinState.Ready
    }

    @Test fun idleWithoutInvitationAndPreviewsWithTokenInBody() {
        assertEquals(JoinState.Idle, model.state.value)
        store.offer(token('a'))
        assertEquals(JoinState.Loading, model.state.value)
        assertEquals("a".repeat(43), api.previews.single().first)
        api.previews.single().second.complete(preview(links = true))
        assertTrue((model.state.value as JoinState.Ready).preview.linksExistingNurse)
    }

    @Test fun memberJoinSendsValidatedMonthAndAdminSendsNone() {
        ready()
        model.redeem("")
        assertEquals(InviteFailure.JOIN_MONTH_REQUIRED, (model.state.value as JoinState.Ready).error)
        model.redeem("2026-12") // future month
        assertEquals(InviteFailure.INVALID_JOIN_MONTH, (model.state.value as JoinState.Ready).error)
        model.redeem("2020-3")
        assertEquals(InviteFailure.INVALID_JOIN_MONTH, (model.state.value as JoinState.Ready).error)
        assertTrue(api.redeems.isEmpty())
        model.redeem(" 2020-03 ")
        assertEquals("2020-03", api.redeems.single().first.hospitalJoinedOn)
        api.redeems.single().second.complete(membership("ward-1"))
        assertEquals(JoinState.Joined("ward-1"), model.state.value)

        store.clear()
        ready(InvitationRoles.ADMIN)
        model.redeem("ignored")
        assertNull(api.redeems.last().first.hospitalJoinedOn)
    }

    @Test fun duplicateTapsSendOneJoinRequest() {
        ready()
        model.redeem("2020-03")
        model.redeem("2020-03")
        store.offer(token('a')) // the same link opened again keeps the in-flight join
        assertEquals(1, api.redeems.size)
        assertTrue((model.state.value as JoinState.Ready).submitting)
    }

    @Test fun newerInvitationDropsLatePreviewAndJoinResponses() {
        store.offer(token('a'))
        val stalePreview = api.previews.single().second
        store.offer(token('b'))
        stalePreview.complete(preview(wardId = "stale"))
        assertEquals(JoinState.Loading, model.state.value)
        api.previews.last().second.complete(preview(wardId = "ward-b"))
        model.redeem("2020-03")
        val staleJoin = api.redeems.single().second
        store.offer(token('c'))
        staleJoin.complete(membership("ward-b"))
        assertEquals(JoinState.Loading, model.state.value)
        assertEquals("c".repeat(43), api.previews.last().first)
    }

    @Test fun clearingInvitationOnLogoutDropsInFlightJoin() {
        ready()
        model.redeem("2020-03")
        store.clear() // AppContainer clears on logout or account change
        api.redeems.single().second.complete(membership("ward-1"))
        assertEquals(JoinState.Idle, model.state.value)
    }

    @Test fun lostJoinResponseIsCheckedBeforeAnyResend() {
        ready()
        model.redeem("2020-03")
        api.redeems.single().second.completeExceptionally(network())
        assertTrue(model.state.value is JoinState.Uncertain)
        model.redeem("2020-03") // not Ready: nothing is resent
        assertEquals(1, api.redeems.size)
        model.checkResult()
        wards.wardCalls.single().complete(Items(listOf(membership("ward-1"))))
        assertEquals(JoinState.Joined("ward-1"), model.state.value)
        assertEquals(1, api.redeems.size)
    }

    @Test fun lostJoinThatDidNotHappenReturnsToReadyForExplicitRetry() {
        ready()
        model.redeem("2020-03")
        api.redeems.single().second.completeExceptionally(apiError(503, null))
        model.checkResult()
        wards.wardCalls.single().complete(Items(emptyList()))
        api.previews.last().second.complete(preview())
        val state = model.state.value as JoinState.Ready
        assertNotNull(state.notice)
        assertEquals(1, api.redeems.size)
    }

    @Test fun alreadyRedeemedAfterLostResponseCountsAsJoinedOnlyForOwnMembership() {
        ready()
        model.redeem("2020-03")
        api.redeems.single().second.completeExceptionally(apiError(409, "INVITATION_ALREADY_REDEEMED"))
        wards.wardCalls.single().complete(Items(listOf(membership("ward-1"))))
        assertEquals(JoinState.Joined("ward-1"), model.state.value)

        store.clear()
        ready()
        model.redeem("2020-03")
        api.redeems.last().second.completeExceptionally(apiError(409, "INVITATION_ALREADY_REDEEMED"))
        wards.wardCalls.last().complete(Items(emptyList()))
        assertEquals(JoinState.Failed(InviteFailure.ALREADY_REDEEMED), model.state.value)
    }

    @Test fun terminalAndConflictErrorsAreDistinct() {
        mapOf(
            "INVITATION_EXPIRED" to InviteFailure.EXPIRED,
            "INVITATION_REVOKED" to InviteFailure.REVOKED,
            "INVITATION_NOT_FOUND" to InviteFailure.NOT_FOUND,
        ).forEach { (code, failure) ->
            store.clear()
            store.offer(token('a'))
            api.previews.last().second.completeExceptionally(apiError(410, code))
            assertEquals(JoinState.Failed(failure), model.state.value)
            assertTrue(failure.endsInvitation)
        }
        store.clear()
        ready()
        model.redeem("2020-03")
        api.redeems.last().second.completeExceptionally(apiError(409, "ALREADY_WARD_MEMBER"))
        assertEquals(JoinState.AlreadyMember("ward-1"), model.state.value)

        store.clear()
        ready()
        model.redeem("2020-03")
        api.redeems.last().second.completeExceptionally(apiError(409, "NURSE_ALREADY_LINKED"))
        assertEquals(InviteFailure.NURSE_ALREADY_LINKED, (model.state.value as JoinState.Ready).error)

        store.clear()
        ready(InvitationRoles.ADMIN)
        model.redeem("")
        api.redeems.last().second.completeExceptionally(apiError(409, "ADMIN_NURSE_RECORD_CONFLICT"))
        assertEquals(InviteFailure.ADMIN_NURSE_CONFLICT, (model.state.value as JoinState.Ready).error)
    }

    @Test fun previewNetworkErrorIsRetryableAndCloseClearsOnlyThisInvitation() {
        store.offer(token('a'))
        api.previews.single().second.completeExceptionally(network())
        assertEquals(JoinState.Failed(InviteFailure.NETWORK), model.state.value)
        model.retryPreview()
        assertEquals(2, api.previews.size)
        api.previews.last().second.complete(preview())
        model.close()
        assertNull(store.current.value)
        assertEquals(JoinState.Idle, model.state.value)
    }

    @Test fun serverRejectedJoinMonthKeepsFormEditableAndAllowsCorrection() {
        ready()
        model.redeem("1960-01") // passes the client precheck; the server is the final judge
        api.redeems.single().second.completeExceptionally(apiError(400, "INVALID_HOSPITAL_JOIN_MONTH"))
        val rejected = model.state.value as JoinState.Ready
        assertEquals(InviteFailure.INVALID_JOIN_MONTH, rejected.error)
        assertFalse("The month field must stay editable", rejected.submitting)
        assertNotNull("The invitation stays open for correction", store.current.value)
        model.redeem("2021-07")
        assertEquals(listOf("1960-01", "2021-07"), api.redeems.map { it.first.hospitalJoinedOn })
        api.redeems.last().second.complete(membership("ward-1"))
        assertEquals(JoinState.Joined("ward-1"), model.state.value)
        assertEquals(0, sessionEnded)
    }

    @Test fun userNotFoundOnJoinEndsSessionInsteadOfShowingRetryableError() {
        ready()
        model.redeem("2020-03")
        api.redeems.single().second.completeExceptionally(apiError(401, "USER_NOT_FOUND"))
        assertEquals(1, sessionEnded)
        assertNull(store.current.value)
        assertEquals(JoinState.Idle, model.state.value)
        assertEquals(1, api.redeems.size) // never resent
    }

    @Test fun userNotFoundOnPreviewAndResultCheckAlsoEndsSession() {
        store.offer(token('a'))
        api.previews.single().second.completeExceptionally(apiError(401, "USER_NOT_FOUND"))
        assertEquals(1, sessionEnded)
        assertNull(store.current.value)

        ready()
        model.redeem("2020-03")
        api.redeems.single().second.completeExceptionally(network())
        model.checkResult()
        wards.wardCalls.single().completeExceptionally(apiError(401, "USER_NOT_FOUND"))
        assertEquals(2, sessionEnded)
        assertEquals(JoinState.Idle, model.state.value)
    }

    @Test fun genericUnauthorizedDoesNotForceLogoutBecauseRefreshFailureAlreadyEndsSession() {
        ready()
        model.redeem("2020-03")
        api.redeems.single().second.completeExceptionally(apiError(401, "UNAUTHORIZED"))
        assertEquals(InviteFailure.UNAUTHORIZED, (model.state.value as JoinState.Ready).error)
        assertEquals(0, sessionEnded)
    }

    @Test fun clearedViewModelCancelsWork() {
        ready()
        model.redeem("2020-03")
        scope.cancel()
        api.redeems.single().second.complete(membership("ward-1"))
        assertTrue((model.state.value as JoinState.Ready).submitting)
    }
}
