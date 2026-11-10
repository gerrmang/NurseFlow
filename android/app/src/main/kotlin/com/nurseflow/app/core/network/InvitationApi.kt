package com.nurseflow.app.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * A1 invitation contract (docs/openapi.yaml, InvitationService on dev). Write calls go through
 * a client that never retries automatically: a lost response must be resolved by the screen,
 * not replayed. Tokens travel only in request bodies.
 */
interface InvitationApi {
    @POST("api/v1/wards/{wardId}/invitations")
    suspend fun create(@Path("wardId") wardId: String, @Body body: CreateInvitationBody): CreatedInvitationDto

    @GET("api/v1/wards/{wardId}/invitations")
    suspend fun list(@Path("wardId") wardId: String): Items<InvitationDto>

    @POST("api/v1/wards/{wardId}/invitations/{invitationId}/reissue")
    suspend fun reissue(@Path("wardId") wardId: String, @Path("invitationId") invitationId: String): CreatedInvitationDto

    @DELETE("api/v1/wards/{wardId}/invitations/{invitationId}")
    suspend fun revoke(@Path("wardId") wardId: String, @Path("invitationId") invitationId: String)

    @POST("api/v1/invitations/preview")
    suspend fun preview(@Body body: InvitationTokenBody): InvitationPreviewDto

    @POST("api/v1/invitations/redeem")
    suspend fun redeem(@Body body: RedeemInvitationBody): MembershipDto

    /** Admin-only staff list, used to label invitation targets and pick an unlinked nurse record. */
    @GET("api/v1/wards/{wardId}/staff")
    suspend fun staff(@Path("wardId") wardId: String, @Query("includeInactive") includeInactive: Boolean = false): StaffPageDto
}

object InvitationRoles {
    const val ADMIN = "WARD_ADMIN"
    const val MEMBER = "WARD_MEMBER"
}

/** `nurseId` is omitted when null (explicitNulls = false) and must never be set for WARD_ADMIN. */
@Serializable
data class CreateInvitationBody(val role: String, val nurseId: String? = null)

@Serializable
class InvitationTokenBody(val token: String) {
    override fun toString() = "InvitationTokenBody(***)"
}

/** Only `token` and, for WARD_MEMBER, `hospitalJoinedOn` (YYYY-MM). No rank, career or role fields. */
@Serializable
class RedeemInvitationBody(val token: String, val hospitalJoinedOn: String? = null) {
    override fun toString() = "RedeemInvitationBody(***, hospitalJoinedOn=$hospitalJoinedOn)"
}

@Serializable
data class InvitationDto(
    val invitationId: String,
    val wardId: String,
    val role: String,
    val nurseId: String? = null,
    val status: String,
    val createdAt: String,
    val expiresAt: String,
    val redeemedAt: String? = null,
    val revokedAt: String? = null,
)

/** Returned once at issue or reissue; the list never contains the token again. */
@Serializable
class CreatedInvitationDto(val invitation: InvitationDto, val token: String, val inviteUrl: String? = null) {
    override fun toString() = "CreatedInvitationDto(invitation=$invitation, token=***)"
}

@Serializable
data class InvitationPreviewDto(
    val wardId: String,
    val wardName: String,
    val role: String,
    val expiresAt: String,
    val linksExistingNurse: Boolean,
)

@Serializable
data class StaffPageDto(val items: List<StaffMemberDto>)

@Serializable
data class StaffMemberDto(
    val nurseId: String,
    val displayName: String,
    val active: Boolean = true,
    val linkedAccount: Boolean,
)
