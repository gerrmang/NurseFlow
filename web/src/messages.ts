import type { WardRole } from './api/types'

/** All user-facing Korean text for session and error states. Screens branch on codes, never on server text. */
const notices: Record<string, string> = {
  expired: '로그인이 만료됐어요. 다시 로그인해 주세요.',
  loggedOut: '로그아웃했어요.',
  cancelled: '카카오 로그인을 취소했어요.',
  loginFailed: '카카오 로그인에 실패했어요. 잠시 후 다시 시도해 주세요.',
}

export function errorMessage(code: string, status = 0): string {
  switch (code) {
    case 'INVALID_LOGIN_STATE':
      return '로그인 시간이 지났거나 다른 창에서 시작된 로그인이에요. 다시 시도해 주세요.'
    case 'INVALID_AUTHORIZATION_CODE':
    case 'INVALID_ID_TOKEN':
      return notices.loginFailed!
    case 'IDENTITY_PROVIDER_UNAVAILABLE':
      return '카카오 로그인 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.'
    case 'IDENTITY_PROVIDER_NOT_CONFIGURED':
    case 'INVALID_REDIRECT_URI':
      return '웹 로그인 설정이 아직 완료되지 않았어요. 운영자에게 문의해 주세요.'
    case 'WEB_CLIENT_REQUIRED':
      return '접속 주소 설정이 올바르지 않아요. 운영자에게 웹 주소와 프록시 설정 확인을 요청해 주세요.'
    case 'NETWORK':
      return '서버에 연결할 수 없어요. 네트워크와 서버 주소를 확인해 주세요.'
    case 'SESSION_EXPIRED':
    case 'INVALID_REFRESH_TOKEN':
    case 'UNAUTHORIZED':
      return notices.expired!
    case 'FORBIDDEN':
      return '권한이 없어요.'
  }
  if (status >= 500 || code === 'INTERNAL_ERROR' || /^HTTP_5\d\d$/.test(code)) {
    return '서버에 일시적인 문제가 있어요. 잠시 후 다시 시도해 주세요.'
  }
  return '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.'
}

export function noticeMessage(notice: string): string {
  return notices[notice] ?? errorMessage(notice)
}

export const cookiesDisabledMessage = '브라우저 쿠키가 차단되어 있어요. 이 사이트의 쿠키를 허용해야 로그인할 수 있어요.'

const roleLabels: Record<WardRole, string> = {
  WARD_ADMIN: '관리자',
  WARD_MEMBER: '간호사',
}

export function roleLabel(role: WardRole): string {
  return roleLabels[role] ?? role
}
