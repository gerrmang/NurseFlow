package com.nurseflow.app.feature.invite

import com.nurseflow.app.core.network.ApiException

/** Invitation failures classified by the server's stable `code` first, then HTTP status. */
enum class InviteFailure(val message: String) {
    EXPIRED("만료된 초대예요. 관리자에게 새 초대를 요청해 주세요."),
    REVOKED("철회된 초대예요. 관리자에게 문의해 주세요."),
    ALREADY_REDEEMED("이미 사용된 초대예요. 초대는 한 번만 사용할 수 있어요."),
    NOT_FOUND("초대를 찾을 수 없어요. 링크를 다시 확인해 주세요."),
    ALREADY_MEMBER("이미 이 병동에 소속되어 있어요."),
    NURSE_ALREADY_LINKED("이미 다른 계정과 연결된 간호사 기록이에요."),
    ADMIN_NURSE_CONFLICT("이 병동에 연결된 간호사 기록이 있는 계정은 관리자 초대를 수락할 수 없어요."),
    ALREADY_PENDING("이 간호사에게 사용 가능한 초대가 이미 있어요."),
    JOIN_MONTH_REQUIRED("병원 입사 연월을 입력해 주세요."),
    INVALID_JOIN_MONTH("병원 입사 연월이 올바르지 않아요. 미래 연월은 입력할 수 없어요."),
    NOT_ADMIN("이 병동의 관리자 권한이 없어요."),
    NURSE_NOT_FOUND("간호사 기록을 찾을 수 없어요. 목록을 새로 불러와 주세요."),
    INVALID_REQUEST("요청 형식이 올바르지 않아요."),
    UNAUTHORIZED("로그인이 필요해요. 다시 로그인해 주세요."),
    NETWORK("서버에 연결할 수 없어요. 네트워크를 확인해 주세요."),
    SERVER("서버에 일시적인 문제가 있어요. 잠시 후 다시 시도해 주세요."),
    UNKNOWN("요청을 처리하지 못했어요."),
    ;

    /** The link itself can no longer be used; the only action is to close it. */
    val endsInvitation: Boolean get() = this in setOf(EXPIRED, REVOKED, ALREADY_REDEEMED, NOT_FOUND)
    /** The server may or may not have processed a write: the result must be checked, not resent blindly. */
    val outcomeUnknown: Boolean get() = this == NETWORK || this == SERVER
}

fun Throwable.toInviteFailure(): InviteFailure {
    val e = this as? ApiException ?: return InviteFailure.UNKNOWN
    return when (e.code) {
        "INVITATION_EXPIRED" -> InviteFailure.EXPIRED
        "INVITATION_REVOKED" -> InviteFailure.REVOKED
        "INVITATION_ALREADY_REDEEMED" -> InviteFailure.ALREADY_REDEEMED
        "INVITATION_NOT_FOUND" -> InviteFailure.NOT_FOUND
        "ALREADY_WARD_MEMBER" -> InviteFailure.ALREADY_MEMBER
        "NURSE_ALREADY_LINKED" -> InviteFailure.NURSE_ALREADY_LINKED
        "ADMIN_NURSE_RECORD_CONFLICT" -> InviteFailure.ADMIN_NURSE_CONFLICT
        "INVITATION_ALREADY_PENDING" -> InviteFailure.ALREADY_PENDING
        "HOSPITAL_JOIN_MONTH_REQUIRED" -> InviteFailure.JOIN_MONTH_REQUIRED
        "INVALID_HOSPITAL_JOIN_MONTH" -> InviteFailure.INVALID_JOIN_MONTH
        "WARD_ADMIN_REQUIRED", "WARD_NOT_FOUND" -> InviteFailure.NOT_ADMIN
        "NURSE_NOT_FOUND" -> InviteFailure.NURSE_NOT_FOUND
        "INVALID_REQUEST", "INVALID_INVITATION" -> InviteFailure.INVALID_REQUEST
        else -> when {
            e.status == 401 -> InviteFailure.UNAUTHORIZED
            e.status == 403 -> InviteFailure.NOT_ADMIN
            e.status == 0 -> InviteFailure.NETWORK
            e.status in 500..599 -> InviteFailure.SERVER
            else -> InviteFailure.UNKNOWN
        }
    }
}
