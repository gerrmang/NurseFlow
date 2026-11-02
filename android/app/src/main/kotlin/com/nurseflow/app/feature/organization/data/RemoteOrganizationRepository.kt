package com.nurseflow.app.feature.organization.data

import com.nurseflow.app.core.network.ApiClient
import com.nurseflow.app.core.network.ApiResult
import com.nurseflow.app.feature.organization.domain.Hospital
import com.nurseflow.app.feature.organization.domain.HospitalWard
import com.nurseflow.app.feature.organization.domain.MyWard
import com.nurseflow.app.feature.organization.domain.OrganizationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class RemoteOrganizationRepository(
    private val client: ApiClient,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : OrganizationRepository {

    override suspend fun myWards(): ApiResult<List<MyWard>> = withContext(io) {
        client.get("/api/v1/wards", ItemsDto.serializer(WardMembershipDto.serializer())).map { page ->
            page.items.map { MyWard(it.wardId, it.wardName, it.timeZone, it.hospitalId, it.hospitalName, it.roles.toSet(), it.canViewWardCalendar) }
        }
    }

    override suspend fun myHospitals(): ApiResult<List<Hospital>> = withContext(io) {
        client.get("/api/v1/hospitals", ItemsDto.serializer(HospitalDto.serializer())).map { page ->
            page.items.map { Hospital(it.hospitalId, it.name, it.timeZone) }
        }
    }

    override suspend fun hospitalWards(hospitalId: String): ApiResult<List<HospitalWard>> = withContext(io) {
        client.get("/api/v1/hospitals/${segment(hospitalId)}/wards", ItemsDto.serializer(WardDto.serializer()))
            .map { page -> page.items.map(::toWard) }
    }

    override suspend fun createWard(
        hospitalId: String, name: String, timeZone: String?, idempotencyKey: String,
    ): ApiResult<HospitalWard> = withContext(io) {
        client.post(
            "/api/v1/hospitals/${segment(hospitalId)}/wards",
            CreateWardRequestDto(name, timeZone),
            CreateWardRequestDto.serializer(),
            WardDto.serializer(),
            headers = mapOf("Idempotency-Key" to idempotencyKey),
        ).map(::toWard)
    }

    private fun toWard(dto: WardDto) = HospitalWard(dto.wardId, dto.hospitalId, dto.name, dto.timeZone)

    private fun segment(value: String) = URLEncoder.encode(value, "UTF-8")

    private inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
        is ApiResult.Success -> ApiResult.Success(transform(value))
        is ApiResult.Failure -> this
    }
}
