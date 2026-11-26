package com.nurseflow.policy.api.dto.response

import java.time.LocalDate

data class TargetHourPolicyResponse(
    val dailyStandardMinutes: Int,
    val leaveCreditMinutes: Int,
    val halfDayCreditMinutes: Int,
    val sofCreditMinutes: Int,
    val nightDedicatedTargetMinutes: Int?,
    val nightDedicatedNightCreditMinutes: Int?,
    val policyRevision: Int,
)

data class HolidayCalendarDayResponse(val date: LocalDate, val kind: String, val name: String?)

data class MonthTargetResponse(
    val month: String,
    val daysInMonth: Int,
    val weekendDays: Int,
    val holidayDays: Int,
    val wardRestDays: Int,
    val wardWorkdays: Int,
    val restDays: Int,
    val targetDays: Int,
    val dailyStandardMinutes: Int,
    val generalTargetMinutes: Int,
)

data class HolidaySourceResponse(val configured: Boolean, val fetchedAt: java.time.Instant?)

data class HolidayCalendarResponse(
    val month: String,
    val days: List<HolidayCalendarDayResponse>,
    val publicHolidays: List<HolidayCalendarDayResponse>,
    val holidaySource: HolidaySourceResponse,
    val target: MonthTargetResponse,
    val policyRevision: Int,
)
