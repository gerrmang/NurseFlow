package com.nurseflow.app.feature.organization.ui

/**
 * 생성 요청의 `Idempotency-Key`를 관리한다.
 *
 * - 결과가 불확실한 실패(통신 실패·5xx) 뒤 **같은 입력**으로 다시 보내면 같은 키를 재사용한다. 서버가 이미 만들었으면 처음 결과를 돌려준다.
 * - 입력을 바꾸면 새 키를 만든다. 다른 입력에 같은 키를 쓰면 서버가 `IDEMPOTENCY_KEY_REUSED`로 거부하기 때문이다.
 * - 성공이나 확정된 거부(4xx)를 받으면 키를 버린다. 다음 생성은 새 요청이다.
 */
class IdempotentSubmission<P>(private val newKey: () -> String) {
    private var pending: Pair<P, String>? = null

    fun keyFor(payload: P): String {
        pending?.let { (previous, key) -> if (previous == payload) return key }
        return newKey().also { pending = payload to it }
    }

    fun settle() {
        pending = null
    }
}
