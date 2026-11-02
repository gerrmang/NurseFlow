package com.nurseflow.app.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Public session endpoints. Uses an HTTP client without the 401 refresh authenticator. */
interface AuthApi {
    @POST("api/v1/auth/kakao/session")
    suspend fun kakaoLogin(@Body body: KakaoLoginBody): SessionDto

    @POST("api/v1/auth/session/refresh")
    suspend fun refresh(@Body body: RefreshTokenBody): SessionDto

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body body: RefreshTokenBody)
}

/** Endpoints that need the NurseFlow access token. */
interface NurseFlowApi {
    @GET("api/v1/me")
    suspend fun me(): CurrentUserDto

    @POST("api/v1/invitations/preview")
    suspend fun previewInvitation(@Body body: InvitationTokenBody): InvitationPreviewDto

    @POST("api/v1/invitations/redeem")
    suspend fun redeemInvitation(@Body body: RedeemInvitationBody): MembershipDto

    @POST("api/v1/wards/{wardId}/invitations")
    suspend fun createInvitation(@Path("wardId") wardId: String, @Body body: CreateInvitationBody): CreatedInvitationDto

    @GET("api/v1/wards/{wardId}/invitations")
    suspend fun invitations(@Path("wardId") wardId: String): Items<InvitationDto>

    @DELETE("api/v1/wards/{wardId}/invitations/{invitationId}")
    suspend fun revokeInvitation(@Path("wardId") wardId: String, @Path("invitationId") invitationId: String)

    @GET("api/v1/wards/{wardId}/career-policy")
    suspend fun careerPolicy(@Path("wardId") wardId: String): CareerPolicyDto

    @GET("api/v1/wards/{wardId}/my-profile")
    suspend fun myProfile(@Path("wardId") wardId: String): StaffMemberDto

    @PUT("api/v1/wards/{wardId}/my-profile")
    suspend fun submitMyProfile(@Path("wardId") wardId: String, @Body body: CareerProfileBody): StaffMemberDto
}
