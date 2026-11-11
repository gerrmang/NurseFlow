package com.nurseflow.app.feature.ward.create

/**
 * Client-side mirror of the server's hospital search query rule, only to avoid a pointless call
 * and to word the hint. The server stays the authority. Returns a Korean hint, or null if valid:
 * trimmed 2..50 characters, no control characters, and at least two letters or digits once
 * spaces, brackets and symbols are removed.
 */
fun validateHospitalQuery(raw: String): String? {
    val query = raw.trim()
    if (query.codePointCount(0, query.length) < 2) return "병원 이름을 2자 이상 입력해 주세요."
    if (query.codePointCount(0, query.length) > 50) return "검색어는 50자 이하로 입력해 주세요."
    if (query.any { Character.isISOControl(it) }) return "검색어에 사용할 수 없는 문자가 있어요."
    if (query.count { it.isLetterOrDigit() } < 2) return "기호를 뺀 글자나 숫자를 2자 이상 입력해 주세요."
    return null
}
