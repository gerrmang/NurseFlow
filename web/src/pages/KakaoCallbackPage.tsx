import { useEffect, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { useSessionManager, useSessionState } from '../session/SessionContext'
import { CALLBACK_PATH } from '../session/SessionManager'
import { SessionStatusView } from './SessionStatusView'

/**
 * Kakao redirects here with ?code&state (or ?error). The parameters are read once and removed
 * from the address bar immediately so the one-time code is not kept in history or re-sent on reload.
 * The exchange itself is serialized with refresh/logout by the SessionManager's lock, and the
 * manager skipped its start-up restore on this route, so restore and exchange cannot race.
 */
export function KakaoCallbackPage() {
  const manager = useSessionManager()
  const state = useSessionState()
  const location = useLocation()
  const navigate = useNavigate()
  const [params] = useState(() => {
    const query = new URLSearchParams(location.search)
    return { code: query.get('code'), state: query.get('state'), error: query.get('error'), hadQuery: location.search !== '' }
  })

  useEffect(() => {
    if (params.hadQuery) navigate(CALLBACK_PATH, { replace: true })
    if (params.code && params.state) void manager.completeLogin(params.code, params.state)
  }, [manager, navigate, params])

  if (params.code && params.state) {
    if (state.status === 'signedIn') return <Navigate to="/wards" replace />
    if (state.status === 'signedOut') return <Navigate to="/login" replace />
    return <SessionStatusView />
  }
  if (params.error) {
    const notice = params.error === 'access_denied' ? 'cancelled' : 'loginFailed'
    return <Navigate to="/login" replace state={{ notice }} />
  }
  // Opened directly without parameters (or reloaded after they were removed).
  return <Navigate to="/" replace />
}
