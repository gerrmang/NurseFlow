package com.nurseflow.app.feature.ward.create

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nurseflow.app.core.network.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI checks of [WardCreateScreen] against a fake API (fictional data only). They need a
 * device or emulator; the unit-test task does not run them. Real-server behaviour is not covered.
 */
@RunWith(AndroidJUnit4::class)
class WardCreateScreenUiTest {
    @get:Rule val rule = createComposeRule()

    private class ScriptedApi : WardCreationApi {
        var createFailure: ApiException? = null
        val createKeys = mutableListOf<String>()
        override suspend fun searchHospitals(query: String, page: Int, size: Int) = HospitalSearchPageDto(
            listOf(
                HospitalCandidateDto("HIRA", "h1", "가상대학교병원", "가상시 가상구 1", "종합병원"),
                HospitalCandidateDto("HIRA", "h2", "가상의료원", "가상시 가상구 2", "병원"),
            ),
            1, 20, 2, "가상 출처 · 기준 2026-01-01",
        )
        override suspend fun createWard(idempotencyKey: String, body: CreateWardBody): MembershipDto {
            createKeys += idempotencyKey
            createFailure?.let { throw it }
            return MembershipDto("ward-new", body.wardName, listOf("WARD_ADMIN"), true, "가상대학교병원")
        }
    }

    private val api = ScriptedApi()
    private fun model() = WardCreateViewModel(api, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

    @Test fun hospitalNotFoundNoticeIsShownOnTheSearchStepAndTheRefusedRowIsDisabled() {
        val vm = model()
        vm.setQuery("가상 병원"); vm.search()
        vm.select((vm.state.value.search as SearchState.Results).items[0])
        vm.setWardName("가상 내과 병동")
        api.createFailure = ApiException(422, "HOSPITAL_NOT_FOUND", "고정 메시지")
        vm.create()
        rule.setContent { WardCreateScreen(vm, {}, {}, {}, {}) }
        rule.onNodeWithTag(HOSPITAL_NOT_FOUND_NOTICE_TAG).assertIsDisplayed()
        rule.onNodeWithText("선택한 병원을 찾을 수 없어요. 다시 검색해 주세요.").assertIsDisplayed()
        // Tapping the refused hospital again changes nothing; the other one is still selectable.
        rule.onNodeWithText("가상대학교병원").performClick()
        assertEquals(CreateStep.SEARCH, vm.state.value.step)
        rule.onNodeWithText("가상의료원").performClick()
        assertEquals(CreateStep.NAME, vm.state.value.step)
        rule.onNodeWithTag(HOSPITAL_NOT_FOUND_NOTICE_TAG).assertDoesNotExist()
    }

    @Test fun nameStepButtonsAreReachableOnAShortScreenWithALongLossNotice() {
        val vm = model()
        vm.setQuery("가상 병원"); vm.search()
        vm.select((vm.state.value.search as SearchState.Results).items[0])
        vm.setWardName("가상 내과 병동")
        api.createFailure = ApiException(0, "NETWORK", "고정 메시지")
        vm.create() // response lost: retry notice appears
        vm.setWardName("가상 내과 병동 2") // changed input adds the "check ward list" button
        var listChecked = false
        rule.setContent {
            Box(Modifier.size(width = 320.dp, height = 180.dp)) { // far shorter than the content
                WardCreateScreen(vm, {}, { listChecked = true }, {}, {})
            }
        }
        rule.onNodeWithText("병동 목록 확인").performScrollTo().performClick()
        assertTrue(listChecked)
        api.createFailure = null
        rule.onNodeWithTag(CREATE_BUTTON_TAG).performScrollTo().performClick()
        assertEquals(2, api.createKeys.size)
        assertTrue(vm.state.value.created != null)
    }
}
