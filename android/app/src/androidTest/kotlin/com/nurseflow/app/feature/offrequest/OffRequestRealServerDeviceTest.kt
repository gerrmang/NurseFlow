package com.nurseflow.app.feature.offrequest

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nurseflow.app.AppContainer
import com.nurseflow.app.MainActivity
import com.nurseflow.app.core.network.*
import com.nurseflow.app.core.session.*
import com.nurseflow.app.test.IsolatedSessionTestApplication
import com.nurseflow.app.test.TestTokenStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.time.YearMonth
import java.io.File
import java.util.UUID

/** Opt-in real Activity/Compose/Retrofit/Spring; fixture OIDC identities are fictional, no SDK login. */
@RunWith(AndroidJUnit4::class)
class OffRequestRealServerDeviceTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var previousProxy: java.net.ProxySelector? = null
    @Before fun isolateFixtureProxy() {
        previousProxy = java.net.ProxySelector.getDefault()
        java.net.ProxySelector.setDefault(object : java.net.ProxySelector() {
            override fun select(uri: java.net.URI) = if (uri.host == "127.0.0.1") listOf(java.net.Proxy.NO_PROXY) else previousProxy?.select(uri) ?: listOf(java.net.Proxy.NO_PROXY)
            override fun connectFailed(uri: java.net.URI, address: java.net.SocketAddress, error: java.io.IOException) = Unit
        })
    }
    @After fun restoreProxy() { java.net.ProxySelector.setDefault(previousProxy) }
    @Test fun twoAccountsSubmitConfirmAndReenterWithSessionIsolation() = runBlocking {
        assumeTrue("Start b8a-server.mjs and adb reverse 18090/18091", InstrumentationRegistry.getArguments().getString("b8aE2e") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as IsolatedSessionTestApplication
        val original = app.container
        val base = "http://127.0.0.1:18091/"
        val suffix = UUID.randomUUID().toString()
        suspend fun token(role: String): String = withContext(Dispatchers.IO) {
            OkHttpClient().newCall(Request.Builder().url("http://127.0.0.1:18090/id-token?account=$role-$suffix").build()).execute().use {
                Json.parseToJsonElement(it.body!!.string()).jsonObject.getValue("idToken").jsonPrimitive.content
            }
        }
        val container = AppContainer(context, base, TestTokenStore())
        withTimeout(5_000) { container.session.state.first { it == SessionState.SignedOut } }
        container.session.loginWithKakao(token("admin"), "fictional-nonce")
        val creation = ApiFactory.wardCreationApi(base, container.session)
        val hospital = creation.searchHospitals("B6").items.single()
        val ward = creation.createWard(suffix, CreateWardBody(HospitalRefBody(hospital.source, hospital.externalId), "B8a 가상 기기 병동"))
        val nurseRecord = container.staffAdminApi().createStaff(ward.wardId, AdminStaffInput("기기 가상 간호사"))
        val invitation = container.invitationApi.create(ward.wardId, CreateInvitationBody("WARD_MEMBER", nurseRecord.nurseId))
        val member = SessionManager(ApiFactory.authApi(base), object : TokenStore {
            override suspend fun read(): String? = null
            override suspend fun write(refreshToken: String) = Unit
            override suspend fun clear() = Unit
        })
        member.loginWithKakao(token("member"), "fictional-nonce")
        ApiFactory.invitationApi(base, member).redeem(RedeemInvitationBody(invitation.token, "2024-01"))
        container.session.logout()
        container.session.loginWithKakao(token("member"), "fictional-nonce")
        app.useContainer(container)
        fun waitText(text: String) { rule.waitUntil(15_000) { rule.onAllNodesWithText(text, substring = false).fetchSemanticsNodes().isNotEmpty() } }
        fun waitListText(text: String) {
            rule.waitUntil(15_000) {
                try {
                    rule.onNodeWithTag("off-list").performScrollToNode(hasText(text, substring = false))
                    rule.onNodeWithText(text, substring = false).assertIsDisplayed()
                    true
                } catch (_: AssertionError) { false }
            }
        }
        fun proof(name: String) {
            rule.waitForIdle()
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(context.getExternalFilesDir(null), name).outputStream().use {
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            screenshot.recycle()
        }
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)).use { scenario ->
                waitText("희망 오프")
                rule.onNodeWithText("희망 오프", substring = false).performScrollTo().performClick()
                waitText("내 희망 오프")
                // Two months ahead is safely before its prior-month deadline.
                repeat(2) { rule.onNodeWithText("다음 달 ›").performScrollTo().performClick(); rule.waitForIdle() }
                val month = YearMonth.now().plusMonths(2)
                waitText("${month.year}년 ${month.monthValue}월")
                rule.waitUntil(10_000) { rule.onAllNodes(hasText("10", substring = false) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("10", substring = false).performScrollTo().performClick()
                rule.onNode(hasText("신청", substring = false) and hasClickAction()).performScrollTo().performClick()
                rule.onNodeWithText("제출", substring = false).performClick()
                waitListText("${month.atDay(10)} · 신청")
                scenario.recreate()
                waitListText("${month.atDay(10)} · 신청")
                waitListText("‹ 병동 목록")
                rule.onNodeWithText("‹ 병동 목록").performClick()
                rule.onNodeWithText("로그아웃", substring = false).performClick()
                waitText("카카오 로그인")
                assertEquals(SessionState.SignedOut, container.session.state.value)
                container.session.loginWithKakao(token("admin"), "fictional-nonce")
                waitText("희망 오프")
                rule.onNodeWithText("희망 오프", substring = false).performScrollTo().performClick()
                waitText("관리자 취합·판단")
                repeat(2) { rule.onNodeWithText("다음 달 ›").performScrollTo().performClick(); rule.waitForIdle() }
                waitListText("${month.atDay(10)} · 신청")
                rule.onNodeWithText("확정", substring = false).performScrollTo().performClick()
                rule.onNode(hasText("확정", substring = false) and hasAnyAncestor(isDialog())).performClick()
                waitListText("${month.atDay(10)} · 확정")
                proof("b8a-admin-confirmed.png")
                waitListText("‹ 병동 목록")
                rule.onNodeWithText("‹ 병동 목록").performClick()
                rule.onNodeWithText("로그아웃", substring = false).performClick()
                waitText("카카오 로그인")
                container.session.loginWithKakao(token("member"), "fictional-nonce")
                waitText("희망 오프"); rule.onNodeWithText("희망 오프", substring = false).performScrollTo().performClick()
                waitText("내 희망 오프")
                repeat(2) { rule.onNodeWithText("다음 달 ›").performScrollTo().performClick(); rule.waitForIdle() }
                waitListText("${month.atDay(10)} · 확정")
                rule.onNodeWithText("철회", substring = false).assertDoesNotExist()
                assertEquals("CONFIRMED", ApiFactory.offRequestApi(base, member).list(ward.wardId, month.toString()).items.single().status)
                proof("b8a-nurse-confirmed.png")
            }
        } finally { container.session.logout(); app.useContainer(original) }
    }
}
