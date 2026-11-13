import { ClientErrorCode } from '../api/http'
import type { Invitation, InvitationRole, InvitationStatus, StaffMember } from '../api/types'
import type { LoginReturnFailure } from './pendingInvite'

/**
 * Invitation outcomes, decided from the server's stable `code` (then the HTTP status).
 * User-facing text comes only from this file: server `detail` and user input are never shown.
 */
export type InviteFailure =
  // The invitation can no longer be used.
  | 'expired'
  | 'revoked'
  | 'redeemed'
  | 'notFound'
  // The invitation cannot be accepted by this account.
  | 'alreadyMember'
  | 'nurseLinked'
  | 'adminConflict'
  // The input can be corrected.
  | 'joinMonthRequired'
  | 'invalidJoinMonth'
  | 'invalidRequest'
  // Admin actions.
  | 'forbidden'
  | 'alreadyPending'
  | 'nurseNotFound'
  // Session / transport.
  | 'userNotFound'
  | 'sessionEnded'
  | 'network'
  | 'server'
  | 'unknown'

export function classifyInviteError(code: string, status: number): InviteFailure {
  switch (code) {
    case 'INVITATION_EXPIRED':
      return 'expired'
    case 'INVITATION_REVOKED':
      return 'revoked'
    case 'INVITATION_ALREADY_REDEEMED':
      return 'redeemed'
    case 'INVITATION_NOT_FOUND':
      return 'notFound'
    case 'ALREADY_WARD_MEMBER':
      return 'alreadyMember'
    case 'NURSE_ALREADY_LINKED':
      return 'nurseLinked'
    case 'ADMIN_NURSE_RECORD_CONFLICT':
      return 'adminConflict'
    case 'HOSPITAL_JOIN_MONTH_REQUIRED':
      return 'joinMonthRequired'
    case 'INVALID_HOSPITAL_JOIN_MONTH':
      return 'invalidJoinMonth'
    case 'INVALID_REQUEST':
    case 'INVALID_INVITATION':
      return 'invalidRequest'
    case 'WARD_ADMIN_REQUIRED':
    case 'WARD_NOT_FOUND':
    case 'FORBIDDEN':
      return 'forbidden'
    case 'INVITATION_ALREADY_PENDING':
      return 'alreadyPending'
    case 'NURSE_NOT_FOUND':
      return 'nurseNotFound'
    case 'USER_NOT_FOUND':
      return 'userNotFound'
    case ClientErrorCode.SESSION_EXPIRED:
    case ClientErrorCode.ABORTED:
    case 'UNAUTHORIZED':
      return 'sessionEnded'
    case ClientErrorCode.NETWORK:
      return 'network'
  }
  if (status >= 500 || code === 'INTERNAL_ERROR' || /^HTTP_5\d\d$/.test(code)) return 'server'
  if (status === 403) return 'forbidden'
  return 'unknown'
}

/** True when the server may or may not have processed the request (the response was lost or errored). */
export const isResultUnknown = (failure: InviteFailure): boolean => failure === 'network' || failure === 'server'

/** The invitation itself is finished: nothing more can be done with this link. */
export const isTerminalInvitation = (failure: InviteFailure): boolean =>
  failure === 'expired' || failure === 'revoked' || failure === 'redeemed' || failure === 'notFound'

const failureMessages: Record<InviteFailure, string> = {
  expired: '만료된 초대예요. 관리자에게 새 초대를 요청해 주세요.',
  revoked: '철회된 초대예요. 관리자에게 문의해 주세요.',
  redeemed: '이미 사용된 초대예요. 초대는 한 번만 사용할 수 있어요.',
  notFound: '초대를 찾을 수 없어요. 링크를 다시 확인해 주세요.',
  alreadyMember: '기존 소속 또는 연결된 간호사 기록이 있어 가입할 수 없어요. 이 초대는 사용되지 않았어요. 병동 목록에 없다면 관리자에게 소속 상태를 확인해 주세요.',
  nurseLinked: '이미 다른 계정과 연결된 간호사 기록이에요. 관리자에게 문의해 주세요.',
  adminConflict: '이 병동에 연결된 간호사 기록이 있는 계정은 관리자 초대를 수락할 수 없어요.',
  joinMonthRequired: '병원 입사 연월을 입력해 주세요.',
  invalidJoinMonth: '병원 입사 연월이 올바르지 않아요. 미래 연월은 입력할 수 없어요.',
  invalidRequest: '요청 형식이 올바르지 않아요.',
  forbidden: '이 계정으로 초대를 확인하거나 가입할 수 없어요. 관리자에게 계정과 초대를 확인해 주세요.',
  alreadyPending: '이 간호사에게 사용 가능한 초대가 이미 있어요.',
  nurseNotFound: '간호사 기록을 찾을 수 없어요. 목록을 새로 불러와 주세요.',
  userNotFound: '계정 정보를 찾을 수 없어요. 다시 로그인해 주세요.',
  sessionEnded: '로그인이 만료됐어요. 다시 로그인해 주세요.',
  network: '서버에 연결할 수 없어요. 네트워크를 확인해 주세요.',
  server: '서버에 일시적인 문제가 있어요. 잠시 후 다시 시도해 주세요.',
  unknown: '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.',
}

export const inviteFailureMessage = (failure: InviteFailure): string => failureMessages[failure]

/** Admin instructions differ from the invitee's recovery steps. */
export function adminInviteFailureMessage(failure: InviteFailure): string {
  if (failure === 'forbidden') return '이 병동의 관리자 권한이 없어요.'
  if (failure === 'nurseLinked') return '이미 계정과 연결된 간호사 기록이에요. 목록을 새로 불러오고 다른 미가입 간호사를 선택해 주세요.'
  if (failure === 'alreadyMember') return '기존 소속 또는 간호사 연결을 확인하고 초대 대상을 다시 선택해 주세요.'
  return inviteFailureMessage(failure)
}

const restoreNoticeMessages: Record<LoginReturnFailure, string> = {
  readFailed: '초대를 이어가지 못했어요(브라우저 저장소를 읽을 수 없음). 받은 초대 링크를 다시 열거나 붙여넣어 주세요.',
  removeFailed: '초대를 이어가지 못했어요(브라우저 저장소를 정리할 수 없음). 받은 초대 링크를 다시 열거나 붙여넣어 주세요.',
  invalid: '초대를 이어가지 못했어요. 받은 초대 링크를 다시 열거나 붙여넣어 주세요.',
  expired: '로그인하는 데 시간이 걸려 초대 확인이 만료됐어요. 받은 초대 링크를 다시 열거나 붙여넣어 주세요.',
}

export const restoreNoticeMessage = (reason: LoginReturnFailure): string => restoreNoticeMessages[reason]

export const saveFailedMessage =
  '이 브라우저에서는 로그인 중에 초대를 기억해 둘 수 없어요. 로그인한 뒤 받은 초대 링크를 다시 열거나 붙여넣어 주세요.'

const statusLabels: Record<InvitationStatus, string> = {
  PENDING: '사용 대기',
  REDEEMED: '가입 완료',
  REVOKED: '철회됨',
  EXPIRED: '만료됨',
}

export const invitationStatusLabel = (status: InvitationStatus): string => statusLabels[status] ?? '알 수 없음'

export const invitationRoleLabel = (role: InvitationRole): string => (role === 'WARD_ADMIN' ? '관리자' : '간호사')

/** Reissue and revoke are offered only for links that can still be acted on (server decides the rest). */
export const isManageable = (status: InvitationStatus): boolean => status === 'PENDING' || status === 'EXPIRED'

/** Role-based fixed wording (feature-breakdown §7 #7): no free-text memo exists. */
export function invitationTargetLabel(invitation: Invitation, staff: readonly StaffMember[]): string {
  if (invitation.nurseId) {
    const nurse = staff.find((member) => member.nurseId === invitation.nurseId)
    if (!nurse) return '연결된 간호사 기록 · 현재 목록에 없음'
    return `${nurse.displayName} · ${nurse.linkedAccount ? '가입됨' : '미가입'}`
  }
  return invitation.role === 'WARD_ADMIN' ? '관리자 초대 · 대상 미지정' : '새 간호사 초대 · 가입 시 기록 생성'
}

/** Active nurse records that can still be linked: not yet linked to an account and without a pending invitation. */
export function linkableNurses(staff: readonly StaffMember[], invitations: readonly Invitation[]): StaffMember[] {
  const pending = new Set(invitations.filter((item) => item.status === 'PENDING' && item.nurseId).map((item) => item.nurseId))
  return staff.filter((member) => member.active && !member.linkedAccount && !pending.has(member.nurseId))
}

const MONTH_PATTERN = /^(\d{4})-(0[1-9]|1[0-2])$/

/** Client-side pre-check only; the server is the final judge. Returns a failure key or null when acceptable. */
export function validateJoinMonth(value: string): 'joinMonthRequired' | 'invalidJoinMonth' | null {
  const text = value.trim()
  if (text === '') return 'joinMonthRequired'
  const match = MONTH_PATTERN.exec(text)
  if (!match) return 'invalidJoinMonth'
  const year = Number(match[1])
  if (year < 1950) return 'invalidJoinMonth'
  // Whether the month is in the future depends on the ward time zone: the server decides.
  return null
}

export function formatInviteTime(iso: string | undefined): string {
  if (!iso) return ''
  const time = new Date(iso)
  if (Number.isNaN(time.getTime())) return ''
  return `${time.getMonth() + 1}월 ${time.getDate()}일 ${String(time.getHours()).padStart(2, '0')}:${String(time.getMinutes()).padStart(2, '0')}`
}
