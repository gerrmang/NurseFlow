package com.nurseflow.app.feature.invite

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.nurseflow.app.AppContainer
import com.nurseflow.app.MainActivity
import com.nurseflow.app.core.session.SessionState
import com.nurseflow.app.test.IsolatedSessionTestApplication
import com.nurseflow.app.test.TestTokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/** Real manifest filter, MainActivity, root and HTTP; a fictional local server stands in for the backend. */
@RunWith(AndroidJUnit4::class)
class InviteDeepLinkDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val server = MockWebServer()
    private val joined = AtomicBoolean(false)
    private val previews = Collections.synchronizedList(mutableListOf<String>())
    private val redeems = Collections.synchronizedList(mutableListOf<JSONObject>())
    private val requestPaths = Collections.synchronizedList(mutableListOf<String>())
    private lateinit var container: AppContainer
    private lateinit var original: AppContainer
    private val memberToken = "m".repeat(43)
    private val expiredToken = "e".repeat(43)
    private val wardJson = """{"wardId":"fake-invite-ward","wardName":"가상 초대 병동","roles":["WARD_MEMBER"],"canViewWardCalendar":true}"""

    @Before fun serveFictionalBackend() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requestPaths += request.path.orEmpty()
                return when (request.path) {
                    "/api/v1/auth/kakao/session" -> json("""{"accessToken":"fake-invite-access","accessTokenExpiresAt":"2099-01-01T00:00:00Z","refreshToken":"fake-invite-refresh","refreshTokenExpiresAt":"2099-02-01T00:00:00Z","user":{"userId":"fake-invite-user","displayName":"가상 초대 사용자"}}""")
                    "/api/v1/auth/logout" -> MockResponse().setResponseCode(204)
                    "/api/v1/me" -> json("""{"userId":"fake-invite-user","displayName":"가상 초대 사용자","memberships":[]}""")
                    "/api/v1/wards" -> json(if (joined.get()) """{"items":[$wardJson]}""" else """{"items":[]}""")
                    "/api/v1/invitations/preview" -> {
                        val token = JSONObject(request.body.readUtf8()).getString("token")
                        previews += token
                        if (token == expiredToken) json("""{"code":"INVITATION_EXPIRED"}""", 410)
                        else json("""{"wardId":"fake-invite-ward","wardName":"가상 초대 병동","role":"WARD_MEMBER","expiresAt":"2099-01-01T00:00:00Z","linksExistingNurse":true}""")
                    }
                    "/api/v1/invitations/redeem" -> {
                        redeems += JSONObject(request.body.readUtf8())
                        joined.set(true)
                        json(wardJson, 201)
                    }
                    else -> if (request.path.orEmpty().startsWith("/api/v1/wards/fake-invite-ward/published-rosters"))
                        json("""{"code":"PUBLISHED_ROSTER_NOT_FOUND"}""", 404) else MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        val application = instrumentation.targetContext.applicationContext as IsolatedSessionTestApplication
        original = application.container
        val baseUrl = server.url("/").toString() // resolves the host: not on the main thread
        instrumentation.runOnMainSync {
            container = AppContainer(application, baseUrl, TestTokenStore())
            application.useContainer(container)
        }
    }

    @After fun restore() {
        val open = mainActivities()
        instrumentation.runOnMainSync { open.forEach { it.finish() } }
        val application = instrumentation.targetContext.applicationContext as IsolatedSessionTestApplication
        application.useContainer(original)
        server.shutdown()
    }

    private fun link(token: String) = Uri.parse("nurseflow://invite#token=$token")

    /** Implicit VIEW intent, resolved through the manifest filter like a link tap from another app. */
    private fun viewIntent(uri: Uri) = Intent(Intent.ACTION_VIEW, uri)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .setPackage(instrumentation.targetContext.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @Test fun manifestRoutesInviteLinkToMainActivityOnly() {
        val resolved = instrumentation.targetContext.packageManager.queryIntentActivities(viewIntent(link(memberToken)), 0)
        assertEquals(listOf(MainActivity::class.java.name), resolved.map { it.activityInfo.name })
    }

    @Test fun coldLinkWaitsForLoginThenPreviewsJoinsAndEntersWard() {
        val first = instrumentation.startActivitySync(viewIntent(link(memberToken))
            .setComponent(ComponentName(instrumentation.targetContext, MainActivity::class.java))) as MainActivity
        awaitText("병동 초대를 받았어요. 초대 내용을 확인하려면 먼저 로그인해 주세요.")
        assertFalse(hasText("가상 초대 병동")) // nothing guessed before login
        assertTrue(previews.isEmpty())
        instrumentation.runOnMainSync { assertNull("The link must not stay in the Activity intent", first.intent.data) }
        login()
        awaitText("가상 초대 병동")
        awaitText("관리자가 등록한 간호사 기록과 연결됩니다.")
        // Rotation-like recreation keeps the preview without re-reading the link.
        instrumentation.runOnMainSync { first.recreate() }
        await { first.isDestroyed && mainActivities().size == 1 }
        awaitText("가상 초대 병동")
        assertEquals(listOf(memberToken), previews.toList())
        click("가입하기")
        awaitText("병원 입사 연월을 입력해 주세요.")
        typeInto("병원 입사 연월 (YYYY-MM)", "2020-03")
        click("가입하기")
        awaitText("아직 확정된 근무표가 없습니다")
        awaitText("가상 초대 병동")
        val body = redeems.single()
        assertEquals(setOf("token", "hospitalJoinedOn"), body.keys().asSequence().toSet())
        assertEquals("2020-03", body.getString("hospitalJoinedOn"))
        assertNull(container.invites.current.value)
        assertTrue("Token must never be in a URL", requestPaths.none { it.contains(memberToken) })
    }

    @Test fun warmLinkReachesRunningActivityAndLogoutDropsIt() {
        val first = launchMain()
        awaitText("카카오 로그인")
        login()
        awaitText("가상 초대 사용자님, 안녕하세요.")
        instrumentation.targetContext.startActivity(viewIntent(link(memberToken)))
        awaitText("관리자가 등록한 간호사 기록과 연결됩니다.")
        assertEquals("singleTop delivers to the running Activity", listOf(first), mainActivities())
        runBlocking { container.session.logout() }
        awaitText("카카오 로그인")
        assertNull(container.invites.current.value)
        assertFalse(hasText("병동 초대를 받았어요. 초대 내용을 확인하려면 먼저 로그인해 주세요."))
        assertTrue(redeems.isEmpty())
    }

    @Test fun expiredAndMalformedLinksShowDistinctErrors() {
        launchMain()
        awaitText("카카오 로그인")
        login()
        awaitText("가상 초대 사용자님, 안녕하세요.")
        instrumentation.targetContext.startActivity(viewIntent(link(expiredToken)))
        awaitText(InviteFailure.EXPIRED.message)
        click("닫기")
        awaitText("가상 초대 사용자님, 안녕하세요.")
        assertNull(container.invites.current.value)
        instrumentation.targetContext.startActivity(viewIntent(Uri.parse("nurseflow://invite#token=short")))
        awaitText("올바른 초대 링크가 아니에요. 관리자에게 받은 링크를 다시 열어 주세요.")
        click("확인")
        assertEquals(listOf(expiredToken), previews.toList())
    }

    private fun launchMain() = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity

    /** Live (not destroyed) MainActivity instances; read on the main thread as the monitor requires. */
    private fun mainActivities(): List<MainActivity> {
        var found = emptyList<MainActivity>()
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            found = Stage.values().filter { it != Stage.DESTROYED }
                .flatMap { monitor.getActivitiesInStage(it) }.filterIsInstance<MainActivity>().distinct()
        }
        return found
    }

    private fun login() = runBlocking {
        container.session.loginWithKakao("fake-id", "fake-nonce")
        assertTrue(container.session.state.value is SessionState.SignedIn)
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isVisibleToUser && node.text?.toString() == text) return node
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
    private fun awaitText(text: String) = await { hasText(text) }
    private fun click(text: String) = await {
        var node = find(instrumentation.uiAutomation.rootInActiveWindow, text)
        while (node != null && !node.isClickable) node = node.parent
        node?.isEnabled == true && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
    private fun editable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) editable(node.getChild(i))?.let { return it }
        return null
    }
    /** The join screen has a single editable field (the joined month); [label] documents which. */
    @Suppress("UNUSED_PARAMETER")
    private fun typeInto(label: String, value: String) = await {
        editable(instrumentation.uiAutomation.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }) == true
    }
}
