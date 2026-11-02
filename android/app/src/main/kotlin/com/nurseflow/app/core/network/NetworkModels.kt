package com.nurseflow.app.core.network

import kotlinx.serialization.Serializable

// Mirrors docs/openapi.yaml. Unknown fields are ignored so additive server changes don't break the app.

@Serializable
data class Items<T>(val items: List<T>)

@Serializable
data class ApiProblemDto(val status: Int? = null, val code: String? = null, val detail: String? = null)

// ── Auth ──

@Serializable
data class KakaoLoginBody(val idToken: String, val nonce: String? = null)

@Serializable
data class RefreshTokenBody(val refreshToken: String)

@Serializable
data class SessionDto(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String? = null,
    val refreshTokenExpiresAt: String,
    val user: SessionUserDto,
)

@Serializable
data class SessionUserDto(val userId: String, val displayName: String, val newUser: Boolean = false)

@Serializable
data class CurrentUserDto(val userId: String, val displayName: String, val memberships: List<MembershipDto>)

@Serializable
data class MembershipDto(
    val wardId: String,
    val wardName: String,
    val roles: List<String>,
    val canViewWardCalendar: Boolean,
) {
    val isAdmin: Boolean get() = "WARD_ADMIN" in roles
}

// ── Invitations ──

@Serializable
data class InvitationTokenBody(val token: String)

@Serializable
data class RedeemInvitationBody(val token: String, val careerStartedOn: String? = null, val rank: String? = null)

@Serializable
data class InvitationPreviewDto(
    val wardId: String,
    val wardName: String,
    val role: String,
    val expiresAt: String,
    val linksExistingNurse: Boolean,
    val ranks: List<String> = emptyList(),
)

@Serializable
data class CreateInvitationBody(val role: String, val nurseId: String? = null, val expiresInHours: Int = 72)

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

@Serializable
data class CreatedInvitationDto(val invitation: InvitationDto, val token: String, val inviteUrl: String? = null)

// ── Staff / career profile ──

@Serializable
data class TeamDto(val teamId: String, val name: String, val sortOrder: Int)

@Serializable
data class CareerPolicyDto(val stageThresholdMonths: List<Int>, val ranks: List<String>, val isDefault: Boolean)

@Serializable
data class CareerProfileDto(
    val status: String,
    val careerStartedOn: String? = null,
    val rank: String? = null,
    val submittedAt: String? = null,
    val confirmedCareerStartedOn: String? = null,
    val confirmedRank: String? = null,
    val confirmedAt: String? = null,
    val experienceMonths: Int? = null,
    val submittedStage: Int? = null,
    val scoringStage: Int? = null,
)

@Serializable
data class StaffMemberDto(
    val nurseId: String,
    val displayName: String,
    val qualifications: List<String> = emptyList(),
    val active: Boolean,
    val linkedAccount: Boolean = false,
    val team: TeamDto? = null,
    val profile: CareerProfileDto,
)

@Serializable
data class CareerProfileBody(val careerStartedOn: String, val rank: String)
