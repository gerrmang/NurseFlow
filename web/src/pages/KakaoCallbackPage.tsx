import { useEffect, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { callbackDestination } from '../invite/callbackDestination'
import { useInviteStore, useLoginReturn } from '../invite/InviteContext'
import { useSessionManager, useSessionState } from '../session/SessionContext'
import { CALLBACK_PATH } from '../session/SessionManager'
import { SessionStatusView } from './SessionStatusView'

/**
 * Kakao redirects here with ?code&state (or ?error). The parameters are read once and removed
 * from the address bar immediately so the one-time code is not kept in history or re-sent on reload.
 * The exchange itself is serialized with refresh/logout by the SessionManager's lock, and the
 * manager skipped its start-up restore on this route, so restore and exchange cannot race.
 *
 * An invitation saved before the login round trip is read exactly once here (the store remembers
 * the first result, so StrictMode's second effect run cannot overwrite it), and `callbackDestination`
 * alone decides where to go next.
 */
export function KakaoCallbackPage() {
  const manager = useSessionManager()
  const store = useInviteStore()
  const state = useSessionState()
  const loginReturn = useLoginReturn()
  const location = useLocation()
  const navigate = useNavigate()
  const [params] = useState(() => {
    const query = new URLSearchParams(location.search)
    return { code: query.get('code'), state: query.get('state'), error: query.get('error'), hadQuery: location.search !== '' }
  })

  useEffect(() => {
    if (params.hadQuery) navigate(CALLBACK_PATH, { replace: true })
    if (params.code && params.state) void manager.completeLogin(params.code, params.state)
    store.consumeLoginReturn()
  }, [manager, navigate, params, store])

  const destination = callbackDestination({
    hasCode: Boolean(params.code && params.state),
    error: params.error,
    sessionStatus: state.status,
    loginReturn,
  })
  if (destination.kind === 'wait') return <SessionStatusView />
  return <Navigate to={destination.to} replace state={destination.state} />
}
