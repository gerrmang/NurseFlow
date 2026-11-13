import type { ReactNode } from 'react'
import { Navigate, Route, Routes, useParams } from 'react-router'
import { InvitationAdminPage } from './pages/InvitationAdminPage'
import { JoinInvitePage } from './pages/JoinInvitePage'
import { KakaoCallbackPage } from './pages/KakaoCallbackPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { SessionStatusView } from './pages/SessionStatusView'
import { WardsPage } from './pages/WardsPage'
import { INVITE_PATH } from './invite/inviteLink'
import { useSessionState } from './session/SessionContext'
import { CALLBACK_PATH, type SessionUser } from './session/SessionManager'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<HomeRedirect />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path={CALLBACK_PATH} element={<KakaoCallbackPage />} />
      <Route path={INVITE_PATH} element={<JoinInvitePage />} />
      <Route
        path="/wards"
        element={<RequireSession>{(user, epoch) => <WardsPage key={`${user.userId}:${epoch}`} user={user} />}</RequireSession>}
      />
      <Route path="/wards/:wardId/invitations" element={<InvitationAdminRoute />} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

function InvitationAdminRoute() {
  const { wardId = '' } = useParams()
  return (
    <RequireSession>
      {(user, epoch) => <InvitationAdminPage key={`${user.userId}:${epoch}:${wardId}`} wardId={wardId} />}
    </RequireSession>
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
