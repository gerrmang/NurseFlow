package com.nurseflow.app.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.*

/** A5/A6 contract. This interface uses the non-retrying authenticated client. */
interface StaffAdminApi {
    @GET("api/v1/wards/{w}/teams") suspend fun teams(@Path("w") ward: String): Items<AdminTeam>
    @GET("api/v1/wards/{w}/staff?includeInactive=true") suspend fun staff(@Path("w") ward: String): Items<AdminStaff>
    @GET("api/v1/wards/{w}/members") suspend fun members(@Path("w") ward: String): Items<AdminMember>
    @GET("api/v1/wards/{w}/career-policy") suspend fun policy(@Path("w") ward: String): AdminCareerPolicy
    @POST("api/v1/wards/{w}/teams") suspend fun createTeam(@Path("w") ward: String, @Body input: AdminTeamInput): AdminTeam
    @PUT("api/v1/wards/{w}/teams/{id}") suspend fun updateTeam(@Path("w") ward: String, @Path("id") id: String, @Body input: AdminTeamInput): AdminTeam
    @DELETE("api/v1/wards/{w}/teams/{id}") suspend fun deleteTeam(@Path("w") ward: String, @Path("id") id: String)
    @POST("api/v1/wards/{w}/staff") suspend fun createStaff(@Path("w") ward: String, @Body input: AdminStaffInput): AdminStaff
    @PUT("api/v1/wards/{w}/staff/{id}") suspend fun updateStaff(@Path("w") ward: String, @Path("id") id: String, @Body input: AdminStaffInput): AdminStaff
    @PUT("api/v1/wards/{w}/staff/{id}/team") suspend fun assignTeam(@Path("w") ward: String, @Path("id") id: String, @Body input: AdminTeamAssignment): AdminStaff
    @PUT("api/v1/wards/{w}/teams/{id}/order") suspend fun order(@Path("w") ward: String, @Path("id") id: String, @Body input: AdminTeamOrder): Items<AdminStaff>
    @PUT("api/v1/wards/{w}/staff/{id}/profile") suspend fun profile(@Path("w") ward: String, @Path("id") id: String, @Body input: AdminProfileInput): AdminStaff
    @POST("api/v1/wards/{w}/staff/{id}/profile/confirm") suspend fun confirmProfile(@Path("w") ward: String, @Path("id") id: String): AdminStaff
    @PUT("api/v1/wards/{w}/staff/{id}/roster-settings") suspend fun settings(@Path("w") ward: String, @Path("id") id: String, @Body input: AdminRosterSettings): AdminStaff
    @GET("api/v1/wards/{w}/staff/{id}/adjustments") suspend fun adjustments(@Path("w") ward: String, @Path("id") id: String): Items<AdminAdjustment>
    @PATCH("api/v1/wards/{w}/memberships/{id}") suspend fun membership(@Path("w") ward: String, @Path("id") id: String, @Body input: AdminMembershipInput): AdminMember
}

@Serializable data class AdminTeam(val teamId: String, val name: String, val sortOrder: Int)
@Serializable data class AdminTeamInput(val name: String, val sortOrder: Int)
@Serializable data class AdminStaffInput(val displayName: String, val qualifications: List<String> = emptyList(), val active: Boolean = true)
// Explicit nulls are required even though the shared Json omits optional null properties.
@Serializable data class AdminTeamAssignment(val teamId: String?)
@Serializable data class AdminTeamOrder(val nurseIds: List<String>)
@Serializable data class AdminProfileInput(val careerStartedOn: String, val rank: String)
@Serializable data class AdminRosterSettings(val seniorEligible: Boolean, val stageOverride: Int?)
@Serializable data class AdminMembershipInput(val admin: Boolean? = null, val active: Boolean? = null)
@Serializable data class AdminCareerPolicy(val stageThresholdMonths: List<Int>, val ranks: List<String>, val isDefault: Boolean)
@Serializable data class AdminCareerProfile(
    val status: String, val hospitalJoinedOn: String? = null, val careerStartedOn: String? = null,
    val rank: String? = null, val confirmedCareerStartedOn: String? = null, val confirmedRank: String? = null,
    val experienceMonths: Int? = null, val submittedStage: Int? = null, val scoringStage: Int? = null,
)
@Serializable data class AdminStaff(
    val nurseId: String, val displayName: String, val qualifications: List<String>, val active: Boolean,
    val linkedAccount: Boolean, val profile: AdminCareerProfile, val team: AdminTeam? = null,
    val teamOrder: Int? = null, val seniorEligible: Boolean, val stageOverride: Int? = null, val rosterStage: Int? = null,
)
@Serializable data class AdminMember(val userId: String, val displayName: String, val roles: List<String>, val active: Boolean, val nurseId: String? = null)
@Serializable data class AdminAdjustment(
    val adjustmentId: String, val kind: String, val actorUserId: String, val at: String,
    val before: String? = null, val after: String? = null, val actorName: String? = null,
)
