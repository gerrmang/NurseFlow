package com.nurseflow.staff.application.service

import com.nurseflow.common.audit.AuditLog
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.staff.application.model.CareerPolicyResult
import com.nurseflow.staff.application.model.CareerProfileResult
import com.nurseflow.staff.application.model.ProfileInput
import com.nurseflow.staff.application.model.RosterSettingsInput
import com.nurseflow.staff.application.model.StaffAdjustmentResult
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
import com.nurseflow.ward.persistence.repository.WardJdbcRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/**
 * Teams, the ward career policy and nurse career profiles. Nurses submit their career start
 * month; only values an admin has confirmed feed the career stage used for scoring.
 */
@Service
class StaffService(
    private val repo: StaffJdbcRepository,
    private val wards: WardService,
    private val wardRepo: WardJdbcRepository,
    private val audit: AuditLog,
) {
    // ── Teams ──

    @Transactional(readOnly = true)
    fun teams(wardId: UUID, actor: String): List<TeamResult> {
        wards.requireMember(wardId, actor)
        return repo.teams(wardId).map { it.toResult() }
    }

    @Transactional
    fun createTeam(wardId: UUID, name: String, sortOrder: Int?, actor: String): TeamResult {
        wards.requireAdmin(wardId, actor)
        val clean = name.trim()
        requireUniqueTeamName(wardId, clean, except = null)
        val team = DbTeam(UUID.randomUUID(), wardId, clean, sortOrder ?: repo.nextTeamOrder(wardId), true)
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
        // Same lock as moving a nurse into a team, so no one can join a team while it is being deleted.
        lockWard(wardId)
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

    /**
     * Team order first, then the admin's order inside the team (relative seniority, decision 12), then for
     * members not yet ordered the most experienced first (earliest career start), then name.
     */
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
                    .thenBy(nullsLast()) { it.teamOrder }
                    .thenBy(nullsLast()) { it.confirmedCareerStartedOn ?: it.careerStartedOn }
                    .thenBy { it.displayName },
            )
            .map { it.toResult(context) }
    }

    @Transactional
    fun createStaff(wardId: UUID, command: StaffInputCommand, actor: String): StaffResult {
        wards.requireAdmin(wardId, actor)
        // Team membership changes take the ward lock that team ordering holds, so a reorder never
        // validates against a member set that changes before it is saved.
        lockWard(wardId)
        command.teamId?.let { repo.findTeam(wardId, it) ?: throw BusinessException(ErrorCode.TEAM_NOT_FOUND) }
        val id = UUID.randomUUID()
        repo.insertStaff(id, wardId, command.displayName.trim(), command.qualifications.distinct(), command.active, command.teamId)
        audit.record(wardId, actor, "STAFF_CREATED", id.toString())
        return requireStaff(wardId, id).toResult(context(wardId))
    }

    @Transactional
    fun updateStaff(wardId: UUID, nurseId: UUID, command: StaffInputCommand, actor: String): StaffResult {
        wards.requireAdmin(wardId, actor)
        // Always under the ward lock and read after it: deciding whether to lock from an unlocked read let a
        // concurrent activation + reorder slip between the read and this write (duplicate team positions).
        lockWard(wardId)
        val current = requireStaff(wardId, nurseId)
        repo.updateStaff(nurseId, command.displayName.trim(), command.qualifications.distinct(), command.active)
        if (current.active && !command.active && current.teamOrder != null) {
            // An inactive nurse leaves the team order; on reactivation they are placed again like a new member,
            // so they can never share a position with someone ordered meanwhile.
            repo.clearTeamOrder(nurseId)
            adjust(wardId, nurseId, "TEAM_ORDER", current.teamOrder.toString(), null, actor)
            current.teamId?.let { closeOrderGaps(wardId, it, actor) }
        }
        if (current.active != command.active) {
            audit.record(wardId, actor, if (command.active) "STAFF_ACTIVATED" else "STAFF_DEACTIVATED", nurseId.toString())
        }
        return requireStaff(wardId, nurseId).toResult(context(wardId))
    }

    /** Only admins assign teams; nurses cannot move themselves. `null` removes the team. */
    @Transactional
    fun assignTeam(wardId: UUID, nurseId: UUID, teamId: UUID?, actor: String): StaffResult {
        wards.requireAdmin(wardId, actor)
        lockWard(wardId)
        val newTeam = teamId?.let { repo.findTeam(wardId, it) ?: throw BusinessException(ErrorCode.TEAM_NOT_FOUND) }
        val nurse = requireStaff(wardId, nurseId)
        val context = context(wardId)
        if (nurse.teamId != teamId) {
            // A moved nurse has no position in the new team: listed after the admin-ordered members
            // (by career date among the unordered) until the admin drags them into place.
            repo.assignTeam(nurseId, teamId)
            val before = nurse.teamId?.let(context.teams::get)?.name
            adjust(wardId, nurseId, "TEAM_ASSIGNED", before, newTeam?.name, actor)
            if (nurse.teamOrder != null && nurse.teamId != null) {
                // Leaving an ordered team, like deactivation, must not leave a gap (1,3) in that team's order.
                adjust(wardId, nurseId, "TEAM_ORDER", nurse.teamOrder.toString(), null, actor)
                closeOrderGaps(wardId, nurse.teamId, actor)
            }
            audit.record(wardId, actor, "STAFF_TEAM_ASSIGNED", nurseId.toString())
        }
        return requireStaff(wardId, nurseId).toResult(context(wardId))
    }

    /**
     * Saves the admin's drag order for a team: [nurseIds] must be exactly the team's active nurses, most
     * senior first. Anything else means the team changed on another screen, so the client must reload.
     */
    @Transactional
    fun reorderTeam(wardId: UUID, teamId: UUID, nurseIds: List<UUID>, actor: String): List<StaffResult> {
        wards.requireAdmin(wardId, actor)
        repo.findTeam(wardId, teamId) ?: throw BusinessException(ErrorCode.TEAM_NOT_FOUND)
        if (nurseIds.toSet().size != nurseIds.size) throw BusinessException(ErrorCode.INVALID_REQUEST)
        lockWard(wardId)
        val members = repo.activeTeamMembers(teamId).associateBy { it.id }
        if (members.keys != nurseIds.toSet()) throw BusinessException(ErrorCode.TEAM_ORDER_STALE)
        nurseIds.forEachIndexed { index, nurseId ->
            val position = index + 1
            val before = members.getValue(nurseId).teamOrder
            if (before != position) {
                repo.setTeamOrder(nurseId, position)
                adjust(wardId, nurseId, "TEAM_ORDER", before?.toString(), position.toString(), actor)
            }
        }
        audit.record(wardId, actor, "TEAM_REORDERED", teamId.toString())
        val context = context(wardId)
        return nurseIds.map { requireStaff(wardId, it).toResult(context) }
    }

    /** Senior eligibility is set by the admin only (decision 11); the stage override replaces the computed stage. */
    @Transactional
    fun updateRosterSettings(wardId: UUID, nurseId: UUID, input: RosterSettingsInput, actor: String): StaffResult {
        wards.requireAdmin(wardId, actor)
        // Row lock: concurrent saves for one nurse must not both log the same before -> after change.
        val nurse = repo.lockStaff(wardId, nurseId) ?: throw BusinessException(ErrorCode.NURSE_NOT_FOUND)
        val context = context(wardId)
        if (nurse.seniorEligible != input.seniorEligible) {
            adjust(wardId, nurseId, "SENIOR_ELIGIBLE", nurse.seniorEligible.toString(), input.seniorEligible.toString(), actor)
        }
        if (nurse.stageOverride != input.stageOverride) {
            adjust(wardId, nurseId, "STAGE_OVERRIDE", nurse.stageOverride?.toString(), input.stageOverride?.toString(), actor)
        }
        repo.updateRosterSettings(nurseId, input.seniorEligible, input.stageOverride)
        return requireStaff(wardId, nurseId).toResult(context)
    }

    @Transactional(readOnly = true)
    fun adjustments(wardId: UUID, nurseId: UUID, actor: String): List<StaffAdjustmentResult> {
        wards.requireAdmin(wardId, actor)
        requireStaff(wardId, nurseId)
        return repo.adjustments(wardId, nurseId).map {
            StaffAdjustmentResult(it.id, it.kind, it.before, it.after, it.actor, it.actorName, it.at)
        }
    }

    /** Confirms what the nurse submitted. Already-confirmed values are a no-op. */
    @Transactional
    fun confirmProfile(wardId: UUID, nurseId: UUID, actor: String): StaffResult {
        wards.requireAdmin(wardId, actor)
        // Row lock: the previous values written to the history must be the ones this change replaces.
        val nurse = repo.lockStaff(wardId, nurseId) ?: throw BusinessException(ErrorCode.NURSE_NOT_FOUND)
        val start = nurse.careerStartedOn ?: throw BusinessException(ErrorCode.PROFILE_NOT_SUBMITTED)
        val context = context(wardId)
        if (nurse.status() != ProfileStatus.CONFIRMED) {
            validateCareerStart(YearMonth.parse(start), context)
            nurse.rank?.let { validateRank(it, context) }
            repo.confirmCareerStart(nurseId, start, actor, Instant.now())
            adjust(wardId, nurseId, "PROFILE_CONFIRMED", profileLabel(nurse.confirmedCareerStartedOn, nurse.confirmedRank),
                profileLabel(start, nurse.rank), actor)
            audit.record(wardId, actor, "PROFILE_CONFIRMED", nurseId.toString())
        }
        return requireStaff(wardId, nurseId).toResult(context)
    }

    /** Admin correction: the admin's own values are confirmed immediately. */
    @Transactional
    fun setProfileAsAdmin(wardId: UUID, nurseId: UUID, input: ProfileInput, actor: String): StaffResult {
        wards.requireAdmin(wardId, actor)
        // Row lock: concurrent edits are recorded as O -> A -> B, and a same-value save becomes a no-op.
        val nurse = repo.lockStaff(wardId, nurseId) ?: throw BusinessException(ErrorCode.NURSE_NOT_FOUND)
        val context = context(wardId)
        validate(input, context)
        val unchanged = nurse.status() == ProfileStatus.CONFIRMED &&
            nurse.confirmedCareerStartedOn == input.careerStartedOn.toString() && nurse.confirmedRank == input.rank
        if (unchanged) return nurse.toResult(context)
        repo.confirmProfile(nurseId, input.careerStartedOn.toString(), input.rank, actor, Instant.now())
        adjust(wardId, nurseId, "PROFILE_SET_BY_ADMIN", profileLabel(nurse.confirmedCareerStartedOn, nurse.confirmedRank),
            profileLabel(input.careerStartedOn.toString(), input.rank), actor)
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
    fun submitMyProfile(wardId: UUID, hospitalJoinedOn: YearMonth, actor: String): StaffResult {
        wards.requireMember(wardId, actor)
        val nurse = repo.findStaffBySubject(wardId, actor) ?: throw BusinessException(ErrorCode.NURSE_IDENTITY_REQUIRED)
        submitHospitalJoinMonth(nurse, hospitalJoinedOn)
        return requireStaff(wardId, nurse.id).toResult(context(wardId))
    }

    /** Used by invitation redemption inside its transaction, after the nurse link exists. */
    fun submitForLinkedNurse(wardId: UUID, subject: String, hospitalJoinedOn: YearMonth) {
        val nurse = repo.findStaffBySubject(wardId, subject) ?: throw BusinessException(ErrorCode.NURSE_IDENTITY_REQUIRED)
        submitHospitalJoinMonth(nurse, hospitalJoinedOn)
    }

    private fun submitHospitalJoinMonth(nurse: DbStaff, hospitalJoinedOn: YearMonth) {
        validateHospitalJoinMonth(hospitalJoinedOn, context(nurse.wardId))
        if (nurse.hospitalJoinedOn != hospitalJoinedOn.toString()) {
            repo.submitHospitalJoinMonth(nurse.id, hospitalJoinedOn.toString())
        }
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
        validateCareerStart(input.careerStartedOn, context)
        validateRank(input.rank, context)
    }

    private fun validateRank(rank: String, context: Context) {
        if (rank !in context.policy.ranks) throw BusinessException(ErrorCode.RANK_NOT_IN_POLICY)
    }

    private fun validateCareerStart(careerStartedOn: YearMonth, context: Context) {
        if (careerStartedOn.isAfter(context.today) || careerStartedOn.year < 1950) {
            throw BusinessException(ErrorCode.INVALID_CAREER_START)
        }
    }

    private fun validateHospitalJoinMonth(hospitalJoinedOn: YearMonth, context: Context) {
        if (hospitalJoinedOn.isAfter(context.today) || hospitalJoinedOn.year < 1950) {
            throw BusinessException(ErrorCode.INVALID_HOSPITAL_JOIN_MONTH)
        }
    }

    private fun lockWard(wardId: UUID) {
        if (!wardRepo.lockWard(wardId)) throw BusinessException(ErrorCode.WARD_NOT_FOUND)
    }

    /** Renumbers the ordered active members of a team to 1..n after one left, keeping their relative order. */
    private fun closeOrderGaps(wardId: UUID, teamId: UUID, actor: String) {
        repo.activeTeamMembers(teamId).filter { it.teamOrder != null }.sortedBy { it.teamOrder }
            .forEachIndexed { index, member ->
                val position = index + 1
                if (member.teamOrder != position) {
                    repo.setTeamOrder(member.id, position)
                    adjust(wardId, member.id, "TEAM_ORDER", member.teamOrder.toString(), position.toString(), actor)
                }
            }
    }

    private fun adjust(wardId: UUID, nurseId: UUID, kind: String, before: String?, after: String?, actor: String) =
        repo.insertAdjustment(wardId, nurseId, kind, before, after, actor, Instant.now())

    private fun profileLabel(careerStartedOn: String?, rank: String?): String? =
        if (careerStartedOn == null && rank == null) null else "${careerStartedOn ?: "-"} / ${rank ?: "-"}"

    private fun requireStaff(wardId: UUID, nurseId: UUID): DbStaff =
        repo.findStaff(wardId, nurseId) ?: throw BusinessException(ErrorCode.NURSE_NOT_FOUND)

    private fun requireUniqueTeamName(wardId: UUID, name: String, except: UUID?) {
        if (repo.teams(wardId).any { it.name == name && it.id != except }) throw BusinessException(ErrorCode.TEAM_NAME_DUPLICATE)
    }

    private fun DbStaff.status(): ProfileStatus = when {
        careerStartedOn == null -> ProfileStatus.MISSING
        confirmedAt != null && confirmedCareerStartedOn == careerStartedOn && confirmedRank == rank -> ProfileStatus.CONFIRMED
        else -> ProfileStatus.PENDING
    }

    private fun DbStaff.toResult(context: Context): StaffResult {
        val submittedStart = careerStartedOn?.let(YearMonth::parse)
        val confirmedStart = confirmedCareerStartedOn?.let(YearMonth::parse)
        val submittedMonths = submittedStart?.let { experienceMonths(it, context.today) }
        val confirmedMonths = confirmedStart?.let { experienceMonths(it, context.today) }
        val team = teamId?.let(context.teams::get)?.takeIf { it.active }
        val scoringStage = confirmedMonths?.takeIf { status() == ProfileStatus.CONFIRMED }?.let(context.policy::stageFor)
        return StaffResult(
            nurseId = id,
            displayName = displayName,
            qualifications = qualifications,
            active = active,
            linkedAccount = subject != null,
            team = team?.toResult(),
            profile = CareerProfileResult(
                status = status(),
                hospitalJoinedOn = hospitalJoinedOn?.let(YearMonth::parse),
                careerStartedOn = submittedStart,
                rank = rank,
                submittedAt = submittedAt,
                confirmedCareerStartedOn = confirmedStart,
                confirmedRank = confirmedRank,
                confirmedAt = confirmedAt,
                experienceMonths = submittedMonths,
                submittedStage = submittedMonths?.let(context.policy::stageFor),
                scoringStage = scoringStage,
            ),
            teamOrder = teamOrder?.takeIf { team != null },
            seniorEligible = seniorEligible,
            stageOverride = stageOverride,
            rosterStage = stageOverride ?: scoringStage,
        )
    }

    private fun DbTeam.toResult() = TeamResult(id, name, sortOrder)
}
