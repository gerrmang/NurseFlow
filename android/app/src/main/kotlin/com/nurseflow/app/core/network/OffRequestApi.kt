package com.nurseflow.app.core.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** A12. All calls use the session-bound, non-retrying client. */
interface OffRequestApi {
    @GET("api/v1/wards/{w}/off-requests") suspend fun list(@Path("w") ward: String, @Query("month") month: String): OffRequestList
    @POST("api/v1/wards/{w}/off-requests") suspend fun submit(@Path("w") ward: String, @Body input: OffRequestInput): OffRequest
    @POST("api/v1/wards/{w}/off-requests/{id}/confirm") suspend fun confirm(@Path("w") ward: String, @Path("id") id: String): OffRequest
    @POST("api/v1/wards/{w}/off-requests/{id}/reject") suspend fun reject(@Path("w") ward: String, @Path("id") id: String): OffRequest
    @POST("api/v1/wards/{w}/off-requests/{id}/withdraw") suspend fun withdraw(@Path("w") ward: String, @Path("id") id: String): OffRequest
    @GET("api/v1/wards/{w}/off-requests/policy") suspend fun policy(@Path("w") ward: String): Response<OffRequestPolicy>
    @PUT("api/v1/wards/{w}/off-requests/policy") suspend fun savePolicy(@Path("w") ward: String, @Header("If-Match") etag: String, @Body input: OffRequestPolicyInput): Response<OffRequestPolicy>
    @GET("api/v1/wards/{w}/roster-eligibility") suspend fun eligibility(@Path("w") ward: String): OffEligibility
}

@Serializable data class OffRequest(
    val requestId: String, val nurseId: String, val date: String, val status: String, val source: String,
    val submittedAt: String, val decidedAt: String? = null, val afterDeadline: Boolean,
) {
    val holdsDate: Boolean get() = status == "SUBMITTED" || status == "CONFIRMED"
    fun checked(): OffRequest = apply {
        require(requestId.isNotBlank() && nurseId.isNotBlank())
        val day = LocalDate.parse(date)
        require(day.toString() == date && day.year in 1..9999)
        require(status in setOf("SUBMITTED", "CONFIRMED", "REJECTED", "WITHDRAWN"))
        require(source in setOf("NURSE", "ADMIN"))
        Instant.parse(submittedAt); decidedAt?.let(Instant::parse)
    }
}
@Serializable data class OffRequestList(val items: List<OffRequest>, val deadline: String, val monthlyLimit: Int) {
    fun checked(month: YearMonth): OffRequestList = apply {
        Instant.parse(deadline); require(monthlyLimit in 0..31)
        require(items.map { it.requestId }.distinct().size == items.size)
        items.forEach { it.checked(); require(YearMonth.from(LocalDate.parse(it.date)) == month) }
    }
}
@Serializable data class OffRequestInput(val date: String, val nurseId: String? = null)
@Serializable data class OffRequestPolicy(val monthlyLimit: Int, val deadlineDay: Int, val policyRevision: Int)
@Serializable data class OffRequestPolicyInput(val monthlyLimit: Int, val deadlineDay: Int)
@Serializable data class OffEligibleNurse(val nurseId: String, val displayName: String, val eligible: Boolean, val reason: String)
@Serializable data class OffEligibility(val demoMode: Boolean, val policyRevision: Int, val items: List<OffEligibleNurse>) {
    fun checked(): OffEligibility = apply {
        require(policyRevision >= 0 && items.map { it.nurseId }.distinct().size == items.size)
        require(items.all { it.nurseId.isNotBlank() })
    }
}
data class OffPolicySnapshot(val value: OffRequestPolicy, val etag: String)
fun Response<OffRequestPolicy>.checkedPolicy(): OffPolicySnapshot {
    if (!isSuccessful) throw retrofit2.HttpException(this)
    val value = requireNotNull(body())
    require(value.monthlyLimit in 0..31 && value.deadlineDay in 1..28 && value.policyRevision >= 0)
    val etag = headers()["ETag"]
    require(etag == "\"policy-${value.policyRevision}\"")
    return OffPolicySnapshot(value, requireNotNull(etag))
}
