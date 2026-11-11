import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router'
import { KakaoCallbackPage } from './pages/KakaoCallbackPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { SessionStatusView } from './pages/SessionStatusView'
import { WardsPage } from './pages/WardsPage'
import { useSessionState } from './session/SessionContext'
import { CALLBACK_PATH, type SessionUser } from './session/SessionManager'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomeRedirect />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path={CALLBACK_PATH} element={<KakaoCallbackPage />} />
      <Route
        path="/wards"
        element={<RequireSession>{(user, epoch) => <WardsPage key={`${user.userId}:${epoch}`} user={user} />}</RequireSession>}
      />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

function HomeRedirect() {
  const state = useSessionState()
  if (state.status === 'signedIn') return <Navigate to="/wards" replace />
  if (state.status === 'signedOut') return <Navigate to="/login" replace />
  return <SessionStatusView />
}

export function RequireSession({ children }: { children: (user: SessionUser, epoch: number) => ReactNode }) {
  const state = useSessionState()
  if (state.status === 'signedIn') return <>{children(state.user, state.epoch)}</>
  if (state.status === 'signedOut') return <Navigate to="/login" replace />
  return <SessionStatusView />
}
