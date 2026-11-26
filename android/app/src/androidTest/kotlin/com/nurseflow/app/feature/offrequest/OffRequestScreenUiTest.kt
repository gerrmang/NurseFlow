package com.nurseflow.app.feature.offrequest

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nurseflow.app.core.network.*
import com.nurseflow.app.ui.theme.NurseFlowTheme
import okhttp3.Headers.Companion.toHeaders
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.time.YearMonth

/** Compose interaction checks with fictional API data. Real Spring verification is separate. */
@RunWith(AndroidJUnit4::class)
class OffRequestScreenUiTest {
    @get:Rule val rule = createComposeRule()
    private var rows = emptyList<OffRequest>()
    private var writes = 0
    private var deadline = "2099-01-01T00:00:00Z"
    private var limit = 4
    private var revision = 0
    private var loseWriteResponse = false
    private var identityMissing = false
    private val api = object : OffRequestApi {
        override suspend fun list(ward: String, month: String): OffRequestList {
            if (identityMissing) throw ApiException(403, "NURSE_IDENTITY_REQUIRED", "Fictional missing identity")
            return OffRequestList(rows, deadline, limit)
        }
        override suspend fun submit(ward: String, input: OffRequestInput): OffRequest {
            writes++
            return OffRequest("r$writes", input.nurseId ?: "n1", input.date, "SUBMITTED", if (input.nurseId == null) "NURSE" else "ADMIN", "2026-11-01T00:00:00Z", afterDeadline = deadline.startsWith("2000")).also { rows = rows + it }
        }
        private fun decide(id: String, status: String): OffRequest {
            writes++; val result = rows.first { it.requestId == id }.copy(status = status, decidedAt = "2026-11-02T00:00:00Z")
            rows = rows.map { if (it.requestId == id) result else it }; return result
        }
        override suspend fun confirm(ward: String, id: String) = decide(id, "CONFIRMED")
        override suspend fun reject(ward: String, id: String) = decide(id, "REJECTED")
        override suspend fun withdraw(ward: String, id: String) = decide(id, "WITHDRAWN")
        override suspend fun policy(ward: String) = Response.success(OffRequestPolicy(limit, 15, revision), mapOf("ETag" to "\"policy-$revision\"").toHeaders())
        override suspend fun savePolicy(ward: String, etag: String, input: OffRequestPolicyInput): Response<OffRequestPolicy> {
            writes++; limit = input.monthlyLimit; revision++
            if (loseWriteResponse) throw java.io.IOException("Fictional lost response")
            return policy(ward)
        }
        override suspend fun eligibility(ward: String) = OffEligibility(true, revision, listOf(OffEligibleNurse("n1", "가상 간호사", true, "ELIGIBLE")))
    }
    private fun show(admin: Boolean = false) {
        val vm = OffRequestViewModel(api, "w1", admin, { listOf(MembershipDto("w1", "가상 병동", listOf(if (admin) "WARD_ADMIN" else "WARD_MEMBER"), true)) }, initialMonth = YearMonth.of(2026, 12))
        rule.setContent { NurseFlowTheme { OffRequestScreen("가상 병동", vm) {} } }
        rule.onNodeWithText("가상 병동 · 희망 오프").assertExists()
    }
    private fun scrollToText(text: String, substring: Boolean = false) {
        rule.onNodeWithTag("off-list").performScrollToNode(hasText(text, substring = substring))
        rule.onNodeWithText(text, substring = substring).assertIsDisplayed()
    }
    @Test fun selfSubmissionRequiresConfirmationThenShowsHistoryAndWithdrawal() {
        show(); rule.onNodeWithText("10", substring = false).performScrollTo().performClick()
        rule.onNodeWithText("신청", substring = false).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(0, writes) }
        rule.onNodeWithText("제출", substring = false).performClick()
        scrollToText("2026-12-10 · 신청")
        rule.onNodeWithText("철회", substring = false).performScrollTo().performClick()
        rule.onNodeWithText("취소", substring = false).performClick()
        rule.runOnIdle { assertEquals(1, writes) }
        rule.onNodeWithText("철회", substring = false).performScrollTo().performClick()
        rule.onNode(hasText("철회", substring = false) and hasAnyAncestor(isDialog())).performClick()
        scrollToText("2026-12-10 · 철회")
    }
    @Test fun lateRequestIsExplicitExceptionWithNoSelfWithdrawal() {
        deadline = "2000-01-01T00:00:00Z"; show()
        rule.onNodeWithText("마감 후 예외 접수 중", substring = true).assertExists()
        rule.onNodeWithText("11", substring = false).performScrollTo().performClick()
        rule.onNode(hasText("예외 요청 제출", substring = false) and hasClickAction()).performScrollTo().performClick()
        rule.onNodeWithText("제출", substring = false).performClick()
        scrollToText("2026-12-11 · 신청")
        rule.onNodeWithText("본인 신청 · 마감 후 예외").assertExists()
        rule.onNodeWithText("철회", substring = false).assertDoesNotExist()
    }
    @Test fun adminCanEnterConfirmAndWithdrawEvenWithZeroOwnLimit() {
        limit = 0; show(true)
        rule.onNodeWithText("대상 간호사 선택").performScrollTo().performClick()
        rule.onNodeWithText("가상 간호사", substring = false).performClick()
        rule.onNodeWithText("12", substring = false).performScrollTo().performClick()
        rule.onNodeWithText("대리 입력", substring = false).performScrollTo().performClick()
        rule.onNodeWithText("제출", substring = false).performClick()
        scrollToText("2026-12-12 · 신청")
        rule.onNodeWithText("확정", substring = false).performScrollTo().performClick()
        rule.onNodeWithText("확정 또는 확정분 철회는 초안 재검증이 필요해요. 기존 공개본은 유지돼요.").assertExists()
        rule.onNode(hasText("확정", substring = false) and hasAnyAncestor(isDialog())).performClick()
        scrollToText("2026-12-12 · 확정")
        rule.onNodeWithText("철회", substring = false).performScrollTo().performClick()
        rule.onNode(hasText("철회", substring = false) and hasAnyAncestor(isDialog())).performClick()
        scrollToText("2026-12-12 · 철회")
        rule.runOnIdle { assertEquals(3, writes) }
    }
    @Test fun adminPolicySaveRequiresConfirmationAndAppliesNewLimit() {
        show(true)
        rule.onNodeWithTag("off-list").performScrollToNode(hasText("월 본인 신청 한도 (0~31일)"))
        rule.onNodeWithText("월 본인 신청 한도 (0~31일)").performTextReplacement("0")
        rule.onNodeWithText("정책 저장", substring = false).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(0, writes) }
        rule.onNodeWithText("저장", substring = false).performClick()
        rule.runOnIdle { assertEquals(1, writes); assertEquals(0, limit) }
        scrollToText("월 본인 신청 한도 0일", substring = true)
    }
    @Test fun policyErrorStaysVisibleWhileScrolledToBottom() {
        show(true)
        rule.onNodeWithTag("off-list").performScrollToNode(hasText("월 본인 신청 한도 (0~31일)"))
        rule.onNodeWithText("월 본인 신청 한도 (0~31일)").performTextReplacement("32")
        rule.onNodeWithText("월 한도는 0~31일로 입력해 주세요.").assertIsDisplayed()
        rule.onNodeWithText("정책 저장", substring = false).performScrollTo().performClick()
        rule.onNodeWithText("저장", substring = false).performClick()
        rule.onNodeWithTag("off-status").assertIsDisplayed()
        rule.onNodeWithText("월 한도 0~31일과 전달 마감일 1~28일을 입력해 주세요.").assertIsDisplayed()
        rule.runOnIdle { assertEquals(0, writes) }
    }
    @Test fun lostPolicyResponseOffersRecoveryWithoutScrollingBackToTop() {
        loseWriteResponse = true; show(true)
        rule.onNodeWithTag("off-list").performScrollToNode(hasText("정책 저장", substring = false))
        rule.onNodeWithText("정책 저장", substring = false).performClick()
        rule.onNodeWithText("저장", substring = false).performClick()
        rule.onNodeWithTag("off-status").assertIsDisplayed()
        rule.onNodeWithText("최신 목록 다시 조회").assertIsDisplayed()
        rule.onNodeWithText("최신 목록 확인 후 계속").assertIsDisplayed().assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(1, writes) }
        rule.onNodeWithText("정책 저장", substring = false).assertIsEnabled()
    }
    @Test fun missingIdentityOffersRetryWithoutRoleLossMessage() {
        identityMissing = true; show()
        rule.onNodeWithText(offRequestError("NURSE_IDENTITY_REQUIRED")).assertIsDisplayed()
        rule.onNodeWithText("병동 목록으로 돌아가 소속과 역할을 다시 확인해 주세요.").assertDoesNotExist()
        rule.onNodeWithText("최신 목록 다시 조회").assertIsEnabled()
        rule.runOnIdle { identityMissing = false }
        rule.onNodeWithText("최신 목록 다시 조회").performClick()
        rule.onNodeWithTag("off-list").performScrollToNode(hasText("선택 날짜: 없음"))
        rule.onNodeWithText("선택 날짜: 없음").assertIsDisplayed()
    }
    @Test fun weekdaysUseTheSameColumnCentersAsDates() {
        show()
        rule.onNodeWithTag("off-list").performScrollToNode(hasTestTag("off-day-1"))
        // December 2026 starts on Tuesday. Check all seven columns, including the next Monday.
        for (day in 1..7) {
            val weekday = java.time.LocalDate.of(2026, 12, day).dayOfWeek.value
            val header = rule.onNodeWithTag("off-weekday-$weekday").fetchSemanticsNode().boundsInRoot
            val date = rule.onNodeWithTag("off-day-$day", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals("Day $day must be centered under weekday $weekday", header.center.x, date.center.x, 0.5f)
        }
    }
}
