package com.nurseflow.app.feature.offrequest

import com.nurseflow.app.core.network.*
import kotlinx.coroutines.*
import okhttp3.Headers.Companion.toHeaders
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.time.*

internal class FakeOffApi : OffRequestApi {
    var rows = emptyList<OffRequest>()
    var limit = 4
    var day = 15
    var revision = 0
    var writes = 0
    var lastInput: OffRequestInput? = null
    var lastEtag: String? = null
    var listRead: suspend (String) -> Unit = {}
    var policyRead: suspend () -> Unit = {}
    var reads = 0
    var write: suspend () -> Unit = {}
    var brokenResult = false
    var deadline = "2026-11-16T00:00:00Z"
    override suspend fun list(ward: String, month: String): OffRequestList {
        reads++; listRead(month)
        return OffRequestList(rows.filter { it.date.startsWith(month) }, deadline, limit)
    }
    override suspend fun submit(ward: String, input: OffRequestInput): OffRequest {
        writes++; lastInput = input
        val row = row("r$writes", input.date).copy(source = if (input.nurseId == null) "NURSE" else "ADMIN", nurseId = input.nurseId ?: "n1")
        rows = rows + row
        write()
        return if (brokenResult) row.copy(date = "2026-12-31") else row
    }
    private suspend fun decide(id: String, status: String): OffRequest {
        writes++; write()
        val item = rows.first { it.requestId == id }.copy(status = status, decidedAt = "2026-11-01T00:00:00Z")
        rows = rows.map { if (it.requestId == id) item else it }
        return item
    }
    override suspend fun confirm(ward: String, id: String) = decide(id, "CONFIRMED")
    override suspend fun reject(ward: String, id: String) = decide(id, "REJECTED")
    override suspend fun withdraw(ward: String, id: String) = decide(id, "WITHDRAWN")
    override suspend fun policy(ward: String): Response<OffRequestPolicy> {
        policyRead()
        return Response.success(OffRequestPolicy(limit, day, revision), mapOf("ETag" to "\"policy-$revision\"").toHeaders())
    }
    override suspend fun savePolicy(ward: String, etag: String, input: OffRequestPolicyInput): Response<OffRequestPolicy> {
        writes++; lastEtag = etag; write(); limit = input.monthlyLimit; day = input.deadlineDay; revision++
        return policy(ward)
    }
    override suspend fun eligibility(ward: String) = OffEligibility(false, revision, listOf(OffEligibleNurse("n1", "가상 간호사", true, "ELIGIBLE"), OffEligibleNurse("n2", "제외된 가상 간호사", false, "INACTIVE")))
    companion object {
        fun row(id: String = "r1", date: String = "2026-12-10") = OffRequest(id, "n1", date, "SUBMITTED", "NURSE", "2026-11-01T00:00:00Z", afterDeadline = false)
    }
}

class OffRequestViewModelTest {
    private val api = FakeOffApi()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var access = true
    private var valid = true
    private var expired = 0
    private var accessRead: suspend () -> Unit = {}
    private fun model(admin: Boolean = false, instant: String = "2026-11-15T23:59:59Z") = OffRequestViewModel(api, "w1", admin, {
        accessRead()
        if (access) listOf(MembershipDto("w1", "가상 병동", listOf(if (admin) "WARD_ADMIN" else "WARD_MEMBER"), true)) else emptyList()
    }, { valid }, { expired++ }, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), YearMonth.of(2026, 12), scope)
    private fun open(admin: Boolean = false): OffRequestViewModel = model(admin).also { it.activate(); assertTrue(it.state.value.editable) }

    @Test fun nurseEntryUsesOwnListAndSubmitsWithoutTarget() {
        val vm = open(); assertNull(vm.state.value.data!!.policy)
        vm.submit("2026-12-10")
        assertNull(api.lastInput!!.nurseId); assertEquals("2026-12-10", vm.state.value.data!!.list.items.single().date)
        assertTrue(vm.state.value.editable)
    }
    @Test fun adminNeedsEligibleTargetAndIgnoresOwnLimit() {
        api.limit = 0; val vm = open(true)
        vm.submit("2026-12-10"); vm.submit("2026-12-10", "n2"); assertEquals(0, api.writes)
        vm.submit("2026-12-10", "n1"); assertEquals(1, api.writes); assertEquals("ADMIN", vm.state.value.data!!.list.items.single().source)
    }
    @Test fun zeroLimitBlocksNurseEvenAfterDeadline() {
        api.limit = 0; val vm = model(instant = "2026-11-17T00:00:00Z"); vm.activate(); vm.submit("2026-12-10"); assertEquals(0, api.writes)
    }
    @Test fun countIncludesOnlyNurseSubmittedAndConfirmed() {
        api.limit = 2
        api.rows = listOf(FakeOffApi.row(), FakeOffApi.row("r2", "2026-12-11").copy(status = "CONFIRMED"), FakeOffApi.row("r3").copy(status = "REJECTED"), FakeOffApi.row("r4").copy(status = "WITHDRAWN"), FakeOffApi.row("r5").copy(source = "ADMIN"))
        val vm = open(); assertEquals(2, ownCount(vm.state.value.data!!.list)); vm.submit("2026-12-12"); assertEquals(0, api.writes)
    }
    @Test fun lateRequestCanBeSubmittedButNotWithdrawn() {
        val vm = model(instant = "2026-11-16T00:00:00Z"); vm.activate(); vm.submit("2026-12-10")
        assertEquals(1, api.writes); vm.decide("r1", "withdraw"); assertEquals(1, api.writes)
    }
    @Test fun deadlineBeforeAndExactlyAtInstant() {
        api.rows = listOf(FakeOffApi.row()); val early = open()
        assertTrue(early.canDecide(api.rows.single(), "withdraw"))
        val at = model(instant = "2026-11-16T00:00:00Z"); at.activate()
        assertFalse(at.canDecide(api.rows.single(), "withdraw")); assertFalse(at.canDecide(api.rows.single(), "confirm"))
    }
    @Test fun nurseCannotConfirmRejectOrWithdrawConfirmed() {
        api.rows = listOf(FakeOffApi.row().copy(status = "CONFIRMED")); val vm = open()
        for (action in listOf("confirm", "reject", "withdraw")) vm.decide("r1", action)
        assertEquals(0, api.writes)
    }
    @Test fun adminConfirmThenWithdrawReloadsEveryTransition() {
        api.rows = listOf(FakeOffApi.row()); val vm = open(true)
        vm.decide("r1", "confirm"); assertEquals("CONFIRMED", vm.state.value.data!!.list.items.single().status)
        vm.decide("r1", "reject"); assertEquals(1, api.writes)
        vm.decide("r1", "withdraw"); assertEquals("WITHDRAWN", vm.state.value.data!!.list.items.single().status)
    }
    @Test fun rejectionAndWithdrawalHistoryCannotBeDecidedAgain() {
        api.rows = listOf(FakeOffApi.row()); val vm = open(true); vm.decide("r1", "reject")
        for (action in listOf("confirm", "reject", "withdraw")) vm.decide("r1", action)
        assertEquals(1, api.writes); assertEquals("REJECTED", vm.state.value.data!!.list.items.single().status)
    }
    @Test fun badDateOtherMonthAndDuplicateAreBlocked() {
        api.rows = listOf(FakeOffApi.row()); val vm = open()
        for (date in listOf("2026-12-10", "2026-12-32", "2026-11-30", "2026-12-1", "nonsense")) vm.submit(date)
        vm.submit("2026-12-11", "n1"); assertEquals(0, api.writes)
    }
    @Test fun rejectedDateMayBeRequestedAgain() {
        api.rows = listOf(FakeOffApi.row("old").copy(status = "REJECTED")); val vm = open(); vm.submit("2026-12-10"); assertEquals(1, api.writes)
    }
    @Test fun policyUsesExactEtagAndValidRanges() {
        val vm = open(true)
        for ((l, d) in listOf("-1" to "15", "32" to "15", "4" to "0", "4" to "29", "x" to "1")) vm.savePolicy(l, d)
        assertEquals(0, api.writes); vm.savePolicy("0", "28")
        assertEquals("\"policy-0\"", api.lastEtag); assertEquals(0, vm.state.value.data!!.list.monthlyLimit)
        assertEquals(28, vm.state.value.data!!.policy!!.value.deadlineDay)
    }
    @Test fun nurseNeverSavesPolicy() { val vm = open(); vm.savePolicy("3", "12"); assertEquals(0, api.writes) }
    @Test fun lostWriteResponseNeedsExplicitAcknowledgement() {
        val vm = open(); api.write = { throw java.io.IOException("fictional disconnect") }; vm.submit("2026-12-10")
        assertTrue(vm.state.value.uncertain); assertFalse(vm.state.value.editable); assertFalse(vm.state.value.stale)
        vm.submit("2026-12-11"); vm.changeMonth(1); assertEquals(1, api.writes); assertEquals(12, vm.state.value.month.monthValue)
        assertEquals(1, vm.state.value.data!!.list.items.size)
        vm.acknowledge(); assertTrue(vm.state.value.editable)
    }
    @Test fun serverFailureAndMalformedSuccessAreUnknown() {
        val vm = open(); api.write = { throw ApiException(503, "UNAVAILABLE", "fixed") }; vm.submit("2026-12-10"); assertTrue(vm.state.value.uncertain)
        vm.acknowledge(); api.write = {}; api.brokenResult = true; vm.submit("2026-12-11"); assertTrue(vm.state.value.uncertain)
    }
    @Test fun conflictReloadsWithoutUnknownOutcome() {
        val vm = open(); api.write = { throw ApiException(409, "OFF_REQUEST_STATE_CONFLICT", "fixed") }; vm.submit("2026-12-10")
        assertFalse(vm.state.value.uncertain); assertTrue(vm.state.value.editable); assertTrue(vm.state.value.notice!!.contains("상태가 변경"))
    }
    @Test fun policyConflictReloadsLatestSettings() {
        val vm = open(true); api.write = { api.limit = 7; throw ApiException(412, "STALE_POLICY_REVISION", "fixed") }
        vm.savePolicy("2", "12"); assertEquals(7, vm.state.value.data!!.policy!!.value.monthlyLimit); assertTrue(vm.state.value.notice!!.contains("다른 관리자"))
    }
    @Test fun postWriteReadFailureBlocksFurtherWritesUntilReload() {
        val vm = open(); api.write = { api.listRead = { throw java.io.IOException() } }; vm.submit("2026-12-10")
        assertTrue(vm.state.value.stale); assertFalse(vm.state.value.editable); vm.submit("2026-12-11"); assertEquals(1, api.writes)
        api.listRead = {}; vm.reload(); assertTrue(vm.state.value.editable)
    }
    @Test fun missingMembershipClearsDataAndPreventsWrites() {
        val vm = open(); access = false; vm.submit("2026-12-10")
        assertEquals(0, api.writes); assertTrue(vm.state.value.forbidden); assertNull(vm.state.value.data)
    }
    @Test fun forbiddenResponseClearsPreviousData() {
        val vm = open(); api.listRead = { throw ApiException(403, "WARD_ADMIN_REQUIRED", "fixed") }; vm.reload()
        assertTrue(vm.state.value.forbidden); assertNull(vm.state.value.data)
    }
    @Test fun unauthorizedEndsSessionAndClearsData() {
        val vm = open(); api.listRead = { throw ApiException(401, "UNAUTHORIZED", "fixed") }; vm.reload()
        assertEquals(1, expired); assertNull(vm.state.value.data)
    }
    @Test fun invalidSessionCannotSendQueuedWrite() { val vm = open(); valid = false; vm.submit("2026-12-10"); assertEquals(0, api.writes) }
    @Test fun roleChangeDuringMembershipCheckNeverWrites() {
        val vm = open(); accessRead = { valid = false }; vm.submit("2026-12-10"); assertEquals(0, api.writes)
    }
    @Test fun writesStaySerializedThroughPostWriteRead() = runBlocking {
        val vm = open(); val gate = CompletableDeferred<Unit>(); api.listRead = { gate.await() }
        vm.submit("2026-12-10"); assertTrue(vm.state.value.busy)
        vm.submit("2026-12-11"); vm.reload(); vm.changeMonth(1); assertEquals(1, api.writes)
        gate.complete(Unit); yield(); assertTrue(vm.state.value.editable)
    }
    @Test fun leavingPendingWriteRetainsUnknownOutcomeOnReentry() = runBlocking {
        val vm = open(); val gate = CompletableDeferred<Unit>(); api.write = { gate.await() }
        vm.submit("2026-12-10"); vm.deactivate(); assertTrue(vm.state.value.uncertain); assertNull(vm.state.value.data)
        api.write = {}; vm.activate(); assertFalse(vm.state.value.editable); assertFalse(vm.state.value.stale)
        vm.acknowledge(); assertTrue(vm.state.value.editable)
    }
    @Test fun lateCancelledReadDoesNotReplaceNewMonth() = runBlocking {
        val vm = open(); val gate = CompletableDeferred<Unit>()
        api.listRead = { month -> if (month == "2026-12") withContext(NonCancellable) { gate.await() } }
        vm.reload(); vm.changeMonth(1); assertEquals(YearMonth.of(2027, 1), vm.state.value.month)
        gate.complete(Unit); yield(); assertEquals(YearMonth.of(2027, 1), vm.state.value.month); assertTrue(vm.state.value.editable)
    }
    @Test fun lateReadFromPreviousEntryIsDiscarded() = runBlocking {
        val vm = open(); val gate = CompletableDeferred<Unit>()
        api.listRead = { withContext(NonCancellable) { gate.await() } }; vm.reload(); vm.deactivate()
        gate.complete(Unit); yield(); assertNull(vm.state.value.data)
    }
    @Test fun transportCancellationDoesNotLeaveBusyOrReplay() {
        val vm = open(); api.write = { throw CancellationException("fictional cancellation") }
        vm.submit("2026-12-10"); assertFalse(vm.state.value.busy); assertTrue(vm.state.value.stale); assertTrue(vm.state.value.uncertain)
        vm.acknowledge(); assertFalse(vm.state.value.editable)
        vm.reload(); assertFalse(vm.state.value.stale); vm.acknowledge(); assertTrue(vm.state.value.editable)
        assertEquals(1, api.writes)
    }
    @Test fun postLoadMembershipLossDiscardsSuccessfulResponse() {
        val vm = open(); api.listRead = { access = false }; vm.reload()
        assertTrue(vm.state.value.forbidden); assertNull(vm.state.value.data)
    }
    @Test fun lookupFailureBeforeSendDoesNotClaimUnknownWrite() {
        val vm = open(); accessRead = { throw java.io.IOException() }; vm.submit("2026-12-10")
        assertEquals(0, api.writes); assertFalse(vm.state.value.uncertain); assertTrue(vm.state.value.stale)
    }
    @Test fun mixedPolicySnapshotRetriesReadsOnceWithoutMutation() {
        var policies = 0
        api.policyRead = { if (++policies == 1) api.limit = 3 }
        val vm = open(true)
        assertEquals(2, api.reads); assertEquals(2, policies); assertEquals(0, api.writes)
        assertEquals(3, vm.state.value.data!!.list.monthlyLimit)
        assertEquals(3, vm.state.value.data!!.policy!!.value.monthlyLimit)
    }
    @Test fun repeatedPolicyChangesFailClosedWithSpecificNotice() {
        api.policyRead = { api.limit = if (api.limit == 4) 3 else 4 }
        val vm = model(true); vm.activate()
        assertEquals(2, api.reads); assertEquals(0, api.writes)
        assertFalse(vm.state.value.editable); assertNull(vm.state.value.data)
        assertTrue(vm.state.value.notice!!.contains("조회 중 정책이 다시 변경"))
    }
    @Test fun cancelledPolicyRetryCannotReplaceNewMonth() = runBlocking {
        val vm = open(true); val gate = CompletableDeferred<Unit>(); var policies = 0
        api.policyRead = { if (++policies == 1) api.limit = 3 }
        api.listRead = { month -> if (month == "2026-12" && policies == 1) withContext(NonCancellable) { gate.await() } }
        vm.reload(); assertTrue(vm.state.value.loading)
        vm.changeMonth(1)
        val fresh = vm.state.value
        gate.complete(Unit); yield()
        assertEquals(YearMonth.of(2027, 1), vm.state.value.month)
        assertEquals(fresh, vm.state.value); assertTrue(vm.state.value.editable)
    }
    @Test fun missingNurseIdentityShowsActionableNoticeAndCanRecover() {
        val vm = model()
        api.listRead = { throw ApiException(403, "NURSE_IDENTITY_REQUIRED", "fixed") }
        vm.activate()
        assertFalse(vm.state.value.forbidden); assertNull(vm.state.value.data)
        assertEquals(0, expired); assertFalse(vm.state.value.editable)
        assertEquals(offRequestError("NURSE_IDENTITY_REQUIRED"), vm.state.value.notice)
        api.listRead = {}; vm.reload()
        assertTrue(vm.state.value.editable)
    }
    @Test fun identityLossClearsPreviouslyLoadedDataWithoutClaimingRoleLoss() {
        val vm = open()
        api.listRead = { throw ApiException(403, "NURSE_IDENTITY_REQUIRED", "fixed") }
        vm.reload()
        assertNull(vm.state.value.data); assertFalse(vm.state.value.forbidden)
        vm.submit("2026-12-10"); assertEquals(0, api.writes)
    }
    @Test fun mixedMonthAndDuplicateIdsFailClosed() {
        api.rows = listOf(FakeOffApi.row(), FakeOffApi.row()); val vm = model(); vm.activate(); assertTrue(vm.state.value.stale); assertNull(vm.state.value.data)
    }
}
