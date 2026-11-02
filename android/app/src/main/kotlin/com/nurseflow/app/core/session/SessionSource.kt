package com.nurseflow.app.core.session

import com.nurseflow.app.core.network.AccessTokenProvider
import kotlinx.coroutines.flow.StateFlow

/**
 * 로그인 세션의 출처. 실제 Kakao 로그인·서버 세션 교환(기능 2)이 연결되면 그 구현으로 교체한다.
 * 앱 코드에는 토큰이나 관리자 계정을 하드코딩하지 않는다. 서버가 모든 토큰을 OIDC로 검증한다.
 */
interface SessionSource : AccessTokenProvider {
    val token: StateFlow<String?>

    /** 개발용 토큰 입력 화면을 보여줄 수 있는가. 디버그 빌드에서만 true. */
    val acceptsDeveloperToken: Boolean

    fun useDeveloperToken(token: String)

    /** 세션을 지운다. 401 응답 뒤 다시 로그인하도록 한다. */
    fun signOut()

    override fun currentToken(): String? = token.value
}
