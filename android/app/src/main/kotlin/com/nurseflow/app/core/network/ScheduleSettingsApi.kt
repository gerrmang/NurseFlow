package com.nurseflow.app.core.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

/** A8/A9/A10 and existing draft/absence contracts. Header values are opaque server ETags. */
interface ScheduleSettingsApi {
    @GET("api/v1/wards/{w}/shift-types?includeInactive=true") suspend fun shifts(@Path("w") ward: String): Response<ScheduleShifts>
    @POST("api/v1/wards/{w}/shift-types") suspend fun createShift(@Path("w") ward: String, @Header("If-Match") tag: String, @Header("Idempotency-Key") key: String, @Body input: ScheduleShiftInput): Response<ScheduleShift>
    @PUT("api/v1/wards/{w}/shift-types/{id}") suspend fun updateShift(@Path("w") ward: String, @Path("id") id: String, @Header("If-Match") tag: String, @Body input: ScheduleShiftInput): Response<ScheduleShift>
    @DELETE("api/v1/wards/{w}/shift-types/{id}") suspend fun deactivateShift(@Path("w") ward: String, @Path("id") id: String, @Header("If-Match") tag: String): Response<ScheduleShift>
    @POST("api/v1/wards/{w}/shift-types/{id}/preview") suspend fun preview(@Path("w") ward: String, @Path("id") id: String, @Body input: ScheduleShiftInput): Response<SchedulePreview>
    @GET("api/v1/wards/{w}/teams") suspend fun teams(@Path("w") ward: String): Response<Items<AdminTeam>>
    @GET("api/v1/wards/{w}/staff?includeInactive=true") suspend fun staff(@Path("w") ward: String): Response<Items<AdminStaff>>
    @GET("api/v1/wards/{w}/rosters") suspend fun rosters(@Path("w") ward: String, @Query("month") month: String): Response<Items<ScheduleRoster>>
    @POST("api/v1/wards/{w}/rosters") suspend fun createRoster(@Path("w") ward: String, @Header("Idempotency-Key") key: String, @Body input: ScheduleMonth): Response<ScheduleRoster>
    @GET("api/v1/rosters/{id}") suspend fun roster(@Path("id") id: String): Response<ScheduleRoster>
    @PUT("api/v1/rosters/{id}/requirements") suspend fun requirements(@Path("id") id: String, @Header("If-Match") tag: String, @Body input: ScheduleRequirements): Response<ScheduleRoster>
    @GET("api/v1/rosters/{id}/absences") suspend fun absences(@Path("id") id: String): Response<Items<ScheduleAbsence>>
    @POST("api/v1/rosters/{id}/absences") suspend fun createAbsence(@Path("id") id: String, @Header("If-Match") tag: String, @Header("Idempotency-Key") key: String, @Body input: ScheduleAbsenceInput): Response<ScheduleAbsence>
    @PUT("api/v1/rosters/{id}/absences/{absence}") suspend fun updateAbsence(@Path("id") id: String, @Path("absence") absence: String, @Header("If-Match") tag: String, @Body input: ScheduleAbsenceInput): Response<ScheduleAbsence>
    @DELETE("api/v1/rosters/{id}/absences/{absence}") suspend fun deleteAbsence(@Path("id") id: String, @Path("absence") absence: String, @Header("If-Match") tag: String): Response<Unit>
    @GET("api/v1/wards/{w}/staff/{id}/work-pattern") suspend fun pattern(@Path("w") ward: String, @Path("id") id: String): Response<SchedulePattern>
    @PUT("api/v1/wards/{w}/staff/{id}/work-pattern") suspend fun savePattern(@Path("w") ward: String, @Path("id") id: String, @Body input: SchedulePattern): Response<SchedulePattern>
}

@Serializable data class ScheduleShifts(val items: List<ScheduleShift>, val policyRevision: Int)
@Serializable data class ScheduleShift(
    val shiftTypeId: String, val code: String, val displayName: String, val kind: String,
    val startTime: String, val endTime: String, val spansMidnight: Boolean,
    val breakMinutes: Int, val recognizedMinutes: Int, val targetCreditMinutes: Int,
    val durationMinutes: Int, val active: Boolean,
)
@Serializable data class ScheduleShiftInput(
    val code: String, val displayName: String, val kind: String, val startTime: String, val endTime: String,
    val spansMidnight: Boolean, val breakMinutes: Int, val recognizedMinutes: Int,
    val targetCreditMinutes: Int, val active: Boolean = true,
)
@Serializable data class SchedulePreview(
    val shiftType: ScheduleShift, val policyRevision: Int, val flagsDrafts: Boolean,
    val rewritesDraftSlotTimes: Boolean, val affectedDrafts: List<ScheduleAffectedDraft>,
    val publishedRostersUsingType: Int,
)
@Serializable data class ScheduleAffectedDraft(val month: String, val slots: Int, val assignedSlots: Int)
@Serializable data class ScheduleMonth(val month: String)
@Serializable data class ScheduleStaffing(val required: Int, val assigned: Int, val unfilled: Int, val hardRuleViolations: Int, val seniorUnfilled: Int = 0)
@Serializable data class ScheduleRoster(
    val rosterId: String, val wardId: String, val month: String, val status: String,
    val revision: Int, val timeZone: String, val staffing: ScheduleStaffing,
    val slots: List<ScheduleSlot>? = null, val needsRevalidation: Boolean = false,
)
@Serializable data class ScheduleSlot(
    val slotId: String, val date: String, val shiftTypeId: String, val shiftCode: String,
    val status: String, val teamId: String? = null, val seniorRequired: Boolean = false,
)
@Serializable data class ScheduleRequirement(val date: String, val shiftTypeId: String, val requiredCount: Int, val requiredQualifications: List<String>, val teamId: String? = null)
@Serializable data class ScheduleRequirements(val items: List<ScheduleRequirement>)
@Serializable data class ScheduleAbsence(val absenceId: String, val nurseId: String, val kind: String, val startsAt: String, val endsAt: String)
@Serializable data class ScheduleAbsenceInput(val nurseId: String, val kind: String, val startsAt: String, val endsAt: String)
@Serializable data class SchedulePattern(val workPattern: String)
