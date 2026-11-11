package com.nurseflow.app.feature.invite

import com.nurseflow.app.core.network.CreatedInvitationDto
import com.nurseflow.app.core.network.InvitationDto
import com.nurseflow.app.core.network.InvitationRoles
import com.nurseflow.app.core.network.StaffMemberDto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class InvitationStatus(val label: String) {
    PENDING("사용 대기"), REDEEMED("가입 완료"), REVOKED("철회됨"), EXPIRED("만료됨"), UNKNOWN("알 수 없음");

    /** Server rule: only PENDING and EXPIRED invitations can be reissued or revoked. */
    val manageable: Boolean get() = this == PENDING || this == EXPIRED

    companion object {
        fun of(raw: String) = entries.firstOrNull { it.name == raw && it != UNKNOWN } ?: UNKNOWN
    }
}

private val instantFormat = DateTimeFormatter.ofPattern("M월 d일 HH:mm")

fun formatInstant(raw: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching { Instant.parse(raw).atZone(zone).format(instantFormat) }.getOrDefault("-")

/**
 * Target label without any server-side name or memo (decision §7 #7, 2026-11-10): a linked nurse
 * record shows its name and join state; otherwise a fixed label by role.
 */
fun targetLabel(invitation: InvitationDto, nurses: Map<String, StaffMemberDto>): String {
    val nurseId = invitation.nurseId
    return when {
        nurseId != null -> nurses[nurseId]?.let { "${it.displayName} · ${if (it.linkedAccount) "가입됨" else "미가입"}" }
            ?: "연결된 간호사 기록 · 현재 목록에 없음"
        invitation.role == InvitationRoles.ADMIN -> "관리자 초대 · 대상 미지정"
        else -> "새 간호사 초대 · 가입 시 기록 생성"
    }
}

/** Short, non-secret suffix to tell otherwise identical rows apart. */
fun invitationShortId(invitation: InvitationDto) = "#${invitation.invitationId.takeLast(4)}"

/**
 * The shareable link for a just-issued token: the server's `inviteUrl` when configured, otherwise
 * the build's development link base. Null when neither exists (no link can be formed).
 */
fun inviteLinkFor(created: CreatedInvitationDto, fallbackBase: String): String? =
    created.inviteUrl?.takeIf { it.isNotBlank() }
        ?: fallbackBase.trim().takeIf { it.isNotEmpty() }?.let { "$it#token=${created.token}" }
