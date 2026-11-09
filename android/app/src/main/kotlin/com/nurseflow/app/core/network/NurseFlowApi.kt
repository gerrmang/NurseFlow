package com.nurseflow.app.core.network
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("api/v1/auth/kakao/session")
    suspend fun kakaoLogin(@Body body: KakaoLoginBody): SessionDto
    @POST("api/v1/auth/session/refresh")
    suspend fun refresh(@Body body: RefreshTokenBody): SessionDto
    @POST("api/v1/auth/logout")
    suspend fun logout(@Body body: RefreshTokenBody)
}
interface NurseFlowApi {
    @GET("api/v1/me")
    suspend fun me(): CurrentUserDto
    @GET("api/v1/wards")
    suspend fun wards(): Items<MembershipDto>
}
