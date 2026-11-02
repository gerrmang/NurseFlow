package com.nurseflow.app.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 릴리스 빌드에는 실제 로그인이 아직 없으므로 세션이 항상 비어 있다. 토큰 입력·우회 경로를 두지 않는다.
 * Kakao 로그인 기능이 들어오면 이 구현을 교체한다.
 */
fun createSessionSource(): SessionSource = object : SessionSource {
    private val state = MutableStateFlow<String?>(null)
    override val token: StateFlow<String?> = state.asStateFlow()
    override val acceptsDeveloperToken: Boolean = false
    override fun useDeveloperToken(token: String) = Unit
    override fun signOut() {
        state.value = null
    }
}
