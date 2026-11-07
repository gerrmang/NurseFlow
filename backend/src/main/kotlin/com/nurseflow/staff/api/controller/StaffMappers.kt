package com.nurseflow.staff.api.controller

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.staff.api.dto.request.CareerProfileRequest
import com.nurseflow.staff.api.dto.request.HospitalJoinMonthRequest
import com.nurseflow.staff.api.dto.request.StaffRequest
import com.nurseflow.staff.api.dto.response.CareerPolicyResponse
import com.nurseflow.staff.api.dto.response.CareerProfileResponse
import com.nurseflow.staff.api.dto.response.StaffMemberResponse
import com.nurseflow.staff.api.dto.response.TeamResponse
import com.nurseflow.staff.application.model.CareerPolicyResult
import com.nurseflow.staff.application.model.ProfileInput
import com.nurseflow.staff.application.model.StaffInputCommand
import com.nurseflow.staff.application.model.StaffResult
import com.nurseflow.staff.application.model.TeamResult
import java.time.YearMonth
import java.time.format.DateTimeParseException

internal fun TeamResult.toResponse() = TeamResponse(teamId, name, sortOrder)

internal fun CareerPolicyResult.toResponse() = CareerPolicyResponse(stageThresholdMonths, ranks, isDefault)

internal fun StaffResult.toResponse() = StaffMemberResponse(
    nurseId, displayName, qualifications, active, linkedAccount, team?.toResponse(),
    with(profile) {
        CareerProfileResponse(
            status.name, hospitalJoinedOn?.toString(), careerStartedOn?.toString(), rank, submittedAt,
            confirmedCareerStartedOn?.toString(), confirmedRank, confirmedAt,
            experienceMonths, submittedStage, scoringStage,
        )
    },
)

internal fun CareerProfileRequest.toInput() = ProfileInput(
    try {
        YearMonth.parse(careerStartedOn)
    } catch (_: DateTimeParseException) {
        throw BusinessException(ErrorCode.INVALID_CAREER_START)
    },
    rank.trim(),
)

internal fun HospitalJoinMonthRequest.toHospitalJoinMonth() = try {
    YearMonth.parse(hospitalJoinedOn)
} catch (_: DateTimeParseException) {
    throw BusinessException(ErrorCode.INVALID_HOSPITAL_JOIN_MONTH)
}

internal fun StaffRequest.toCommand() = StaffInputCommand(displayName, qualifications, active, teamId)
