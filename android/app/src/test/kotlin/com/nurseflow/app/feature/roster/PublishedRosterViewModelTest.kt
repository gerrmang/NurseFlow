package com.nurseflow.app.feature.roster

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.nurseflow.app.core.network.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.YearMonth

class PublishedRosterViewModelTest {
    private val wardId = "demo-ward"
    private val shiftA = "00000000-0000-0000-0000-00000000000a"
    private val shiftB = "00000000-0000-0000-0000-00000000000b"

    private fun personal(month: String, vararg dates: String) = PublishedPersonalCalendarDto(
        month, "Asia/Seoul",
        dates.map { PersonalAssignmentDto(it, "D", "${it}T22:00:00Z", "${it}T06:00:00Z") })

    private fun notFound() = ApiException(404, "PUBLISHED_ROSTER_NOT_FOUND", "찾을 수 없어요.")

    /** Fake API whose calls suspend until the test completes them. */
    private class FakeApi : NurseFlowApi {
        val mine = mutableListOf<Pair<String, CompletableDeferred<PublishedPersonalCalendarDto>>>()
        val ward = mutableListOf<Pair<String, CompletableDeferred<PublishedWardCalendarDto>>>()
        override suspend fun me() = error("unused")
        override suspend fun wards() = error("unused")
        override suspend fun publishedMine(wardId: String, month: String) =
            CompletableDeferred<PublishedPersonalCalendarDto>().also { mine += month to it }.await()
        override suspend fun publishedWard(wardId: String, month: String) =
            CompletableDeferred<PublishedWardCalendarDto>().also { ward += month to it }.await()
    }

    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private fun vm(api: FakeApi, canViewWard: Boolean = true) =
        PublishedRosterViewModel(api, wardId, canViewWard, scope(), YearMonth.of(2026, 11))

    @Test fun loadsOwnPublishedRosterFirstWithTimesInWardZone() {
        val api = FakeApi()
        val model = vm(api)
        assertEquals(RosterContent.Loading, model.state.value.content)
        assertEquals("2026-11", api.mine.single().first)
        api.mine.single().second.complete(personal("2026-11", "2026-11-10"))
        val days = (model.state.value.content as RosterContent.Mine).days
        assertEquals("D", days.single().shiftCode)
        assertEquals("07:00–15:00", days.single().timeLabel)
    }

    @Test fun emptyOwnAssignmentsIsDistinctFromNotPublished() {
        val api = FakeApi()
        val model = vm(api)
        api.mine.single().second.complete(personal("2026-11"))
        assertEquals(RosterContent.Mine(emptyList()), model.state.value.content)
        model.changeMonth(1)
        api.mine.last().second.completeExceptionally(notFound())
        assertEquals(RosterContent.NotPublished, model.state.value.content)
        assertEquals(YearMonth.of(2026, 12), model.state.value.month)
    }

    @Test fun wardViewGroupsByTimeRangeWithoutShiftCodeGuessing() {
        val api = FakeApi()
        val model = vm(api)
        api.mine.single().second.complete(personal("2026-11"))
        model.selectMode(RosterMode.WARD)
        val slots = listOf(
            StaffingSlotDto("2026-11-10", shiftB, "2026-11-10T06:00:00Z", "2026-11-10T14:00:00Z", "FILLED", AssignmentDto("가상 둘")),
            StaffingSlotDto("2026-11-10", shiftB, "2026-11-10T06:00:00Z", "2026-11-10T14:00:00Z", "FILLED", AssignmentDto("가상 하나")),
            StaffingSlotDto("2026-11-10", shiftB, "2026-11-10T06:00:00Z", "2026-11-10T14:00:00Z", "UNFILLED"),
            StaffingSlotDto("2026-11-10", shiftA, "2026-11-10T14:00:00Z", "2026-11-10T22:00:00Z", "FILLED", AssignmentDto("가상 셋")),
        )
        api.ward.single().second.complete(PublishedWardCalendarDto("2026-11", "Asia/Seoul", slots))
        val day = (model.state.value.content as RosterContent.Ward).days.single()
        assertEquals(listOf("15:00–23:00", "23:00–07:00"), day.groups.map { it.timeLabel })
        assertEquals(listOf("가상 둘", "가상 하나"), day.groups[0].names)
        assertEquals(1, day.groups[0].unfilled)
    }

    @Test fun wardTabIsUnavailableWithoutWardViewPermission() {
        val api = FakeApi()
        val model = vm(api, canViewWard = false)
        api.mine.single().second.complete(personal("2026-11"))
        model.selectMode(RosterMode.WARD)
        assertEquals(RosterMode.MINE, model.state.value.mode)
        assertTrue(api.ward.isEmpty())
    }

    @Test fun lateResponseForPreviousMonthNeverOverwritesNewMonth() {
        val api = FakeApi()
        val model = vm(api)
        val november = api.mine.single().second
        model.changeMonth(1)
        model.changeMonth(1)
        assertEquals(listOf("2026-11", "2026-12", "2027-01"), api.mine.map { it.first })
        api.mine[2].second.complete(personal("2027-01", "2027-01-05"))
        november.complete(personal("2026-11", "2026-11-01"))
        api.mine[1].second.complete(personal("2026-12", "2026-12-01"))
        val state = model.state.value
        assertEquals(YearMonth.of(2027, 1), state.month)
        assertEquals("2027-01-05", (state.content as RosterContent.Mine).days.single().date.toString())
    }

    @Test fun lateOwnResponseNeverOverwritesWardModeAndViceVersa() {
        val api = FakeApi()
        val model = vm(api)
        val own = api.mine.single().second
        model.selectMode(RosterMode.WARD)
        api.ward.single().second.complete(PublishedWardCalendarDto("2026-11", "Asia/Seoul", emptyList()))
        own.complete(personal("2026-11", "2026-11-02"))
        assertEquals(RosterMode.WARD, model.state.value.mode)
        assertTrue(model.state.value.content is RosterContent.Ward)
    }

    @Test fun forbiddenNetworkAndRetry() {
        val api = FakeApi()
        val model = vm(api)
        api.mine[0].second.completeExceptionally(ApiException(0, "NETWORK", "서버에 연결할 수 없어요."))
        assertTrue(model.state.value.content is RosterContent.Retryable)
        model.retry()
        assertEquals(RosterContent.Loading, model.state.value.content)
        api.mine[1].second.completeExceptionally(ApiException(403, "WARD_CALENDAR_VIEW_REQUIRED", "권한이 없어요."))
        assertEquals(RosterContent.Forbidden, model.state.value.content)
    }

    @Test fun nonMemberNotFoundIsNotShownAsUnpublished() {
        val api = FakeApi()
        val model = vm(api)
        api.mine[0].second.completeExceptionally(ApiException(404, "WARD_NOT_FOUND", "찾을 수 없어요."))
        assertTrue(model.state.value.content is RosterContent.Failed)
    }

    @Test fun reentryAfterServerPublishShowsPublishedRosterInsteadOfStaleNotPublished() {
        val api = FakeApi()
        val model = vm(api)
        model.enter(1) // first entry is served by init: no duplicate request
        assertEquals(1, api.mine.size)
        api.mine[0].second.completeExceptionally(notFound())
        assertEquals(RosterContent.NotPublished, model.state.value.content)
        model.enter(1) // recomposition / Activity recreation of the same entry
        assertEquals(1, api.mine.size)
        model.enter(2) // user leaves and re-enters the same ward
        assertEquals(RosterContent.Loading, model.state.value.content)
        assertEquals(listOf("2026-11", "2026-11"), api.mine.map { it.first })
        api.mine[1].second.complete(personal("2026-11", "2026-11-10"))
        assertEquals(1, (model.state.value.content as RosterContent.Mine).days.size)
    }

    @Test fun reentryAfterRepublishReplacesOldAssignmentsAndKeepsModeAndMonth() {
        val api = FakeApi()
        val model = vm(api)
        model.enter(1)
        api.mine[0].second.complete(personal("2026-11"))
        model.selectMode(RosterMode.WARD)
        model.changeMonth(1)
        val slot = { name: String -> StaffingSlotDto("2026-12-01", shiftA, "2026-12-01T00:00:00Z", "2026-12-01T08:00:00Z", "FILLED", AssignmentDto(name)) }
        api.ward.last().second.complete(PublishedWardCalendarDto("2026-12", "Asia/Seoul", listOf(slot("가상 이전"))))
        val old = api.ward.last().second
        model.enter(2)
        assertEquals(listOf("2026-11", "2026-12", "2026-12"), api.ward.map { it.first })
        api.ward.last().second.complete(PublishedWardCalendarDto("2026-12", "Asia/Seoul", listOf(slot("가상 재공개"))))
        val state = model.state.value
        assertEquals(RosterMode.WARD, state.mode)
        assertEquals(YearMonth.of(2026, 12), state.month)
        assertEquals(listOf("가상 재공개"), (state.content as RosterContent.Ward).days.single().groups.single().names)
        assertTrue(old.isCompleted)
    }

    @Test fun reentryDiscardsLateResponseFromBeforeReentry() {
        val api = FakeApi()
        val model = vm(api)
        model.enter(1)
        val stale = api.mine[0].second
        model.enter(2)
        api.mine[1].second.complete(personal("2026-11", "2026-11-20"))
        stale.complete(personal("2026-11", "2026-11-01"))
        assertEquals("2026-11-20", (model.state.value.content as RosterContent.Mine).days.single().date.toString())
    }

    @Test fun clearingStoreCancelsInFlightRequest() {
        val api = FakeApi()
        val store = ViewModelStore()
        val m = ViewModelProvider(object : ViewModelStoreOwner { override val viewModelStore = store }, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>) =
                PublishedRosterViewModel(api, wardId, true, scope(), YearMonth.of(2026, 11)) as T
        })[PublishedRosterViewModel::class.java]
        store.clear()
        api.mine.last().second.complete(personal("2026-11", "2026-11-03"))
        // Logout clears the session store: the late result must not reach a state anyone sees.
        assertEquals(RosterContent.Loading, m.state.value.content)
    }
}
