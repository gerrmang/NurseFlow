package com.nurseflow.app.feature.invite

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nurseflow.app.AppContainer
import com.nurseflow.app.MainActivity
import com.nurseflow.app.core.network.CreateInvitationBody
import com.nurseflow.app.core.network.InvitationRoles
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

/**
 * Real Spring invitation services, production HTTP and actual Compose UI; only the external
 * OIDC provider is fictional (FakeOidcProvider). Requires AndroidInvitationE2eServerTest.
 * Tokens read from the screen are never printed in assertion messages.
 */
@RunWith(AndroidJUnit4::class)
class InvitationJoinDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val http = OkHttpClient()
    private val backend = "http://127.0.0.1:18085/"
    private val controls = "http://127.0.0.1:18086/"
    private val ward = "가상 초대 병동"

    @Test fun adminIssuesAndNurseAndAdminJoinWithErrorStates() = runBlocking {
        assumeTrue("Opt in with -e inviteE2e true and start AndroidInvitationE2eServerTest",
            InstrumentationRegistry.getArguments().getString("inviteE2e") == "true")
        val app = instrumentation.targetContext.applicationContext as IsolatedSessionTestApplication
        val original = app.container
        lateinit var container: AppContainer
        instrumentation.runOnMainSync {
            container = AppContainer(instrumentation.targetContext, backend, TestTokenStore())
            app.useContainer(container)
        }
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var passed = false
        try {
            await { container.session.state.value == SessionState.SignedOut }

            // ── Admin: issue a member invitation linked to the unregistered nurse record, then reissue it ──
            login(container, "admin")
            click("초대 관리")
            waitText("$ward 초대 관리")
            waitText("가상 미가입 간호사 · 초대 없음")
            click("새 초대 발급")
            waitText("연결할 간호사 기록 (선택)")
            click("가상 미가입 간호사")
            click("발급")
            val revokedByReissue = takeLink()
            waitText("가상 미가입 간호사 · 미가입")
            waitText("가상 미가입 간호사 · 사용 대기")
            click("재발급")
            waitText("초대를 재발급할까요?")
            click("재발급")
            val memberLink = takeLink()
            assertNotEquals(revokedByReissue, memberLink)
            waitText("철회됨 · 간호사", substring = true) // the reissued source keeps its role/target but is revoked
            logout(container)

            // ── Nurse: revoked link, then join with the reissued link ──
            login(container, "nurse")
            waitText("아직 소속된 병동이 없어요.")
            open(revokedByReissue)
            waitText(InviteFailure.REVOKED.message)
            click("닫기")
            open(memberLink)
            waitText("관리자가 등록한 간호사 기록과 연결됩니다.")
            waitText("초대 역할: 간호사")
            typeMonth("2020-03")
            click("가입하기")
            waitText("아직 확정된 근무표가 없습니다")
            click("‹ 병동 목록")
            waitText(ward)
            assertEquals(listOf(InvitationRoles.MEMBER), container.api.wards().items.single().roles)
            open(memberLink) // single use
            waitText(InviteFailure.ALREADY_REDEEMED.message)
            click("닫기")
            logout(container)

            // ── Admin: list reflects the join; issue an admin invitation; revoke and expire others ──
            login(container, "admin")
            click("초대 관리")
            waitText("가상 미가입 간호사 · 가입됨")
            waitText("가입 완료 · 간호사", substring = true)
            assertFalse(hasText("미가입 간호사"))
            click("새 초대 발급")
            waitText("연결할 간호사 기록 (선택)")
            click("관리자")
            click("발급")
            val adminLink = takeLink()
            val wardId = container.api.wards().items.single().wardId
            val toExpire = container.invitationApi.create(wardId, CreateInvitationBody(InvitationRoles.MEMBER))
            val toRevoke = container.invitationApi.create(wardId, CreateInvitationBody(InvitationRoles.MEMBER))
            expire(toExpire.invitation.invitationId)
            click("새로고침")
            waitText("만료됨 · 간호사 #${toExpire.invitation.invitationId.takeLast(4)}")
            waitText("새 간호사 초대 · 가입 시 기록 생성")
            click("철회") // the list is newest first: the top row is toRevoke
            waitText("초대를 철회할까요?")
            click("철회")
            waitText("철회됨 · 간호사 #${toRevoke.invitation.invitationId.takeLast(4)}")
            logout(container)

            // ── Nurse: already a member, expired link ──
            login(container, "nurse")
            waitText(ward)
            open(adminLink)
            waitText("관리자 초대는 입사 연월 입력 없이 가입합니다.")
            click("가입하기")
            waitText(InviteFailure.ALREADY_MEMBER.message)
            click("닫기")
            open(link(toRevoke.token))
            waitText(InviteFailure.REVOKED.message)
            click("닫기")
            logout(container)

            // ── Signed out: the link waits for login, then the second admin joins without a month ──
            open(adminLink)
            waitText("병동 초대를 받았어요. 초대 내용을 확인하려면 먼저 로그인해 주세요.")
            assertFalse(hasText(ward))
            login(container, "admin2")
            waitText("초대 역할: 관리자")
            click("가입하기")
            waitText("본인 근무표에 연결된 간호사 정보가 없습니다.")
            click("‹ 병동 목록")
            waitText("초대 관리")
            assertEquals(listOf(InvitationRoles.ADMIN), container.api.wards().items.single().roles)
            logout(container)

            // ── Another account: used and expired links ──
            login(container, "other")
            open(memberLink)
            waitText(InviteFailure.ALREADY_REDEEMED.message)
            click("닫기")
            open(link(toExpire.token))
            waitText(InviteFailure.EXPIRED.message)
            click("닫기")
            assertTrue(container.api.wards().items.isEmpty())
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

    private fun link(token: String) = "nurseflow://invite#token=$token"

    private fun open(link: String) {
        instrumentation.targetContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(instrumentation.targetContext.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private suspend fun login(container: AppContainer, account: String) {
        val identity = http.newCall(Request.Builder().url(controls + "identity?account=$account").build()).execute().use {
            check(it.code == 200) { "Fixture identity HTTP ${it.code}" }
            JSONObject(it.body!!.string())
        }
        container.session.loginWithKakao(identity.getString("idToken"), identity.getString("nonce"))
    }

    private fun logout(container: AppContainer) {
        instrumentation.runOnMainSync { container.logout() }
        waitText("카카오 로그인")
    }

    private fun expire(invitationId: String) {
        http.newCall(Request.Builder().url(controls + "expire?invitationId=$invitationId").post(ByteArray(0).toRequestBody()).build())
            .execute().use { assertEquals("Fixture expiry", 200, it.code) }
    }

    /** Reads the one-time link from the dialog, then closes it. */
    private fun takeLink(): String {
        var found: String? = null
        await {
            found = findPrefix(instrumentation.uiAutomation.rootInActiveWindow, "nurseflow://invite#token=")
            found != null
        }
        assertNotNull("Issued link must be a parseable development link", parseInviteLink(found!!))
        click("닫기")
        return found!!
    }

    private fun typeMonth(value: String) = await {
        editable(instrumentation.uiAutomation.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }) == true
    }

    private fun editable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) editable(node.getChild(i))?.let { return it }
        return null
    }

    private fun findPrefix(node: AccessibilityNodeInfo?, prefix: String): String? {
        if (node == null) return null
        node.text?.toString()?.takeIf { it.startsWith(prefix) }?.let { return it }
        for (i in 0 until node.childCount) findPrefix(node.getChild(i), prefix)?.let { return it }
        return null
    }

    private fun find(node: AccessibilityNodeInfo?, text: String, substring: Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        val value = node.text?.toString()
        if (node.isVisibleToUser && (if (substring) value?.contains(text) == true else value == text)) return node
        for (i in 0 until node.childCount) find(node.getChild(i), text, substring)?.let { return it }
        return null
    }

    private fun hasText(text: String, substring: Boolean = false) = find(instrumentation.uiAutomation.rootInActiveWindow, text, substring) != null
    private fun await(check: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < end) {
            if (check()) return
            SystemClock.sleep(100)
        }
        fail("Expected device state was not reached")
    }
    private fun waitText(text: String, substring: Boolean = false) = await { hasText(text, substring) }
    private fun click(text: String) = await {
        var node = find(instrumentation.uiAutomation.rootInActiveWindow, text, false)
        while (node != null && !node.isClickable) node = node.parent
        node?.isEnabled == true && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}
