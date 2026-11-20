package com.nurseflow.app.feature.staff

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nurseflow.app.core.network.*
import com.nurseflow.app.ui.theme.NurseFlowTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

/** Fake API Compose checks; real-server verification is separate. All people are fictional. */
@RunWith(AndroidJUnit4::class)
class StaffAdminScreenUiTest {
    @get:Rule val rule = createComposeRule()
    private val team = AdminTeam("t1", "가상 A팀", 1)
    private var names = listOf("n1", "n2", "n3")
    private var saved: List<String>? = null
    private var membershipWrites = 0
    private fun nurses() = names.mapIndexed { index, id ->
        AdminStaff(id, "가상 $id", emptyList(), true, false, AdminCareerProfile("MISSING"), team, index + 1, false)
    }
    private val api = Proxy.newProxyInstance(StaffAdminApi::class.java.classLoader, arrayOf(StaffAdminApi::class.java)) { _, method, args ->
        when (method.name) {
            "teams" -> Items(listOf(team))
            "staff" -> Items(nurses())
            "members" -> Items(listOf(AdminMember("u1", "가상 관리자", listOf("WARD_ADMIN"), true)))
            "policy" -> AdminCareerPolicy(listOf(12, 36, 72, 120), listOf("간호사"), true)
            "eligibility" -> AdminEligibility(true, 1)
            "adjustments" -> Items<AdminAdjustment>(emptyList())
            "order" -> { saved = (args!![2] as AdminTeamOrder).nurseIds; names = saved!!; Items(nurses()) }
            "membership" -> {
                membershipWrites++
                @Suppress("UNCHECKED_CAST")
                val continuation = args!!.last() as kotlin.coroutines.Continuation<Any?>
                continuation.resumeWith(Result.failure(ApiException(409, "LAST_WARD_ADMIN", "고정")))
                kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
            }
            else -> error("Unexpected fake API call: ${method.name}")
        }
    } as StaffAdminApi
    private fun show() {
        val model = StaffAdminViewModel(api, "w1", { listOf(MembershipDto("w1", "가상 병동", listOf("WARD_ADMIN"), true)) })
        rule.setContent { NurseFlowTheme { StaffAdminScreen("가상 병동", model, {}, {}) } }
        rule.onNodeWithText("팀 1개 · 간호사 기록 3명").assertExists()
    }
    @Test fun buttonOrderIsSavedAndReloaded() {
        show()
        rule.onNodeWithContentDescription("가상 n1 아래로").performScrollTo().performClick()
        rule.onNodeWithText("가상 A팀 경력순 저장").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(listOf("n2", "n1", "n3"), saved) }
        rule.onNodeWithText("1. 가상 n2").assertExists()
    }
    @Test fun serverDemoModeIsClearlyLabeled() {
        show()
        rule.onNodeWithTag("staff-demo-mode").assertTextContains("시연 모드 · 계정이 연결되지 않은 가상 간호사도", substring = true)
    }
    @Test fun longPressDragChangesOrderBeforeExplicitSave() {
        show()
        rule.onNodeWithTag("order-n1").performScrollTo().performTouchInput {
            down(center); advanceEventTime(700); moveBy(androidx.compose.ui.geometry.Offset(0f, height * 1.3f), 500); up()
        }
        rule.onNodeWithText("가상 A팀 경력순 저장").performScrollTo().assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(listOf("n2", "n1", "n3"), saved) }
    }
    @Test fun lastAdminChangeRequiresConfirmationAndShowsServerRefusal() {
        show(); rule.onNodeWithText("구성원", substring = false).performClick()
        rule.onNodeWithText("관리자 해제").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(0, membershipWrites) }
        rule.onNodeWithText("변경", substring = false).performClick()
        rule.onNodeWithTag("staff-notice").assertTextContains("마지막 활성 관리자", substring = true)
        rule.runOnIdle { assertEquals(1, membershipWrites) }
    }
}
