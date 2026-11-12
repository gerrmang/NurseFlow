import type { SessionState } from '../session/SessionManager'
import { INVITE_PATH } from './inviteLink'
import type { LoginReturn } from './pendingInvite'

export interface CallbackInput {
  /** Both `code` and `state` were present in the callback URL. */
  hasCode: boolean
  /** The `error` query value, if Kakao reported one. */
  error: string | null
  sessionStatus: SessionState['status']
  /** null until the one-time login-return read has happened. */
  loginReturn: LoginReturn | null
}

export type CallbackDestination = { kind: 'wait' } | { kind: 'go'; to: string; state?: { notice: string } }

/**
 * The single place that decides where the Kakao callback goes next, so the existing /wards and
 * /login redirects and the invitation redirect cannot race each other.
 */
export function callbackDestination(input: CallbackInput): CallbackDestination {
  const { hasCode, error, sessionStatus, loginReturn } = input
  // The stored invitation is read exactly once; nothing is decided before that result exists.
  if (loginReturn === null) return { kind: 'wait' }
  const withInvite = loginReturn.kind === 'restored'
  if (hasCode) {
    if (sessionStatus === 'signedIn') return { kind: 'go', to: withInvite ? INVITE_PATH : '/wards' }
    if (sessionStatus === 'signedOut') return { kind: 'go', to: withInvite ? INVITE_PATH : '/login' }
    return { kind: 'wait' }
  }
  if (error) {
    const notice = error === 'access_denied' ? 'cancelled' : 'loginFailed'
    return { kind: 'go', to: withInvite ? INVITE_PATH : '/login', state: { notice } }
  }
  // Opened directly without parameters (or reloaded after they were removed).
  return { kind: 'go', to: '/' }
}
