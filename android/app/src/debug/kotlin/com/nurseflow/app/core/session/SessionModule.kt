package com.nurseflow.app.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * 디버그 빌드 전용. 개발자가 개발용 OIDC 발급자에서 받은 access token을 직접 붙여넣어 서버 연동을 확인한다.
 * 토큰은 메모리에만 두고 저장·로그 출력하지 않는다. 서버는 이 토큰도 운영과 같은 issuer·audience·서명 검증을 거친다.
 * 릴리스 빌드에는 이 파일이 포함되지 않는다.
 */
fun createSessionSource(): SessionSource = object : SessionSource {
    private val state = MutableStateFlow<ActiveSession?>(null)
    override val activeSession: StateFlow<ActiveSession?> = state.asStateFlow()
    override val acceptsDeveloperToken: Boolean = true

    override fun useDeveloperToken(token: String) {
        val normalized = token.trim().removePrefix("Bearer ").trim().takeIf { it.isNotEmpty() }
        state.value = normalized?.let { ActiveSession(UUID.randomUUID(), it) }
    }

    override fun signOut() {
        state.value = null
    }
}
