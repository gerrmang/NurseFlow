package com.nurseflow.app.feature.invite.admin

import com.nurseflow.app.core.network.*
import com.nurseflow.app.feature.invite.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.*
import org.junit.Test

class InvitationAdminViewModelTest {
    private val token = "Z".repeat(43) // fictional
    private val api = FakeInvitationApi()
    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private fun inv(id: String, role: String = InvitationRoles.MEMBER, nurseId: String? = null, status: String = "PENDING") =
        InvitationDto(id, "ward-1", role, nurseId, status, "2026-11-10T00:00:00Z", "2026-11-11T00:00:00Z")
    private val staff = listOf(
        StaffMemberDto("n-free", "가상 미가입", true, false),
        StaffMemberDto("n-pending", "가상 대기", true, false),
        StaffMemberDto("n-joined", "가상 가입", true, true),
    )
    private fun loaded(vararg rows: InvitationDto, linkBase: String = "nurseflow://invite"): InvitationAdminViewModel {
        val model = InvitationAdminViewModel(api, "ward-1", linkBase, scope())
        respond(*rows)
        return model
    }
    private fun respond(vararg rows: InvitationDto) {
        api.lists.last().complete(Items(rows.toList()))
        api.staffs.last().complete(StaffPageDto(staff))
    }

    @Test fun labelsTargetsWithoutServerNamesAndListsUnlinkedNurses() {
        val model = loaded(
            inv("aaaa-0001", nurseId = "n-pending"),
            inv("aaaa-0002", role = InvitationRoles.ADMIN, status = "EXPIRED"),
            inv("aaaa-0003"),
            inv("aaaa-0004", nurseId = "n-joined", status = "REDEEMED"),
            inv("aaaa-0005", nurseId = "gone", status = "REVOKED"),
            inv("aaaa-0006", status = "SOMETHING_NEW"),
        )
        val rows = model.state.value.rows
        assertEquals("가상 대기 · 미가입", rows[0].target)
        assertEquals("관리자 초대 · 대상 미지정", rows[1].target)
        assertEquals("새 간호사 초대 · 가입 시 기록 생성", rows[2].target)
        assertEquals("가상 가입 · 가입됨", rows[3].target)
        assertEquals("연결된 간호사 기록 · 현재 목록에 없음", rows[4].target)
        assertEquals(InvitationStatus.UNKNOWN, rows[5].status)
        assertEquals("#0001", rows[0].shortId)
        assertEquals(listOf(true, true, true, false, false, false), rows.map { it.status.manageable })
        assertEquals(listOf("가상 미가입" to null, "가상 대기" to InvitationStatus.PENDING),
            model.state.value.unlinked.map { it.displayName to it.latest })
        assertEquals(listOf("n-free"), model.state.value.invitableNurses.map { it.nurseId })
    }

    @Test fun issueShowsOneTimeLinkAndAdminNeverSendsNurse() {
        val model = loaded()
        model.create(InvitationRoles.ADMIN, "n-free")
        assertEquals(CreateInvitationBody(InvitationRoles.ADMIN), api.creates.single().first)
        model.create(InvitationRoles.MEMBER, "n-free") // ignored while busy
        assertEquals(1, api.creates.size)
        api.creates.single().second.complete(CreatedInvitationDto(inv("new", InvitationRoles.ADMIN), token, null))
        val link = model.state.value.link!!
        assertEquals("nurseflow://invite#token=$token", link.url)
        assertFalse(link.toString().contains(token))
        assertEquals(2, api.lists.size) // list refreshed after issue
        model.dismissLink()
        assertNull(model.state.value.link)

        respond()
        model.create(InvitationRoles.MEMBER, "n-free")
        assertEquals(CreateInvitationBody(InvitationRoles.MEMBER, "n-free"), api.creates.last().first)
        api.creates.last().second.complete(CreatedInvitationDto(inv("new2"), token, "https://server.example/invite#token=$token"))
        assertEquals("https://server.example/invite#token=$token", model.state.value.link!!.url)
    }

    @Test fun missingLinkBaseNeverInventsALink() {
        val model = loaded(linkBase = "")
        model.create(InvitationRoles.MEMBER, null)
        api.creates.single().second.complete(CreatedInvitationDto(inv("new"), token, null))
        assertNull(model.state.value.link)
        assertNotNull(model.state.value.notice)
    }

    @Test fun lostWriteResponsesReloadWithoutResending() {
        val model = loaded(inv("aaaa-0001"))
        model.reissue("aaaa-0001")
        api.reissues.single().second.completeExceptionally(network())
        assertEquals(1, api.reissues.size)
        assertTrue(model.state.value.notice!!.contains("재발급 결과를 확인하지 못했어요"))
        assertEquals(2, api.lists.size)
        respond(inv("aaaa-0009"), inv("aaaa-0001", status = "REVOKED"))
        model.revoke("aaaa-0009")
        api.revokes.single().second.completeExceptionally(apiError(502, null))
        assertEquals(1, api.revokes.size)
        assertTrue(model.state.value.notice!!.contains("철회 결과를 확인하지 못했어요"))
        respond()
        model.create(InvitationRoles.MEMBER, null)
        api.creates.single().second.completeExceptionally(network())
        assertEquals(1, api.creates.size)
        assertTrue(model.state.value.notice!!.contains("발급 결과를 확인하지 못했어요"))
    }

    @Test fun reissueOfRevokedOrRedeemedExplainsAndRefreshes() {
        val model = loaded(inv("aaaa-0001"))
        model.reissue("aaaa-0001")
        api.reissues.single().second.completeExceptionally(apiError(410, "INVITATION_REVOKED"))
        assertEquals("이미 철회된 초대예요. 목록을 새로 불러왔어요.", model.state.value.notice)
        respond()
        model.reissue("aaaa-0001")
        api.reissues.last().second.completeExceptionally(apiError(409, "INVITATION_ALREADY_REDEEMED"))
        assertEquals("이미 가입에 사용된 초대예요. 목록을 새로 불러왔어요.", model.state.value.notice)
        assertNull(model.state.value.link)
    }

    @Test fun serverDeniedAdminAccessClosesScreen() {
        val model = InvitationAdminViewModel(api, "ward-1", "nurseflow://invite", scope())
        api.lists.single().completeExceptionally(apiError(403, "WARD_ADMIN_REQUIRED"))
        assertTrue(model.state.value.forbidden)
        model.acknowledgeForbidden() // re-entry shows the error instead of closing again
        assertFalse(model.state.value.forbidden)
        assertEquals(InviteFailure.NOT_ADMIN, model.state.value.loadError)

        val other = loaded(inv("aaaa-0001"))
        other.revoke("aaaa-0001")
        api.revokes.single().second.completeExceptionally(apiError(404, "WARD_NOT_FOUND"))
        assertTrue(other.state.value.forbidden)
    }
}
