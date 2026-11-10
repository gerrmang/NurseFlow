package com.nurseflow.app.feature.roster

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nurseflow.app.AppContainer
import com.nurseflow.app.MainActivity
import com.nurseflow.app.core.network.ApiException
import com.nurseflow.app.core.network.apiCall
import com.nurseflow.app.core.session.SessionState
import com.nurseflow.app.test.IsolatedSessionTestApplication
import com.nurseflow.app.test.TestTokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth
import java.time.ZoneId

/** Actual Compose UI, production HTTP, Spring publication and permissions; fake external OIDC only. */
@RunWith(AndroidJUnit4::class)
class PublishedRosterDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val http = OkHttpClient()
    private val backend = "http://127.0.0.1:18083/"
    private val controls = "http://127.0.0.1:18084/"

    @Test fun publicationReentryMonthModesAndPermissionIsolation() = runBlocking {
        assumeTrue("Opt in with -e rosterE2e true and start AndroidRosterE2eServerTest",
            InstrumentationRegistry.getArguments().getString("rosterE2e") == "true")
        val app = instrumentation.targetContext.applicationContext as IsolatedSessionTestApplication
        val original = app.container
        val container = AppContainer(instrumentation.targetContext, backend, TestTokenStore())
        app.useContainer(container)
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var passed = false
        try {
            await { container.session.state.value == SessionState.SignedOut }
            login(container, "member")
            waitText("가상 공개 병동")
            val ward = container.api.wards().items.single().wardId
            val month = YearMonth.now(ZoneId.of("Asia/Seoul"))
            assertApiCode("PUBLISHED_ROSTER_NOT_FOUND") { container.api.publishedMine(ward, month.toString()) }
            click("가상 공개 병동")
            waitText("아직 확정된 근무표가 없습니다")
            waitText("${month.year}년 ${month.monthValue}월")
            assertFalse(hasText("가상 근무표 시연 보기 (서버 미연동)"))
            click("‹ 병동 목록")
            publish("first")
            click("가상 공개 병동")
            waitText("D · 07:00–15:00")
            click("병동 전체")
            waitText("07:00–15:00 · 가상 근무자 member")
            click("다음 달 ›")
            waitText("아직 확정된 근무표가 없습니다")
            val next = month.plusMonths(1)
            waitText("${next.year}년 ${next.monthValue}월")
            click("내 근무")
            waitText("아직 확정된 근무표가 없습니다")
            click("‹ 이전 달")
            waitText("D · 07:00–15:00")
            click("병동 전체")
            waitText("07:00–15:00 · 가상 근무자 member")
            click("‹ 병동 목록")
            publish("second")
            click("가상 공개 병동")
            waitText("07:00–15:00 · 가상 근무자 limited")
            assertFalse(hasText("07:00–15:00 · 가상 근무자 member"))
            click("내 근무")
            waitText("이번 달 배정된 근무가 없습니다")
            container.session.logout()
            waitText("카카오 로그인")
            assertFalse(hasText("이번 달 배정된 근무가 없습니다"))
            login(container, "limited")
            waitText("가상 공개 병동")
            assertApiCode("WARD_CALENDAR_VIEW_REQUIRED") { container.api.publishedWard(ward, month.toString()) }
            click("가상 공개 병동")
            waitText("D · 07:00–15:00")
            assertFalse(hasText("병동 전체"))
            assertFalse(hasText("이번 달 배정된 근무가 없습니다"))
            container.session.logout()
            waitText("카카오 로그인")
            login(container, "admin")
            waitText("가상 공개 병동")
            assertApiCode("NURSE_IDENTITY_REQUIRED") { container.api.publishedMine(ward, month.toString()) }
            click("가상 공개 병동")
            waitText("본인 근무표에 연결된 간호사 정보가 없습니다.")
            assertFalse(hasText("이 병동의 근무표를 볼 권한이 없어요."))
            click("병동 전체 보기")
            waitText("07:00–15:00 · 가상 근무자 limited")
            passed = true
        } finally {
            try {
                container.session.logout()
                instrumentation.runOnMainSync { activity.finish() }
                app.useContainer(original)
            } finally {
                http.newCall(Request.Builder().url(controls + "finish?passed=$passed").build()).execute().close()
            }
        }
    }

    private suspend fun login(container: AppContainer, account: String) {
        val identity = http.newCall(Request.Builder().url(controls + "identity?account=$account").build()).execute().use {
            check(it.code == 200) { "Fixture identity HTTP ${it.code}" }
            JSONObject(it.body!!.string())
        }
        container.session.loginWithKakao(identity.getString("idToken"), identity.getString("nonce"))
    }

    private fun publish(phase: String) {
        http.newCall(Request.Builder().url(controls + "publish?phase=$phase").post(ByteArray(0).toRequestBody()).build())
            .execute().use { assertEquals("Real server publication", 200, it.code) }
    }

    private suspend fun assertApiCode(code: String, action: suspend () -> Any) {
        try { apiCall { action() }; fail("Expected server code $code") }
        catch (e: ApiException) { assertEquals(code, e.code) }
    }

    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == text) return node
        for (i in 0 until node.childCount) find(node.getChild(i), text)?.let { return it }
        return null
    }

    private fun hasText(text: String) = find(instrumentation.uiAutomation.rootInActiveWindow, text) != null
    private fun await(check: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < end) {
            if (check()) return
            SystemClock.sleep(100)
        }
        fail("Expected device state was not reached")
    }
    private fun waitText(text: String) = await { hasText(text) }
    private fun click(text: String) {
        // The departing screen can still contain the ward header. Wait for the clickable card,
        // not merely matching text, before acting after a back-navigation transition.
        await {
            var node = find(instrumentation.uiAutomation.rootInActiveWindow, text)
            while (node != null && !node.isClickable) node = node.parent
            node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
    }
}
