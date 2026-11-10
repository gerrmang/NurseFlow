package com.nurseflow.app.feature.invite

import com.nurseflow.app.core.network.*
import kotlinx.coroutines.CompletableDeferred

/** Calls suspend until the test completes them, so ordering and late responses are explicit. */
class FakeInvitationApi : InvitationApi {
    val previews = mutableListOf<Pair<String, CompletableDeferred<InvitationPreviewDto>>>()
    val redeems = mutableListOf<Pair<RedeemInvitationBody, CompletableDeferred<MembershipDto>>>()
    val creates = mutableListOf<Pair<CreateInvitationBody, CompletableDeferred<CreatedInvitationDto>>>()
    val reissues = mutableListOf<Pair<String, CompletableDeferred<CreatedInvitationDto>>>()
    val revokes = mutableListOf<Pair<String, CompletableDeferred<Unit>>>()
    val lists = mutableListOf<CompletableDeferred<Items<InvitationDto>>>()
    val staffs = mutableListOf<CompletableDeferred<StaffPageDto>>()

    override suspend fun create(wardId: String, body: CreateInvitationBody) =
        CompletableDeferred<CreatedInvitationDto>().also { creates += body to it }.await()
    override suspend fun list(wardId: String) = CompletableDeferred<Items<InvitationDto>>().also { lists += it }.await()
    override suspend fun reissue(wardId: String, invitationId: String) =
        CompletableDeferred<CreatedInvitationDto>().also { reissues += invitationId to it }.await()
    override suspend fun revoke(wardId: String, invitationId: String) =
        CompletableDeferred<Unit>().also { revokes += invitationId to it }.await()
    override suspend fun preview(body: InvitationTokenBody) =
        CompletableDeferred<InvitationPreviewDto>().also { previews += body.token to it }.await()
    override suspend fun redeem(body: RedeemInvitationBody) =
        CompletableDeferred<MembershipDto>().also { redeems += body to it }.await()
    override suspend fun staff(wardId: String, includeInactive: Boolean) =
        CompletableDeferred<StaffPageDto>().also { staffs += it }.await()
}

class FakeWardApi : NurseFlowApi {
    val wardCalls = mutableListOf<CompletableDeferred<Items<MembershipDto>>>()
    override suspend fun me() = error("unused")
    override suspend fun wards() = CompletableDeferred<Items<MembershipDto>>().also { wardCalls += it }.await()
    override suspend fun publishedWard(wardId: String, month: String) = error("unused")
    override suspend fun publishedMine(wardId: String, month: String) = error("unused")
}

fun membership(wardId: String, role: String = InvitationRoles.MEMBER) = MembershipDto(wardId, "가상 병동", listOf(role), true)
fun preview(wardId: String = "ward-1", role: String = InvitationRoles.MEMBER, links: Boolean = false) =
    InvitationPreviewDto(wardId, "가상 병동", role, "2026-11-11T00:00:00Z", links)
fun apiError(status: Int, code: String?) = ApiException(status, code, "고정 메시지")
fun network() = ApiException(0, "NETWORK", "고정 메시지")
