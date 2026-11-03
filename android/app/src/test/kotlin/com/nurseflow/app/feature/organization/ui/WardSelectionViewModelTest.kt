package com.nurseflow.app.feature.organization.ui

import com.nurseflow.app.core.network.ApiError
import com.nurseflow.app.core.network.ApiResult
import com.nurseflow.app.feature.organization.domain.Hospital
import com.nurseflow.app.feature.organization.domain.HospitalWard
import com.nurseflow.app.feature.organization.domain.MyWard
import com.nurseflow.app.feature.organization.domain.OrganizationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WardSelectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val hospital = Hospital("h1", "가상 병원", "Asia/Seoul")
    private var keyCounter = 0

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(repo: FakeRepository) = WardSelectionViewModel(repo) { "key-${++keyCounter}" }

    @Test
    fun `member without hospital role sees own wards and no admin section`() = runTest(dispatcher) {
        val repo = FakeRepository(myWards = mutableListOf(ward("w1", "WARD_MEMBER")))
        val vm = vm(repo)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(LoadState.Ready, state.load)
        assertEquals(listOf("w1"), state.myWards.map { it.wardId })
        assertFalse(state.isHospitalAdmin)
        assertEquals("w1", state.highlightedWardId)
        assertNull("기본 강조는 방금 생성 표시가 아니다", state.justCreatedWardId)
        assertEquals(0, repo.hospitalWardCalls)

        vm.choose("w1")
        assertEquals("w1", vm.uiState.value.chosenWard?.wardId)
        vm.choose("not-mine")
        assertEquals("w1", vm.uiState.value.chosenWardId)
    }

    @Test
    fun `empty lists and load failure with retry`() = runTest(dispatcher) {
        val repo = FakeRepository(failNext = ApiError.Network)
        val vm = vm(repo)
        advanceUntilIdle()
        assertEquals(LoadState.Failed(ApiError.Network), vm.uiState.value.load)

        vm.refresh()
        advanceUntilIdle()
        assertEquals(LoadState.Ready, vm.uiState.value.load)
        assertTrue(vm.uiState.value.myWards.isEmpty())
    }

    @Test
    fun `expired session is surfaced instead of a generic error`() = runTest(dispatcher) {
        val vm = vm(FakeRepository(failNext = ApiError.SessionExpired))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.sessionExpired)
    }

    @Test
    fun `hospital admin creates a ward then lists refresh and the new ward is chosen`() = runTest(dispatcher) {
        val repo = FakeRepository(hospitals = listOf(hospital))
        val vm = vm(repo)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isHospitalAdmin)
        assertEquals("h1", vm.uiState.value.selectedHospitalId)

        vm.openCreateForm()
        vm.editName("  신규 병동 ")
        vm.submitCreate()
        assertTrue(vm.uiState.value.form!!.submitting)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull(state.form)
        assertEquals(listOf(CreateCall("h1", "신규 병동", null, "key-1")), repo.createCalls)
        assertEquals("new-1", state.chosenWardId)
        assertEquals("new-1", state.justCreatedWardId)
        assertEquals(listOf("new-1"), state.hospitalWards.map { it.wardId })
        assertTrue(state.notice!!.contains("신규 병동"))
        assertFalse(state.noticeIsError)
    }

    @Test
    fun `invalid input is caught before calling the server`() = runTest(dispatcher) {
        val repo = FakeRepository(hospitals = listOf(hospital))
        val vm = vm(repo)
        advanceUntilIdle()
        vm.openCreateForm()
        vm.editName("   ")
        vm.editTimeZone("+09:00")
        vm.submitCreate()

        val form = vm.uiState.value.form!!
        assertEquals(WardSelectionViewModel.NAME_RULE, form.nameError)
        assertEquals(WardSelectionViewModel.ZONE_RULE, form.timeZoneError)
        assertTrue(repo.createCalls.isEmpty())

        vm.editName("신규 병동")
        vm.editTimeZone("Mars/Base")
        vm.submitCreate()
        assertEquals(WardSelectionViewModel.ZONE_RULE, vm.uiState.value.form!!.timeZoneError)
        assertTrue(repo.createCalls.isEmpty())
    }

    @Test
    fun `server field errors map to the right input`() = runTest(dispatcher) {
        val repo = FakeRepository(hospitals = listOf(hospital))
        val vm = vm(repo)
        advanceUntilIdle()
        vm.openCreateForm()
        vm.editName("중복 병동")
        repo.createResult = ApiResult.Failure(ApiError.Rejected(409, "WARD_NAME_TAKEN"))
        vm.submitCreate()
        advanceUntilIdle()
        assertEquals("같은 병원에 같은 이름의 병동이 있습니다.", vm.uiState.value.form!!.nameError)

        // 확정된 거부 뒤의 재제출은 새 요청이므로 새 키를 쓴다.
        repo.createResult = null
        vm.submitCreate()
        advanceUntilIdle()
        assertEquals(listOf("key-1", "key-2"), repo.createCalls.map { it.key })
    }

    @Test
    fun `uncertain failure retries with the same key and edited input gets a new key`() = runTest(dispatcher) {
        val repo = FakeRepository(hospitals = listOf(hospital))
        val vm = vm(repo)
        advanceUntilIdle()
        vm.openCreateForm()
        vm.editName("재시도 병동")

        repo.createResult = ApiResult.Failure(ApiError.Network)
        vm.submitCreate()
        advanceUntilIdle()
        assertEquals(ApiError.Network, vm.uiState.value.form!!.failure)
        repo.createResult = ApiResult.Failure(ApiError.Server(503))
        vm.submitCreate()
        advanceUntilIdle()
        assertEquals(repo.createCalls[0].key, repo.createCalls[1].key)

        vm.editName("다른 병동")
        repo.createResult = null
        vm.submitCreate()
        advanceUntilIdle()
        assertNotEquals(repo.createCalls[0].key, repo.createCalls[2].key)
        assertNull(vm.uiState.value.form)
    }

    @Test
    fun `losing hospital permission reloads lists from the server`() = runTest(dispatcher) {
        val repo = FakeRepository(hospitals = listOf(hospital))
        val vm = vm(repo)
        advanceUntilIdle()
        vm.openCreateForm()
        vm.editName("권한 없음 병동")
        repo.createResult = ApiResult.Failure(ApiError.Forbidden("HOSPITAL_ADMIN_REQUIRED"))
        repo.hospitals = emptyList()
        vm.submitCreate()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isHospitalAdmin)
        assertNull(state.form)
        // 양식이 닫혀도 이유를 알려준다(에뮬레이터 검증에서 발견한 조용한 실패 회귀 방지).
        assertTrue(state.noticeIsError)
        assertTrue(state.notice!!.startsWith("병원 관리자 권한이 없습니다."))
    }

    // ── PR #14 리뷰 P2-1: 생성 중 병원 전환으로 요청 상태가 섞이는 문제 ──

    private val hospitalB = Hospital("h2", "가상 병원 B", "Asia/Seoul")

    private fun TestScope.startDelayedCreate(repo: FakeRepository, name: String): Pair<WardSelectionViewModel, CompletableDeferred<ApiResult<HospitalWard>>> {
        val vm = vm(repo)
        advanceUntilIdle()
        vm.openCreateForm()
        vm.editName(name)
        val gate = CompletableDeferred<ApiResult<HospitalWard>>()
        repo.gate = gate
        vm.submitCreate()
        advanceUntilIdle()
        assertTrue("요청이 진행 중이어야 함", vm.uiState.value.form!!.submitting)
        return vm to gate
    }


    @Test
    fun `while creating in hospital A, switching hospital, reopening, editing, choosing and resubmitting are blocked`() = runTest(dispatcher) {
        val repo = FakeRepository(myWards = mutableListOf(ward("w1", "WARD_ADMIN")), hospitals = listOf(hospital, hospitalB))
        val (vm, gate) = startDelayedCreate(repo, "A 병동")

        vm.selectHospital("h2")
        vm.openCreateForm()
        vm.editName("바뀐 이름")
        vm.editTimeZone("UTC")
        vm.closeCreateForm()
        vm.choose("w1")
        vm.submitCreate()
        advanceUntilIdle()

        val during = vm.uiState.value
        assertEquals("h1", during.selectedHospitalId)
        assertEquals("A 병동", during.form!!.name)
        assertEquals("", during.form!!.timeZone)
        assertTrue(during.form!!.submitting)
        assertNull(during.chosenWardId)
        assertEquals(1, repo.createCalls.size)

        // A병원의 확정 실패는 A병원 양식에만 반영된다.
        gate.complete(ApiResult.Failure(ApiError.Rejected(409, "WARD_NAME_TAKEN")))
        advanceUntilIdle()
        val after = vm.uiState.value
        assertEquals("h1", after.selectedHospitalId)
        assertEquals("같은 병원에 같은 이름의 병동이 있습니다.", after.form!!.nameError)
        assertFalse(after.form!!.submitting)

        // 요청이 끝나면 병원 전환이 정상 동작한다.
        vm.selectHospital("h2")
        advanceUntilIdle()
        assertEquals("h2", vm.uiState.value.selectedHospitalId)
        assertNull(vm.uiState.value.form)
    }

    @Test
    fun `delayed success applies to hospital A only and hospital switching works afterwards`() = runTest(dispatcher) {
        val repo = FakeRepository(hospitals = listOf(hospital, hospitalB))
        val (vm, gate) = startDelayedCreate(repo, "A 병동")
        vm.selectHospital("h2")
        assertEquals("진행 중에는 병원 전환이 막혀야 함", "h1", vm.uiState.value.selectedHospitalId)

        val created = HospitalWard("a-1", "h1", "A 병동", "Asia/Seoul")
        repo.hospitalWards += created
        repo.myWards += MyWard("a-1", "A 병동", "Asia/Seoul", "h1", "가상 병원", setOf("WARD_ADMIN"), true)
        gate.complete(ApiResult.Success(created))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("h1", state.selectedHospitalId)
        assertNull(state.form)
        assertEquals("a-1", state.justCreatedWardId)
        assertEquals(listOf("a-1"), state.hospitalWards.map { it.wardId })

        vm.changeWard()
        vm.selectHospital("h2")
        advanceUntilIdle()
        assertEquals("h2", vm.uiState.value.selectedHospitalId)
        assertTrue(vm.uiState.value.hospitalWards.isEmpty())
    }

    @Test
    fun `retry after a lost delayed response keeps the same idempotency key`() = runTest(dispatcher) {
        val repo = FakeRepository(hospitals = listOf(hospital, hospitalB))
        val (vm, gate) = startDelayedCreate(repo, "A 병동")
        gate.complete(ApiResult.Failure(ApiError.Network))
        advanceUntilIdle()

        val retryGate = CompletableDeferred<ApiResult<HospitalWard>>()
        repo.gate = retryGate
        vm.submitCreate()
        advanceUntilIdle()
        vm.selectHospital("h2")
        assertEquals("h1", vm.uiState.value.selectedHospitalId)
        retryGate.complete(ApiResult.Success(HospitalWard("a-1", "h1", "A 병동", "Asia/Seoul")))
        advanceUntilIdle()

        assertEquals(2, repo.createCalls.size)
        assertEquals(repo.createCalls[0].key, repo.createCalls[1].key)
        assertNull(vm.uiState.value.form)
    }

    // ── 1293bf7 재리뷰 P2: 생성 성공 뒤 목록 갱신 동안 잠금이 풀리는 문제 ──

    @Test
    fun `lock stays held while lists refresh after a successful create`() = runTest(dispatcher) {
        val existing = ward("w1", "WARD_ADMIN")
        val repo = FakeRepository(myWards = mutableListOf(existing), hospitals = listOf(hospital, hospitalB))
        val vm = vm(repo)
        advanceUntilIdle()
        vm.openCreateForm()
        vm.editName("A 병동")
        val listGate = CompletableDeferred<Unit>()
        repo.myWardsGate = listGate // POST는 즉시 성공, 뒤따르는 목록 조회만 지연
        vm.submitCreate()
        advanceUntilIdle()
        assertEquals("POST는 끝났음", 1, repo.createCalls.size)
        assertTrue("목록 갱신 중에도 작업 중이어야 함", vm.uiState.value.isCreating)

        val callsBefore = repo.myWardsCalls
        vm.selectHospital("h2")
        vm.openCreateForm()
        vm.editName("끼어든 이름")
        vm.choose("w1")
        vm.refresh()
        vm.submitCreate()
        advanceUntilIdle()
        val during = vm.uiState.value
        assertEquals("h1", during.selectedHospitalId)
        assertNull("새 양식을 열 수 없음", during.form)
        assertNull("병동 선택 차단", during.chosenWardId)
        assertEquals("새로고침 차단", callsBefore, repo.myWardsCalls)
        assertEquals("재제출 차단", 1, repo.createCalls.size)

        listGate.complete(Unit)
        advanceUntilIdle()
        val after = vm.uiState.value
        assertEquals("h1", after.selectedHospitalId)
        assertEquals("새 병동이 선택됨", "new-1", after.chosenWardId)
        assertFalse("잠금 해제", after.isCreating)
        assertTrue(after.notice!!.contains("A 병동"))

        vm.changeWard()
        vm.selectHospital("h2")
        advanceUntilIdle()
        assertEquals("완료 뒤 병원 전환 가능", "h2", vm.uiState.value.selectedHospitalId)
    }

    @Test
    fun `list refresh failure after a successful create releases the lock and retries only the lists`() = runTest(dispatcher) {
        val repo = FakeRepository(hospitals = listOf(hospital, hospitalB))
        val vm = vm(repo)
        advanceUntilIdle()
        vm.openCreateForm()
        vm.editName("A 병동")
        repo.failNext = ApiError.Network // 생성 뒤 목록 조회만 실패
        vm.submitCreate()
        advanceUntilIdle()

        val failed = vm.uiState.value
        assertEquals(LoadState.Failed(ApiError.Network), failed.load)
        assertFalse("실패해도 잠금 해제", failed.isCreating)
        assertEquals(1, repo.createCalls.size)

        vm.refresh()
        advanceUntilIdle()
        val recovered = vm.uiState.value
        assertEquals(LoadState.Ready, recovered.load)
        assertEquals("생성 POST를 다시 보내지 않음", 1, repo.createCalls.size)
        assertEquals("목록 재시도 뒤 새 병동 선택", "new-1", recovered.chosenWardId)
        assertEquals("h1", recovered.selectedHospitalId)
        assertFalse(recovered.isCreating)
    }

    @Test
    fun `reload after a permission failure also holds the lock until it finishes`() = runTest(dispatcher) {
        val repo = FakeRepository(hospitals = listOf(hospital, hospitalB))
        val vm = vm(repo)
        advanceUntilIdle()
        vm.openCreateForm()
        vm.editName("권한 없음")
        repo.createResult = ApiResult.Failure(ApiError.Forbidden("HOSPITAL_ADMIN_REQUIRED"))
        val listGate = CompletableDeferred<Unit>()
        repo.myWardsGate = listGate
        vm.submitCreate()
        advanceUntilIdle()

        assertTrue("권한 실패 뒤 목록 갱신 중에도 잠금", vm.uiState.value.isCreating)
        vm.selectHospital("h2")
        assertEquals("h1", vm.uiState.value.selectedHospitalId)

        listGate.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isCreating)
        vm.selectHospital("h2")
        assertEquals("h2", vm.uiState.value.selectedHospitalId)
    }

    // ── PR #14 리뷰 P2-2: 성공 응답 해석 실패 시 멱등 키를 버리는 문제 ──

    @Test
    fun `unreadable success response keeps the key for the same input and a new input gets a new key`() = runTest(dispatcher) {
        val repo = FakeRepository(hospitals = listOf(hospital))
        val vm = vm(repo)
        advanceUntilIdle()
        vm.openCreateForm()
        vm.editName("해석 실패 병동")

        repo.createResult = ApiResult.Failure(ApiError.UnexpectedResponse)
        vm.submitCreate()
        advanceUntilIdle()
        assertEquals(ApiError.UnexpectedResponse, vm.uiState.value.form!!.failure)
        vm.submitCreate()
        advanceUntilIdle()
        assertEquals("결과 불명 뒤 같은 입력 재시도는 같은 키", repo.createCalls[0].key, repo.createCalls[1].key)

        // 정상 응답을 받으면 성공 처리하고 키를 정리한다.
        repo.createResult = null
        vm.submitCreate()
        advanceUntilIdle()
        assertEquals(repo.createCalls[0].key, repo.createCalls[2].key)
        assertNull(vm.uiState.value.form)
        vm.changeWard()
        vm.openCreateForm()
        vm.editName("해석 실패 병동")
        vm.submitCreate()
        advanceUntilIdle()
        assertNotEquals("성공 뒤에는 새 요청", repo.createCalls[0].key, repo.createCalls[3].key)
    }

    @Test
    fun `changing input after an unreadable response uses a new key`() = runTest(dispatcher) {
        val repo = FakeRepository(hospitals = listOf(hospital))
        val vm = vm(repo)
        advanceUntilIdle()
        vm.openCreateForm()
        vm.editName("첫 이름")
        repo.createResult = ApiResult.Failure(ApiError.UnexpectedResponse)
        vm.submitCreate()
        advanceUntilIdle()

        vm.editName("다른 이름")
        repo.createResult = null
        vm.submitCreate()
        advanceUntilIdle()
        assertNotEquals(repo.createCalls[0].key, repo.createCalls[1].key)
    }

    private fun ward(id: String, role: String) = MyWard(id, "병동 $id", "Asia/Seoul", "h1", "가상 병원", setOf(role), true)

    private data class CreateCall(val hospitalId: String, val name: String, val timeZone: String?, val key: String)

    private inner class FakeRepository(
        val myWards: MutableList<MyWard> = mutableListOf(),
        var hospitals: List<Hospital> = emptyList(),
        var failNext: ApiError? = null,
    ) : OrganizationRepository {
        val hospitalWards = mutableListOf<HospitalWard>()
        val createCalls = mutableListOf<CreateCall>()
        var createResult: ApiResult<HospitalWard>? = null
        var hospitalWardCalls = 0

        /** 설정하면 다음 생성 요청이 테스트가 완료시킬 때까지 응답하지 않는다(진행 중 요청 재현). */
        var gate: CompletableDeferred<ApiResult<HospitalWard>>? = null

        /** 설정하면 다음 본인 병동 조회가 테스트가 완료시킬 때까지 응답하지 않는다(생성 뒤 목록 갱신 지연 재현). */
        var myWardsGate: CompletableDeferred<Unit>? = null
        var myWardsCalls = 0

        override suspend fun myWards(): ApiResult<List<MyWard>> {
            myWardsCalls++
            myWardsGate?.let { pending -> myWardsGate = null; pending.await() }
            return failNext?.let { failNext = null; ApiResult.Failure(it) } ?: ApiResult.Success(myWards.toList())
        }

        override suspend fun myHospitals(): ApiResult<List<Hospital>> = ApiResult.Success(hospitals)

        override suspend fun hospitalWards(hospitalId: String): ApiResult<List<HospitalWard>> {
            hospitalWardCalls++
            return ApiResult.Success(hospitalWards.filter { it.hospitalId == hospitalId })
        }

        override suspend fun createWard(hospitalId: String, name: String, timeZone: String?, idempotencyKey: String): ApiResult<HospitalWard> {
            createCalls += CreateCall(hospitalId, name, timeZone, idempotencyKey)
            gate?.let { pending -> gate = null; return pending.await() }
            createResult?.let { return it }
            val created = HospitalWard("new-${createCalls.size}", hospitalId, name, timeZone ?: "Asia/Seoul")
            if (hospitalWards.none { it.wardId == created.wardId }) {
                hospitalWards += created
                myWards += MyWard(created.wardId, name, created.timeZone, hospitalId, "가상 병원", setOf("WARD_ADMIN"), true)
            }
            return ApiResult.Success(created)
        }
    }
}
