package com.nurseflow.app.core.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

interface HolidaySettingsApi {
    @GET("api/v1/wards/{w}/holiday-calendar") suspend fun calendar(@Path("w") ward: String, @Query("month") month: String): Response<HolidayCalendar>
    @PUT("api/v1/wards/{w}/holiday-calendar") suspend fun saveCalendar(@Path("w") ward: String, @Query("month") month: String, @Header("If-Match") tag: String, @Body input: HolidayCalendarInput): Response<HolidayCalendar>
    @POST("api/v1/wards/{w}/holiday-calendar/refresh") suspend fun refresh(@Path("w") ward: String, @Query("month") month: String): Response<HolidayCalendar>
    @GET("api/v1/wards/{w}/target-hour-policy") suspend fun policy(@Path("w") ward: String): Response<HolidayPolicy>
    @PUT("api/v1/wards/{w}/target-hour-policy") suspend fun savePolicy(@Path("w") ward: String, @Header("If-Match") tag: String, @Body input: HolidayPolicyInput): Response<HolidayPolicy>
}
@Serializable data class HolidayDay(val date: String, val kind: String, val name: String? = null)
@Serializable data class HolidayCalendarInput(val days: List<HolidayDay>)
@Serializable data class HolidaySource(val configured: Boolean, val fetchedAt: String?)
@Serializable data class HolidayTarget(val month: String, val daysInMonth: Int, val weekendDays: Int, val holidayDays: Int, val wardRestDays: Int, val wardWorkdays: Int, val restDays: Int, val targetDays: Int, val dailyStandardMinutes: Int, val generalTargetMinutes: Int)
@Serializable data class HolidayCalendar(val month: String, val days: List<HolidayDay>, val publicHolidays: List<HolidayDay>, val holidaySource: HolidaySource, val target: HolidayTarget, val policyRevision: Int)
@Serializable data class HolidayPolicy(val dailyStandardMinutes: Int, val leaveCreditMinutes: Int, val halfDayCreditMinutes: Int, val sofCreditMinutes: Int, val nightDedicatedTargetMinutes: Int?, val nightDedicatedNightCreditMinutes: Int?, val policyRevision: Int) {
    fun input() = HolidayPolicyInput(dailyStandardMinutes, leaveCreditMinutes, halfDayCreditMinutes, sofCreditMinutes, nightDedicatedTargetMinutes, nightDedicatedNightCreditMinutes)
}
@Serializable data class HolidayPolicyInput(val dailyStandardMinutes: Int, val leaveCreditMinutes: Int, val halfDayCreditMinutes: Int, val sofCreditMinutes: Int, val nightDedicatedTargetMinutes: Int? = null, val nightDedicatedNightCreditMinutes: Int? = null)
