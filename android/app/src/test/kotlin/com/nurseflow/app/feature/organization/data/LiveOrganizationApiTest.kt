package com.nurseflow.app.feature.organization.data

import com.nurseflow.app.core.network.ApiClient
import com.nurseflow.app.core.network.ApiError
import com.nurseflow.app.core.network.ApiResult
import com.nurseflow.app.core.network.UrlConnectionTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.URL
import java.util.UUID

/**
 * 실제 백엔드에 앱의 네트워크·저장소 코드를 연결하는 로컬 통합 검증. `scripts/local-e2e/run-hospital-ward-e2e.sh`가
 * 로컬 OIDC 발급자와 서버를 띄우고 환경변수를 넘길 때만 실행되며, 일반 단위 테스트 실행에서는 건너뛴다.
 * Kakao 로그인과 Android 기기 화면은 포함하지 않는다.
 */
class LiveOrganizationApiTest {
    private val baseUrl: String? = System.getenv("NURSEFLOW_LIVE_BASE_URL")
    private val issuer: String? = System.getenv("NURSEFLOW_LIVE_ISSUER")
    private val adminSubject: String? = System.getenv("NURSEFLOW_LIVE_HOSPITAL_ADMIN")

    @Before
    fun requireLiveEnvironment() {
        assumeTrue("로컬 통합 환경 변수가 없어 건너뜀", baseUrl != null && issuer != null && adminSubject != null)
    }

    private fun token(subject: String, ttlSeconds: Int = 600): String =
        Regex("\"access_token\":\"([^\"]+)\"").find(URL("$issuer/token?sub=$subject&ttl=$ttlSeconds").readText())!!.groupValues[1]

    private fun repo(token: String?) =
        RemoteOrganizationRepository(ApiClient(baseUrl!!, { token }, UrlConnectionTransport()), Dispatchers.IO)

    @Test
    fun `hospital admin creates a ward through the app network layer and retries idempotently`() = runTest {
        val admin = repo(token(adminSubject!!))
        val hospital = (admin.myHospitals() as ApiResult.Success).value.single()
        val name = "E2E 병동 ${UUID.randomUUID().toString().take(8)}"
        val key = UUID.randomUUID().toString()

        val created = (admin.createWard(hospital.hospitalId, name, null, key) as ApiResult.Success).value
        val retried = (admin.createWard(hospital.hospitalId, name, null, key) as ApiResult.Success).value

        assertEquals(created, retried)
        assertEquals(hospital.timeZone, created.timeZone)
        assertTrue((admin.hospitalWards(hospital.hospitalId) as ApiResult.Success).value.any { it.wardId == created.wardId })
        val mine = (admin.myWards() as ApiResult.Success).value.single { it.wardId == created.wardId }
        assertTrue(mine.isWardAdmin)
        assertEquals(hospital.name, mine.hospitalName)
        assertEquals(
            ApiResult.Failure(ApiError.Rejected(409, "WARD_NAME_TAKEN")),
            admin.createWard(hospital.hospitalId, name, null, UUID.randomUUID().toString()),
        )
        assertEquals(
            ApiResult.Failure(ApiError.Rejected(409, "IDEMPOTENCY_KEY_REUSED")),
            admin.createWard(hospital.hospitalId, "$name-2", null, key),
        )
    }

    @Test
    fun `unrelated user sees nothing and cannot create wards`() = runTest {
        val adminHospital = (repo(token(adminSubject!!)).myHospitals() as ApiResult.Success).value.single()
        val stranger = repo(token("e2e-stranger-${UUID.randomUUID().toString().take(8)}"))

        assertEquals(ApiResult.Success(emptyList<Any>()), stranger.myWards())
        assertEquals(ApiResult.Success(emptyList<Any>()), stranger.myHospitals())
        assertEquals(ApiResult.Failure(ApiError.NotFound("HOSPITAL_NOT_FOUND")), stranger.hospitalWards(adminHospital.hospitalId))
        assertEquals(
            ApiResult.Failure(ApiError.NotFound("HOSPITAL_NOT_FOUND")),
            stranger.createWard(adminHospital.hospitalId, "침투 병동", null, UUID.randomUUID().toString()),
        )
    }

    @Test
    fun `expired or forged tokens are rejected as session expiry`() = runTest {
        assertEquals(ApiResult.Failure(ApiError.SessionExpired), repo(token(adminSubject!!, ttlSeconds = -300)).myWards())
        assertEquals(ApiResult.Failure(ApiError.SessionExpired), repo("not-a-jwt").myHospitals())
    }
}
