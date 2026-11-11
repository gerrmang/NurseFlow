package com.nurseflow.app.feature.ward.create

import com.nurseflow.app.core.network.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.*
import org.junit.Test

/** Calls suspend until the test completes them, so ordering and late responses are explicit. */
class FakeWardCreationApi : WardCreationApi {
    val searches = mutableListOf<Pair<String, CompletableDeferred<HospitalSearchPageDto>>>()
    val creates = mutableListOf<Triple<String, CreateWardBody, CompletableDeferred<MembershipDto>>>()

    override suspend fun searchHospitals(query: String, page: Int, size: Int) =
        CompletableDeferred<HospitalSearchPageDto>().also { searches += query to it }.await()

    override suspend fun createWard(idempotencyKey: String, body: CreateWardBody) =
        CompletableDeferred<MembershipDto>().also { creates += Triple(idempotencyKey, body, it) }.await()
}

fun candidate(id: String, name: String = "가상대학교병원", address: String = "가상시 가상구 1") =
    HospitalCandidateDto("TEST_SOURCE", id, name, address, "종합병원")

fun searchPage(vararg items: HospitalCandidateDto, total: Int = items.size, attribution: String? = "가상 출처 · 기준 2026-01-01") =
    HospitalSearchPageDto(items.toList(), 1, 20, total, attribution)

fun createdWard(id: String = "ward-new", name: String = "가상 내과 병동") =
    MembershipDto(id, name, listOf("WARD_ADMIN"), true, "가상대학교병원")

private fun problem(status: Int, code: String?) = ApiException(status, code, "고정 메시지")
private fun networkError() = ApiException(0, "NETWORK", "고정 메시지")

class WardCreateViewModelTest {
    private val api = FakeWardCreationApi()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var keyCounter = 0
    private var invalid = 0
    private val createdCallbacks = mutableListOf<MembershipDto>()
    private fun model() = WardCreateViewModel(
        api, scope,
        onSessionInvalid = { invalid++ },
        onCreated = { createdCallbacks += it },
        newKey = { "key-${++keyCounter}" },
    )

    private fun WardCreateViewModel.pick(hospital: HospitalCandidateDto = candidate("h1"), name: String = "가상 내과 병동") {
        select(hospital)
        setWardName(name)
    }

    // ---- search ----

    @Test fun lateOlderSearchResultIsDiscardedAfterANewerSearch() {
        val m = model()
        m.setQuery("가상 병원"); m.search()
        m.setQuery("가상 의료원"); m.search()
        assertEquals(2, api.searches.size)
        api.searches[1].second.complete(searchPage(candidate("new")))
        api.searches[0].second.complete(searchPage(candidate("old"))) // arrives late
        val results = m.state.value.search as SearchState.Results
        assertEquals("가상 의료원", results.query)
        assertEquals("new", results.items.single().externalId)
    }

    @Test fun selectedHospitalIsNotOverwrittenByAnInFlightSearch() {
        val m = model()
        m.setQuery("가상 병원"); m.search()
        m.select(candidate("chosen"))
        api.searches[0].second.complete(searchPage(candidate("other")))
        assertEquals("chosen", m.state.value.selected!!.externalId)
        assertEquals(CreateStep.NAME, m.state.value.step)
    }

    @Test fun emptyResultKeepsAttribution() {
        val m = model()
        m.setQuery("가상 병원"); m.search()
        api.searches[0].second.complete(searchPage(total = 0))
        val empty = m.state.value.search as SearchState.Empty
        assertEquals("가상 출처 · 기준 2026-01-01", empty.attribution)
    }

    @Test fun resultsKeepAttributionAndFlagTruncation() {
        val m = model()
        m.setQuery("가상 병원"); m.search()
        api.searches[0].second.complete(searchPage(candidate("a"), candidate("b"), total = 57))
        val results = m.state.value.search as SearchState.Results
        assertTrue(results.truncated)
        assertNotNull(results.attribution)
    }

    @Test fun searchFailuresAreDistinguishedByCode() {
        val cases = listOf(
            networkError() to WardCreateFailure.NETWORK,
            problem(503, "HOSPITAL_SEARCH_UNAVAILABLE") to WardCreateFailure.UNAVAILABLE,
            problem(503, "HOSPITAL_SEARCH_NOT_CONFIGURED") to WardCreateFailure.NOT_CONFIGURED,
            problem(400, "INVALID_REQUEST") to WardCreateFailure.INVALID_REQUEST,
            problem(500, null) to WardCreateFailure.SERVER,
        )
        for ((error, expected) in cases) {
            val a = FakeWardCreationApi()
            val m = WardCreateViewModel(a, scope)
            m.setQuery("가상 병원"); m.search()
            a.searches[0].second.completeExceptionally(error)
            assertEquals(expected, (m.state.value.search as SearchState.Failed).failure)
        }
    }

    @Test fun invalidQueryNeverCallsTheServer() {
        val m = model()
        for (bad in listOf("(가)", "가", "", " 가 ", "가".repeat(51), "가나\n다")) {
            m.setQuery(bad); m.search()
            assertNotNull("'$bad' should be rejected", m.state.value.queryError)
        }
        assertEquals(0, api.searches.size)
        assertEquals(SearchState.Idle, m.state.value.search)
    }

    @Test fun searchSendsTheTrimmedQuery() {
        val m = model()
        m.setQuery("  가상 병원  "); m.search()
        assertEquals("가상 병원", api.searches.single().first)
    }

    // ---- create body / double tap ----

    @Test fun createBodyHasOnlyTheCandidateReferenceAndTrimmedName() {
        val m = model()
        m.pick(candidate("h1", name = "가상대학교병원"), "  가상 내과 병동  ")
        m.create()
        val (_, body, _) = api.creates.single()
        assertEquals(CreateWardBody(HospitalRefBody("TEST_SOURCE", "h1"), "가상 내과 병동"), body)
    }

    @Test fun duplicateTapsWhileCreatingAreIgnored() {
        val m = model()
        m.pick()
        m.create(); m.create(); m.create()
        assertEquals(1, api.creates.size)
        assertTrue(m.state.value.creating)
        m.setWardName("바꾼 이름") // input is locked while creating
        assertEquals("가상 내과 병동", m.state.value.wardName)
    }

    @Test fun successShowsCreatedAndNotifiesOnce() {
        val m = model()
        m.pick()
        m.create()
        api.creates[0].third.complete(createdWard())
        assertEquals(CreateStep.DONE, m.state.value.step)
        assertEquals("ward-new", m.state.value.created!!.wardId)
        assertEquals(listOf("ward-new"), createdCallbacks.map { it.wardId })
        m.create() // already created: no second request
        assertEquals(1, api.creates.size)
    }

    // ---- idempotency key policy ----

    @Test fun networkLossThenUnchangedRetryReusesTheKey() {
        val m = model()
        m.pick()
        m.create()
        api.creates[0].third.completeExceptionally(networkError())
        assertTrue(m.state.value.lostResponse)
        assertFalse(m.state.value.inputChangedSinceLoss)
        m.create()
        assertEquals(api.creates[0].first, api.creates[1].first)
        assertEquals(1, keyCounter)
    }

    @Test fun serverErrorAndUnknownErrorAlsoKeepTheKey() {
        for (error in listOf(problem(500, null), problem(502, "WHATEVER"), problem(418, null))) {
            val a = FakeWardCreationApi()
            val m = WardCreateViewModel(a, scope, newKey = { "key-${++keyCounter}" })
            m.pick(); m.create()
            a.creates[0].third.completeExceptionally(error)
            assertTrue(m.state.value.lostResponse)
            m.create()
            assertEquals(a.creates[0].first, a.creates[1].first)
        }
    }

    @Test fun whitespaceOnlyDifferenceIsTheSameDraftAndKey() {
        val m = model()
        m.pick(name = "가상 내과 병동")
        m.create()
        api.creates[0].third.completeExceptionally(networkError())
        m.setWardName("  가상 내과 병동 ")
        assertFalse(m.state.value.inputChangedSinceLoss)
        m.create()
        assertEquals(api.creates[0].first, api.creates[1].first)
    }

    @Test fun changingWardNameAfterLossUsesANewKeyAndWarns() {
        val m = model()
        m.pick()
        m.create()
        api.creates[0].third.completeExceptionally(networkError())
        m.setWardName("가상 외과 병동")
        assertTrue(m.state.value.inputChangedSinceLoss)
        m.create()
        assertNotEquals(api.creates[0].first, api.creates[1].first)
    }

    @Test fun changingHospitalAfterLossUsesANewKey() {
        val m = model()
        m.pick(candidate("h1"))
        m.create()
        api.creates[0].third.completeExceptionally(networkError())
        m.backToSearch()
        m.select(candidate("h2"))
        assertTrue(m.state.value.inputChangedSinceLoss)
        m.create()
        assertNotEquals(api.creates[0].first, api.creates[1].first)
    }

    @Test fun afterSuccessANewAttemptUsesANewKey() {
        val m = model()
        m.pick(); m.create()
        api.creates[0].third.complete(createdWard())
        m.reset()
        m.pick(); m.create()
        assertNotEquals(api.creates[0].first, api.creates[1].first)
    }

    @Test fun keyReusedGetsANewKeyWithoutAutomaticResend() {
        val m = model()
        m.pick(); m.create()
        api.creates[0].third.completeExceptionally(problem(409, "IDEMPOTENCY_KEY_REUSED"))
        assertEquals(WardCreateFailure.KEY_REUSED, m.state.value.createError)
        assertEquals(1, api.creates.size) // not resent by itself
        m.create()
        assertEquals(2, api.creates.size)
        assertNotEquals(api.creates[0].first, api.creates[1].first)
    }

    // ---- errors ----

    @Test fun hospitalNotFoundClearsSelectionAndReturnsToSearch() {
        val m = model()
        m.pick(); m.create()
        api.creates[0].third.completeExceptionally(problem(422, "HOSPITAL_NOT_FOUND"))
        assertNull(m.state.value.selected)
        assertEquals(CreateStep.SEARCH, m.state.value.step)
        assertEquals(WardCreateFailure.HOSPITAL_NOT_FOUND, m.state.value.createError)
    }

    private fun WardCreateViewModel.refuseHospital(hospital: HospitalCandidateDto = candidate("h1")) {
        pick(hospital); create()
        api.creates.last().third.completeExceptionally(problem(422, "HOSPITAL_NOT_FOUND"))
    }

    @Test fun refusedHospitalCannotBePickedAgainAndOthersCan() {
        val m = model()
        m.setQuery("가상 병원"); m.search()
        api.searches[0].second.complete(searchPage(candidate("h1"), candidate("h2")))
        m.refuseHospital(candidate("h1"))
        assertTrue(m.state.value.isInvalid(candidate("h1")))
        assertFalse(m.state.value.isInvalid(candidate("h2")))
        m.select(candidate("h1")) // the stale result row is tapped again
        assertNull(m.state.value.selected)
        assertEquals(CreateStep.SEARCH, m.state.value.step)
        assertEquals(WardCreateFailure.HOSPITAL_NOT_FOUND, m.state.value.createError) // not cleared by the refused tap
        m.select(candidate("h2"))
        assertEquals("h2", m.state.value.selected?.externalId)
        assertEquals(CreateStep.NAME, m.state.value.step)
    }

    @Test fun notFoundNoticeStaysUntilANewSearchStartsThenClears() {
        val m = model()
        m.refuseHospital()
        m.setQuery("가") // typing alone keeps the notice
        assertEquals(WardCreateFailure.HOSPITAL_NOT_FOUND, m.state.value.createError)
        m.setQuery("가상 병원"); m.search()
        assertNull(m.state.value.createError)
        assertTrue(m.state.value.search is SearchState.Loading)
    }

    @Test fun invalidSearchTextKeepsTheNotFoundNoticeBecauseNoSearchStarted() {
        val m = model()
        m.refuseHospital()
        m.setQuery("가"); m.search() // rejected on the client: too short
        assertNotNull(m.state.value.queryError)
        assertEquals(WardCreateFailure.HOSPITAL_NOT_FOUND, m.state.value.createError)
        assertTrue(api.searches.isEmpty())
    }

    @Test fun pickingADifferentHospitalClearsTheNotFoundNotice() {
        val m = model()
        m.refuseHospital(candidate("h1"))
        m.select(candidate("h2"))
        assertNull(m.state.value.createError)
        assertEquals("h2", m.state.value.selected?.externalId)
    }

    @Test fun searchFailureAfterNotFoundReplacesTheNoticeWithTheSearchError() {
        val m = model()
        m.refuseHospital()
        m.setQuery("가상 병원"); m.search()
        api.searches[0].second.completeExceptionally(problem(503, "HOSPITAL_SEARCH_UNAVAILABLE"))
        assertNull(m.state.value.createError)
        assertEquals(SearchState.Failed(WardCreateFailure.UNAVAILABLE), m.state.value.search)
    }

    @Test fun resetForgetsRefusedHospitals() {
        val m = model()
        m.refuseHospital()
        m.reset()
        assertTrue(m.state.value.invalidHospitals.isEmpty())
        assertNull(m.state.value.createError)
        m.select(candidate("h1"))
        assertEquals("h1", m.state.value.selected?.externalId)
    }

    @Test fun refusedLimitErrorIsNotClearedByANewSearch() {
        val m = model()
        m.pick(); m.create()
        api.creates[0].third.completeExceptionally(problem(409, "WARD_CREATION_LIMIT_REACHED"))
        m.backToSearch()
        m.setQuery("가상 병원"); m.search()
        assertEquals(WardCreateFailure.LIMIT_REACHED, m.state.value.createError)
    }

    @Test fun alreadyCreatedAndLimitReachedAreDifferentOutcomes() {
        val a = FakeWardCreationApi()
        val m1 = WardCreateViewModel(a, scope)
        m1.pick(); m1.create()
        a.creates[0].third.completeExceptionally(problem(409, "WARD_ALREADY_CREATED"))
        assertEquals(WardCreateFailure.ALREADY_CREATED, m1.state.value.createError)
        assertFalse(m1.state.value.lostResponse)
        m1.create() // a definite refusal can be edited and sent again
        assertEquals(2, a.creates.size)

        val b = FakeWardCreationApi()
        val m2 = WardCreateViewModel(b, scope)
        m2.pick(); m2.create()
        b.creates[0].third.completeExceptionally(problem(409, "WARD_CREATION_LIMIT_REACHED"))
        assertEquals(WardCreateFailure.LIMIT_REACHED, m2.state.value.createError)
        m2.create() // the limit disables further attempts
        assertEquals(1, b.creates.size)
        m2.setWardName("다른 이름")
        assertEquals(WardCreateFailure.LIMIT_REACHED, m2.state.value.createError)
    }

    @Test fun notConfiguredOnCreateIsReportedNotRetriedAsLoss() {
        val m = model()
        m.pick(); m.create()
        api.creates[0].third.completeExceptionally(problem(503, "HOSPITAL_SEARCH_NOT_CONFIGURED"))
        assertEquals(WardCreateFailure.NOT_CONFIGURED, m.state.value.createError)
        assertFalse(m.state.value.lostResponse)
    }

    @Test fun userNotFoundEndsTheSession() {
        val m = model()
        m.pick(); m.create()
        api.creates[0].third.completeExceptionally(problem(401, "USER_NOT_FOUND"))
        assertEquals(1, invalid)
    }

    @Test fun userNotFoundDuringSearchEndsTheSessionToo() {
        val m = model()
        m.setQuery("가상 병원"); m.search()
        api.searches[0].second.completeExceptionally(problem(401, "USER_NOT_FOUND"))
        assertEquals(1, invalid)
    }

    @Test fun otherUnauthorizedKeepsTheKeyForARetry() {
        val m = model()
        m.pick(); m.create()
        api.creates[0].third.completeExceptionally(problem(401, null))
        assertEquals(WardCreateFailure.UNAUTHORIZED, m.state.value.createError)
        assertEquals(0, invalid)
        m.create()
        assertEquals(api.creates[0].first, api.creates[1].first)
    }

    // ---- lifetime: rotation vs re-entry ----

    @Test fun recompositionOrRotationKeepsStateBecauseTheSameModelSurvives() {
        // Rotation recreates the UI, not the ViewModel: state read again is unchanged.
        val m = model()
        m.setQuery("가상 병원"); m.search()
        api.searches[0].second.complete(searchPage(candidate("h1")))
        m.select(candidate("h1")); m.setWardName("가상 내과 병동")
        m.create()
        api.creates[0].third.completeExceptionally(networkError())
        val before = m.state.value
        assertEquals(before, m.state.value)
        assertEquals("가상 병원", before.queryInput)
        assertEquals("h1", before.selected!!.externalId)
        assertEquals("가상 내과 병동", before.wardName)
        assertTrue(before.lostResponse)
        m.create() // and the key survives with it
        assertEquals(api.creates[0].first, api.creates[1].first)
    }

    @Test fun resetOnReentryClearsEverythingIncludingTheKey() {
        val m = model()
        m.setQuery("가상 병원"); m.search()
        api.searches[0].second.complete(searchPage(candidate("h1")))
        m.pick(candidate("h1"))
        m.create()
        api.creates[0].third.completeExceptionally(networkError())
        m.reset()
        assertEquals(WardCreateState(), m.state.value)
        // Same content after the reset must not reuse the previous key.
        m.pick(candidate("h1")); m.create()
        assertNotEquals(api.creates[0].first, api.creates[1].first)
    }

    @Test fun resetClearsACreatedWard() {
        val m = model()
        m.pick(); m.create()
        api.creates[0].third.complete(createdWard())
        m.reset()
        assertNull(m.state.value.created)
        assertEquals(CreateStep.SEARCH, m.state.value.step)
    }

    @Test fun searchResponseFromBeforeResetIsDiscarded() {
        val m = model()
        m.setQuery("가상 병원"); m.search()
        m.reset()
        api.searches[0].second.complete(searchPage(candidate("stale")))
        assertEquals(SearchState.Idle, m.state.value.search)
    }

    @Test fun searchFailureFromBeforeResetIsDiscarded() {
        val m = model()
        m.setQuery("가상 병원"); m.search()
        m.reset()
        api.searches[0].second.completeExceptionally(problem(401, "USER_NOT_FOUND"))
        assertEquals(SearchState.Idle, m.state.value.search)
        assertEquals(0, invalid)
    }

    @Test fun createResponseFromBeforeResetIsDiscarded() {
        val m = model()
        m.pick(); m.create()
        m.reset()
        api.creates[0].third.complete(createdWard())
        assertEquals(WardCreateState(), m.state.value)
        assertTrue("the old request must not notify the ward list", createdCallbacks.isEmpty())
    }

    @Test fun createFailureFromBeforeResetIsDiscarded() {
        val m = model()
        m.pick(); m.create()
        m.reset()
        api.creates[0].third.completeExceptionally(problem(401, "USER_NOT_FOUND"))
        assertEquals(WardCreateState(), m.state.value)
        assertEquals(0, invalid)
    }
}
