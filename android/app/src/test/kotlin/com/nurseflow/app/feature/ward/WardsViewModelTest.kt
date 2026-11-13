package com.nurseflow.app.feature.ward

import com.nurseflow.app.core.network.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.*
import org.junit.Test

class WardsViewModelTest {
    private class Api : NurseFlowApi {
        val mes = mutableListOf<CompletableDeferred<CurrentUserDto>>()
        val wardCalls = mutableListOf<CompletableDeferred<Items<MembershipDto>>>()
        override suspend fun me() = CompletableDeferred<CurrentUserDto>().also { mes += it }.await()
        override suspend fun wards() = CompletableDeferred<Items<MembershipDto>>().also { wardCalls += it }.await()
        override suspend fun publishedWard(wardId: String, month: String) = error("unused")
        override suspend fun publishedMine(wardId: String, month: String) = error("unused")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private fun ward(id: String, role: String = "WARD_ADMIN") = MembershipDto(id, "가상 내과 병동", listOf(role), true, "가상대학교병원")
    private fun Api.finish(vararg wards: MembershipDto, index: Int) {
        mes[index].complete(CurrentUserDto("user-1", "가상 사용자", emptyList()))
        wardCalls.last().complete(Items(wards.toList())) // the wards call is made once me() returns
    }
    private fun WardsViewModel.loadedWards() = (state.value as WardsUiState.Loaded).wards.map { it.wardId }

    @Test fun createdWardIsVisibleImmediatelyButSuccessfulReloadIsAuthoritative() {
        val api = Api()
        val model = WardsViewModel(api, scope)
        api.finish(ward("ward-old", "WARD_MEMBER"), index = 0)
        assertEquals(listOf("ward-old"), model.loadedWards())

        model.onCreated(ward("ward-new"))
        // Visible right away, and the list never flips back to Loading (open screens are not bounced).
        assertEquals(listOf("ward-old", "ward-new"), model.loadedWards())
        assertEquals(2, api.mes.size)

        // Successful reload drops wards whose membership is no longer active.
        api.finish(ward("ward-old", "WARD_MEMBER"), index = 1)
        assertEquals(listOf("ward-old"), model.loadedWards())
    }

    @Test fun reloadContainingTheCreatedWardDoesNotDuplicateIt() {
        val api = Api()
        val model = WardsViewModel(api, scope)
        api.finish(index = 0)
        model.onCreated(ward("ward-new"))
        api.finish(ward("ward-new"), index = 1)
        assertEquals(listOf("ward-new"), model.loadedWards())
        // Once the server lists it, a later reload is purely server-authoritative.
        model.reload()
        api.finish(index = 2)
        assertEquals(emptyList<String>(), model.loadedWards())
    }

    @Test fun createdWardIsDedupedByWardId() {
        val api = Api()
        val model = WardsViewModel(api, scope)
        api.finish(ward("ward-1"), index = 0)
        model.onCreated(ward("ward-1"))
        assertEquals(listOf("ward-1"), model.loadedWards())
    }

    @Test fun failedSilentRefreshKeepsTheCreatedWardVisible() {
        val api = Api()
        val model = WardsViewModel(api, scope)
        api.finish(ward("ward-old", "WARD_MEMBER"), index = 0)
        model.onCreated(ward("ward-new"))
        api.mes[1].completeExceptionally(ApiException(0, "NETWORK", "고정 메시지"))
        assertEquals(listOf("ward-old", "ward-new"), model.loadedWards())
    }

    @Test fun failedReloadAfterCreationWithoutACompleteListIsVisible() {
        val api = Api()
        val model = WardsViewModel(api, scope)
        api.mes[0].completeExceptionally(ApiException(0, "NETWORK", "고정 메시지"))
        model.onCreated(ward("ward-new"))
        api.mes[1].completeExceptionally(ApiException(0, "NETWORK", "고정 메시지"))
        assertTrue(model.state.value is WardsUiState.Failed)
    }

    @Test fun creationBeforeFirstLoadRequiresACompleteServerList() {
        val api = Api()
        val model = WardsViewModel(api, scope)
        model.onCreated(ward("ward-new")) // the initial load is still in flight
        assertEquals(WardsUiState.Loading, model.state.value)
        api.finish(ward("ward-old", "WARD_MEMBER"), ward("ward-new"), index = 1)
        assertEquals(listOf("ward-old", "ward-new"), model.loadedWards())
        assertEquals("가상 사용자", (model.state.value as WardsUiState.Loaded).displayName)
    }
}
