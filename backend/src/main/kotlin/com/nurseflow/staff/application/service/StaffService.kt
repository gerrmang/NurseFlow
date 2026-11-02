package com.nurseflow.staff.application.service

import com.nurseflow.common.audit.AuditLog
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.staff.application.model.CareerPolicyResult
import com.nurseflow.staff.application.model.CareerProfileResult
import com.nurseflow.staff.application.model.ProfileInput
import com.nurseflow.staff.application.model.StaffInputCommand
import com.nurseflow.staff.application.model.StaffResult
import com.nurseflow.staff.application.model.TeamResult
import com.nurseflow.staff.domain.CareerPolicy
import com.nurseflow.staff.domain.ProfileStatus
import com.nurseflow.staff.domain.experienceMonths
import com.nurseflow.staff.persistence.entity.DbStaff
import com.nurseflow.staff.persistence.entity.DbTeam
import com.nurseflow.staff.persistence.repository.StaffJdbcRepository
import com.nurseflow.ward.application.service.WardService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/**
 * Teams, the ward career policy and nurse career profiles. A nurse submits a career start
 * month and rank; only values an admin has confirmed feed the career stage used for scoring.
 */
@Service
class StaffService(
    private val repo: StaffJdbcRepository,
    private val wards: WardService,
    private val audit: AuditLog,
) {
    // ── Teams ──

    @Transactional(readOnly = true)
    fun teams(wardId: UUID, actor: String): List<TeamResult> {
        wards.requireMember(wardId, actor)
        return repo.teams(wardId).map { it.toResult() }
    }

    @Transactional
    fun createTeam(wardId: UUID, name: String, actor: String): TeamResult {
        wards.requireAdmin(wardId, actor)
        val clean = name.trim()
        requireUniqueTeamName(wardId, clean, except = null)
        val team = DbTeam(UUID.randomUUID(), wardId, clean, repo.nextTeamOrder(wardId), true)
        repo.insertTeam(team)
        audit.record(wardId, actor, "TEAM_CREATED", team.id.toString())
        return team.toResult()
    }

    @Transactional
    fun updateTeam(wardId: UUID, teamId: UUID, name: String, sortOrder: Int?, actor: String): TeamResult {
        wards.requireAdmin(wardId, actor)
        val team = repo.findTeam(wardId, teamId) ?: throw BusinessException(ErrorCode.TEAM_NOT_FOUND)
        val clean = name.trim()
        requireUniqueTeamName(wardId, clean, except = teamId)
        val order = sortOrder ?: team.sortOrder
        repo.updateTeam(teamId, clean, order)
        audit.record(wardId, actor, "TEAM_UPDATED", teamId.toString())
        return team.copy(name = clean, sortOrder = order).toResult()
    }

    @Transactional
    fun deleteTeam(wardId: UUID, teamId: UUID, actor: String) {
        wards.requireAdmin(wardId, actor)
        repo.findTeam(wardId, teamId) ?: throw BusinessException(ErrorCode.TEAM_NOT_FOUND)
        if (repo.activeNursesInTeam(teamId) > 0) throw BusinessException(ErrorCode.TEAM_NOT_EMPTY)
        repo.deactivateTeam(teamId)
        audit.record(wardId, actor, "TEAM_DELETED", teamId.toString())
    }

    // ── Career policy ──

    @Transactional(readOnly = true)
    fun careerPolicy(wardId: UUID, actor: String): CareerPolicyResult {
        wards.requireMember(wardId, actor)
        return policyResult(wardId)
    }

    /** Rank names for an invitee who is not a member yet (shown on the join screen). */
    @Transactional(readOnly = true)
    fun ranksForInvitee(wardId: UUID): List<String> = policyResult(wardId).ranks

    /** Takes effect immediately: stages are computed from confirmed inputs and the current policy. */
    @Transactional
    fun updateCareerPolicy(wardId: UUID, thresholds: List<Int>, ranks: List<String>, actor: String): CareerPolicyResult {
        wards.requireAdmin(wardId, actor)
        val cleanRanks = ranks.map { it.trim() }
        if (!CareerPolicy.isValid(thresholds, cleanRanks)) throw BusinessException(ErrorCode.INVALID_CAREER_POLICY)
        repo.saveCareerPolicy(wardId, thresholds, cleanRanks, actor, Instant.now())
        audit.record(wardId, actor, "CAREER_POLICY_UPDATED")
        return CareerPolicyResult(thresholds, cleanRanks, isDefault = false)
    }

    // ── Staff (admin) ──

    /** Team order first, then most experienced first (earliest career start), then name. */
    @Transactional(readOnly = true)
    fun staff(wardId: UUID, actor: String, teamId: UUID?, includeInactive: Boolean): List<StaffResult> {
        wards.requireAdmin(wardId, actor)
        val context = context(wardId)
        val order = context.teams.values.associate { it.id to it.sortOrder }
        return repo.staff(wardId)
            .filter { includeInactive || it.active }
            .filter { teamId == null || it.teamId == teamId }
            .sortedWith(
                compareBy<DbStaff>({ it.teamId?.let(order::get) ?: Int.MAX_VALUE })
                    .thenBy(nullsLast()) { it.confirmedCareerStartedOn ?: it.careerStartedOn }
                    .thenBy { it.displayName },
            )
            .map { it.toResult(context) }
    }

    @Transactional
    fun createStaff(wardId: UUID, command: StaffInputCommand, actor: String): StaffResult {
        wards.requireAdmin(wardId, actor)
        command.teamId?.let { repo.findTeam(wardId, it) ?: throw BusinessException(ErrorCode.TEAM_NOT_FOUND) }
        val id = UUID.randomUUID()
        repo.insertStaff(id, wardId, command.displayName.trim(), command.qualifications.distinct(), command.active, command.teamId)
        audit.record(wardId, actor, "STAFF_CREATED", id.toString())
        return requireStaff(wardId, id).toResult(context(wardId))
    }

    @Transactional
    fun updateStaff(wardId: UUID, nurseId: UUID, command: StaffInputCommand, actor: String): StaffResult {
        wards.requireAdmin(wardId, actor)
        val current = requireStaff(wardId, nurseId)
        repo.updateStaff(nurseId, command.displayName.trim(), command.qualifications.distinct(), command.active)
        if (current.active != command.active) {
            audit.record(wardId, actor, if (command.active) "STAFF_ACTIVATED" else "STAFF_DEACTIVATED", nurseId.toString())
        }
        return requireStaff(wardId, nurseId).toResult(context(wardId))
    }

    /** Only admins assign teams; nurses cannot move themselves. `null` removes the team. */
    @Transactional
    fun assignTeam(wardId: UUID, nurseId: UUID, teamId: UUID?, actor: String): StaffResult {
        wards.requireAdmin(wardId, actor)
        requireStaff(wardId, nurseId)
        teamId?.let { repo.findTeam(wardId, it) ?: throw BusinessException(ErrorCode.TEAM_NOT_FOUND) }
        repo.assignTeam(nurseId, teamId)
        audit.record(wardId, actor, "STAFF_TEAM_ASSIGNED", nurseId.toString())
        return requireStaff(wardId, nurseId).toResult(context(wardId))
    }

    /** Confirms what the nurse submitted. Already-confirmed values are a no-op. */
    @Transactional
    fun confirmProfile(wardId: UUID, nurseId: UUID, actor: String): StaffResult {
        wards.requireAdmin(wardId, actor)
        val nurse = requireStaff(wardId, nurseId)
        val start = nurse.careerStartedOn ?: throw BusinessException(ErrorCode.PROFILE_NOT_SUBMITTED)
        val rank = nurse.rank ?: throw BusinessException(ErrorCode.PROFILE_NOT_SUBMITTED)
        if (nurse.status() != ProfileStatus.CONFIRMED) {
            repo.confirmProfile(nurseId, start, rank, actor, Instant.now())
            audit.record(wardId, actor, "PROFILE_CONFIRMED", nurseId.toString())
        }
        return requireStaff(wardId, nurseId).toResult(context(wardId))
    }

    /** Admin correction: the admin's own values are confirmed immediately. */
    @Transactional
    fun setProfileAsAdmin(wardId: UUID, nurseId: UUID, input: ProfileInput, actor: String): StaffResult {
        wards.requireAdmin(wardId, actor)
        requireStaff(wardId, nurseId)
        val context = context(wardId)
        validate(input, context)
        repo.confirmProfile(nurseId, input.careerStartedOn.toString(), input.rank, actor, Instant.now())
        audit.record(wardId, actor, "PROFILE_SET_BY_ADMIN", nurseId.toString())
        return requireStaff(wardId, nurseId).toResult(context)
    }

    // ── My profile (nurse) ──

    @Transactional(readOnly = true)
    fun myProfile(wardId: UUID, actor: String): StaffResult {
        wards.requireMember(wardId, actor)
        val nurse = repo.findStaffBySubject(wardId, actor) ?: throw BusinessException(ErrorCode.NURSE_IDENTITY_REQUIRED)
        return nurse.toResult(context(wardId))
    }

    @Transactional
    fun submitMyProfile(wardId: UUID, input: ProfileInput, actor: String): StaffResult {
        wards.requireMember(wardId, actor)
        val nurse = repo.findStaffBySubject(wardId, actor) ?: throw BusinessException(ErrorCode.NURSE_IDENTITY_REQUIRED)
        submit(nurse, input)
        return requireStaff(wardId, nurse.id).toResult(context(wardId))
    }

    /** Used by invitation redemption inside its transaction, after the nurse link exists. */
    fun submitForLinkedNurse(wardId: UUID, subject: String, input: ProfileInput) {
        val nurse = repo.findStaffBySubject(wardId, subject) ?: throw BusinessException(ErrorCode.NURSE_IDENTITY_REQUIRED)
        submit(nurse, input)
    }

    private fun submit(nurse: DbStaff, input: ProfileInput) {
        validate(input, context(nurse.wardId))
        val unchanged = nurse.careerStartedOn == input.careerStartedOn.toString() && nurse.rank == input.rank
        if (!unchanged) repo.submitProfile(nurse.id, input.careerStartedOn.toString(), input.rank, Instant.now())
    }

    // ── Helpers ──

    private class Context(val policy: CareerPolicy, val zone: ZoneId, val teams: Map<UUID, DbTeam>) {
        val today: YearMonth get() = YearMonth.now(zone)
    }

    private fun context(wardId: UUID): Context {
        val stored = repo.careerPolicy(wardId)
        val policy = stored?.let { CareerPolicy(it.thresholdMonths, it.ranks) } ?: CareerPolicy.DEFAULT
        val zone = repo.wardZone(wardId)?.let(ZoneId::of) ?: throw BusinessException(ErrorCode.WARD_NOT_FOUND)
        return Context(policy, zone, repo.teams(wardId, includeInactive = true).associateBy { it.id })
    }

    private fun policyResult(wardId: UUID): CareerPolicyResult {
        val stored = repo.careerPolicy(wardId)
        val policy = stored?.let { CareerPolicy(it.thresholdMonths, it.ranks) } ?: CareerPolicy.DEFAULT
        return CareerPolicyResult(policy.stageThresholdMonths, policy.ranks, isDefault = stored == null)
    }

    private fun validate(input: ProfileInput, context: Context) {
        if (input.careerStartedOn.isAfter(context.today) || input.careerStartedOn.year < 1950) {
            throw BusinessException(ErrorCode.INVALID_CAREER_START)
        }
        if (input.rank !in context.policy.ranks) throw BusinessException(ErrorCode.RANK_NOT_IN_POLICY)
    }

    private fun requireStaff(wardId: UUID, nurseId: UUID): DbStaff =
        repo.findStaff(wardId, nurseId) ?: throw BusinessException(ErrorCode.NURSE_NOT_FOUND)

    private fun requireUniqueTeamName(wardId: UUID, name: String, except: UUID?) {
        if (repo.teams(wardId).any { it.name == name && it.id != except }) throw BusinessException(ErrorCode.TEAM_NAME_DUPLICATE)
    }

    private fun DbStaff.status(): ProfileStatus = when {
        careerStartedOn == null || rank == null -> ProfileStatus.MISSING
        confirmedAt != null && confirmedCareerStartedOn == careerStartedOn && confirmedRank == rank -> ProfileStatus.CONFIRMED
        else -> ProfileStatus.PENDING
    }

    private fun DbStaff.toResult(context: Context): StaffResult {
        val submittedStart = careerStartedOn?.let(YearMonth::parse)
        val confirmedStart = confirmedCareerStartedOn?.let(YearMonth::parse)
        val submittedMonths = submittedStart?.let { experienceMonths(it, context.today) }
        val confirmedMonths = confirmedStart?.let { experienceMonths(it, context.today) }
        val team = teamId?.let(context.teams::get)?.takeIf { it.active }
        return StaffResult(
            nurseId = id,
            displayName = displayName,
            qualifications = qualifications,
            active = active,
            linkedAccount = subject != null,
            team = team?.toResult(),
            profile = CareerProfileResult(
                status = status(),
                careerStartedOn = submittedStart,
                rank = rank,
                submittedAt = submittedAt,
                confirmedCareerStartedOn = confirmedStart,
                confirmedRank = confirmedRank,
                confirmedAt = confirmedAt,
                experienceMonths = submittedMonths,
                submittedStage = submittedMonths?.let(context.policy::stageFor),
                scoringStage = confirmedMonths?.takeIf { status() == ProfileStatus.CONFIRMED }?.let(context.policy::stageFor),
            ),
        )
    }

    private fun DbTeam.toResult() = TeamResult(id, name, sortOrder)
}
