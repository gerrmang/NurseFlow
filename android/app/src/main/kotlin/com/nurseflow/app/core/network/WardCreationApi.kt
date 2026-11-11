package com.nurseflow.app.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * A3/A4 contract (docs/openapi.yaml): official hospital search and ward creation. Kept apart from
 * [NurseFlowApi] because the create call is a write that must never be retried automatically:
 * a lost response is resolved by the screen with the same Idempotency-Key.
 */
interface WardCreationApi {
    @GET("api/v1/hospitals/search")
    suspend fun searchHospitals(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20,
    ): HospitalSearchPageDto

    @POST("api/v1/wards")
    suspend fun createWard(@Header("Idempotency-Key") idempotencyKey: String, @Body body: CreateWardBody): MembershipDto
}

@Serializable
data class HospitalCandidateDto(
    val source: String,
    val externalId: String,
    val name: String,
    val address: String,
    val category: String? = null,
)

@Serializable
data class HospitalSearchPageDto(
    val items: List<HospitalCandidateDto>,
    val page: Int,
    val size: Int,
    val totalCount: Int,
    val attribution: String? = null,
)

/** Only the official reference and the ward name. The server resolves hospital name/address itself. */
@Serializable
data class HospitalRefBody(val source: String, val externalId: String)

/** `timeZone` is omitted so the server default (Asia/Seoul) applies. */
@Serializable
data class CreateWardBody(val hospital: HospitalRefBody, val wardName: String)
