import type { Invitation, StaffMember } from '../api/types'
import { callbackDestination } from './callbackDestination'
import {
  classifyInviteError,
  adminInviteFailureMessage,
  inviteFailureMessage,
  invitationTargetLabel,
  isResultUnknown,
  isTerminalInvitation,
  linkableNurses,
  validateJoinMonth,
  type InviteFailure,
} from './invitationMessages'

describe('classifyInviteError', () => {
  it.each([
    ['INVITATION_EXPIRED', 410, 'expired'],
    ['INVITATION_REVOKED', 410, 'revoked'],
    ['INVITATION_ALREADY_REDEEMED', 409, 'redeemed'],
    ['INVITATION_NOT_FOUND', 404, 'notFound'],
    ['ALREADY_WARD_MEMBER', 409, 'alreadyMember'],
    ['NURSE_ALREADY_LINKED', 409, 'nurseLinked'],
    ['ADMIN_NURSE_RECORD_CONFLICT', 409, 'adminConflict'],
    ['HOSPITAL_JOIN_MONTH_REQUIRED', 400, 'joinMonthRequired'],
    ['INVALID_HOSPITAL_JOIN_MONTH', 400, 'invalidJoinMonth'],
    ['INVALID_REQUEST', 400, 'invalidRequest'],
    ['INVALID_INVITATION', 400, 'invalidRequest'],
    ['WARD_ADMIN_REQUIRED', 403, 'forbidden'],
    ['WARD_NOT_FOUND', 404, 'forbidden'],
    ['INVITATION_ALREADY_PENDING', 409, 'alreadyPending'],
    ['NURSE_NOT_FOUND', 404, 'nurseNotFound'],
    ['USER_NOT_FOUND', 401, 'userNotFound'],
    ['UNAUTHORIZED', 401, 'sessionEnded'],
    ['SESSION_EXPIRED', 401, 'sessionEnded'],
    ['ABORTED', 0, 'sessionEnded'],
    ['NETWORK', 0, 'network'],
    ['INTERNAL_ERROR', 500, 'server'],
    ['HTTP_502', 502, 'server'],
    ['SOMETHING_NEW', 400, 'unknown'],
    ['HTTP_403', 403, 'forbidden'],
  ])('%s (%i) -> %s', (code, status, expected) => {
    expect(classifyInviteError(code, status)).toBe(expected)
  })

  it('knows which outcomes leave the server result unknown and which end the invitation', () => {
    const unknown: InviteFailure[] = ['network', 'server']
    const terminal: InviteFailure[] = ['expired', 'revoked', 'redeemed', 'notFound']
    for (const failure of unknown) expect(isResultUnknown(failure)).toBe(true)
    for (const failure of terminal) expect(isTerminalInvitation(failure)).toBe(true)
    expect(isResultUnknown('unknown')).toBe(false)
    expect(isResultUnknown('invalidRequest')).toBe(false)
    expect(isTerminalInvitation('alreadyMember')).toBe(false)
  })

  it('has a fixed message for every outcome and never includes a code', () => {
    const all: InviteFailure[] = [
      'expired', 'revoked', 'redeemed', 'notFound', 'alreadyMember', 'nurseLinked', 'adminConflict', 'joinMonthRequired',
      'invalidJoinMonth', 'invalidRequest', 'forbidden', 'alreadyPending', 'nurseNotFound', 'userNotFound', 'sessionEnded',
      'network', 'server', 'unknown',
    ]
    for (const failure of all) {
      const message = inviteFailureMessage(failure)
      expect(message.length).toBeGreaterThan(5)
      expect(message).not.toMatch(/[A-Z_]{6,}/)
    }
  })
})

describe('validateJoinMonth', () => {
  it.each([
    ['', 'joinMonthRequired'],
    ['  ', 'joinMonthRequired'],
    ['2026-12', null],
    ['2027-01', null],
    ['1949-12', 'invalidJoinMonth'],
    ['2021-00', 'invalidJoinMonth'],
    ['2021-13', 'invalidJoinMonth'],
    ['21-03', 'invalidJoinMonth'],
    ['2021/03', 'invalidJoinMonth'],
    ['2026-11', null],
    ['1950-01', null],
    [' 2021-03 ', null],
  ])('%j -> %s', (value, expected) => {
    expect(validateJoinMonth(value)).toBe(expected)
  })
})

describe('labels', () => {
  const nurse = (partial: Partial<StaffMember>): StaffMember =>
    ({ nurseId: 'n1', displayName: '가상 간호사', active: true, linkedAccount: false, ...partial }) as StaffMember
  const invitation = (partial: Partial<Invitation>): Invitation =>
    ({ invitationId: 'i1', wardId: 'w', role: 'WARD_MEMBER', status: 'PENDING', createdAt: '', expiresAt: '', ...partial }) as Invitation

  it('uses fixed role-based wording when no nurse record is linked', () => {
    expect(invitationTargetLabel(invitation({}), [])).toBe('새 간호사 초대 · 가입 시 기록 생성')
    expect(invitationTargetLabel(invitation({ role: 'WARD_ADMIN' }), [])).toBe('관리자 초대 · 대상 미지정')
  })

  it('names the linked nurse record or says it is missing from the list', () => {
    expect(invitationTargetLabel(invitation({ nurseId: 'n1' }), [nurse({})])).toBe('가상 간호사 · 미가입')
    expect(invitationTargetLabel(invitation({ nurseId: 'n1' }), [nurse({ linkedAccount: true })])).toBe('가상 간호사 · 가입됨')
    expect(invitationTargetLabel(invitation({ nurseId: 'n9' }), [nurse({})])).toBe('연결된 간호사 기록 · 현재 목록에 없음')
  })

  it('links only active, unlinked nurses without a pending invitation', () => {
    const staff = [nurse({ nurseId: 'a' }), nurse({ nurseId: 'b', linkedAccount: true }), nurse({ nurseId: 'c', active: false }), nurse({ nurseId: 'd' })]
    const invitations = [
      invitation({ nurseId: 'd', status: 'PENDING' }),
      invitation({ nurseId: 'a', status: 'REVOKED' }),
    ]
    expect(linkableNurses(staff, invitations).map((member) => member.nurseId)).toEqual(['a'])
  })
})

describe('callbackDestination (single decision point)', () => {
  const base = { hasCode: true, error: null }
  it('waits until the login return was read once', () => {
    expect(callbackDestination({ ...base, sessionStatus: 'signedIn', loginReturn: null })).toEqual({ kind: 'wait' })
  })

  it.each([
    ['signedIn', { kind: 'none' }, '/wards'],
    ['signedIn', { kind: 'restored' }, '/invite'],
    ['signedIn', { kind: 'failed', reason: 'expired' }, '/wards'],
    ['signedOut', { kind: 'none' }, '/login'],
    ['signedOut', { kind: 'restored' }, '/invite'],
    ['signedOut', { kind: 'failed', reason: 'invalid' }, '/login'],
  ] as const)('code present, session %s, return %j -> %s', (sessionStatus, loginReturn, to) => {
    expect(callbackDestination({ ...base, sessionStatus, loginReturn })).toEqual({ kind: 'go', to })
  })

  it('waits while the exchange is running', () => {
    expect(callbackDestination({ ...base, sessionStatus: 'exchanging', loginReturn: { kind: 'restored' } })).toEqual({ kind: 'wait' })
  })

  it('error from Kakao keeps the notice and goes to the invitation only when one was restored', () => {
    const input = { hasCode: false, sessionStatus: 'restoring' as const }
    expect(callbackDestination({ ...input, error: 'access_denied', loginReturn: { kind: 'none' } })).toEqual({
      kind: 'go',
      to: '/login',
      state: { notice: 'cancelled' },
    })
    expect(callbackDestination({ ...input, error: 'server_error', loginReturn: { kind: 'restored' } })).toEqual({
      kind: 'go',
      to: '/invite',
      state: { notice: 'loginFailed' },
    })
  })

  it('no parameters at all goes to the home redirect', () => {
    expect(callbackDestination({ hasCode: false, error: null, sessionStatus: 'signedOut', loginReturn: { kind: 'none' } })).toEqual({ kind: 'go', to: '/' })
  })
})


it('gives administrators actionable guidance without referring them to themselves', () => {
  expect(adminInviteFailureMessage('nurseLinked')).toContain('다른 미가입 간호사를 선택')
  expect(adminInviteFailureMessage('forbidden')).toBe('이 병동의 관리자 권한이 없어요.')
  expect(inviteFailureMessage('forbidden')).not.toContain('관리자 권한이 없어요')
})
