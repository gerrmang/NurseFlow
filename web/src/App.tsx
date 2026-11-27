import type { ReactNode } from 'react'
import { Navigate, Route, Routes, useParams } from 'react-router'
import { InvitationAdminPage } from './pages/InvitationAdminPage'
import { JoinInvitePage } from './pages/JoinInvitePage'
import { KakaoCallbackPage } from './pages/KakaoCallbackPage'
import { LoginPage } from './pages/LoginPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { SessionStatusView } from './pages/SessionStatusView'
import { WardsPage } from './pages/WardsPage'
import { PublishedRosterPage } from './pages/PublishedRosterPage'
import { WardCreatePage } from './pages/WardCreatePage'
import { StaffAdminPage } from './pages/StaffAdminPage'
import { OffRequestsPage } from './pages/OffRequestsPage'
import { ScheduleSettingsPage } from './pages/ScheduleSettingsPage'
import { HolidaySettingsPage } from './pages/HolidaySettingsPage'
import { INVITE_PATH } from './invite/inviteLink'
import { useSessionState } from './session/SessionContext'
import { CALLBACK_PATH, type SessionUser } from './session/SessionManager'
import { WardCreationGuard } from './ward/WardCreationGuard'

export function AppRoutes() {
  return (
    <>
      <WardCreationGuard />
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
        <Route path="/wards/new" element={<RequireSession>{(user, epoch) => <WardCreatePage key={`${user.userId}:${epoch}`} epoch={epoch} />}</RequireSession>} />
        <Route path="/wards/:wardId/staff" element={<StaffAdminRoute />} />
        <Route path="/wards/:wardId/off-requests" element={<OffRequestsRoute />} />
        <Route path="/wards/:wardId/schedule-settings" element={<ScheduleSettingsRoute />} />
        <Route path="/wards/:wardId/holiday-settings" element={<HolidaySettingsRoute />} />
        <Route path="/wards/:wardId/published-roster" element={<PublishedRosterRoute />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </>
  )
}

function OffRequestsRoute() {
  const { wardId = '' } = useParams()
  return <RequireSession>{(user, epoch) => <OffRequestsPage key={`${user.userId}:${epoch}:${wardId}`} wardId={wardId} />}</RequireSession>
}

function ScheduleSettingsRoute() {
  const { wardId = '' } = useParams()
  return <RequireSession>{(user, epoch) => <ScheduleSettingsPage key={`${user.userId}:${epoch}:${wardId}`} wardId={wardId} />}</RequireSession>
}

function HolidaySettingsRoute() {
  const { wardId = '' } = useParams()
  return <RequireSession>{(user, epoch) => <HolidaySettingsPage key={`${user.userId}:${epoch}:${wardId}`} wardId={wardId} />}</RequireSession>
}

function PublishedRosterRoute() {
  const { wardId = '' } = useParams()
  return <RequireSession>{(user, epoch) => <PublishedRosterPage key={`${user.userId}:${epoch}:${wardId}`} wardId={wardId} />}</RequireSession>
}

function StaffAdminRoute() {
  const { wardId = '' } = useParams()
  return <RequireSession>{(user, epoch) => <StaffAdminPage key={`${user.userId}:${epoch}:${wardId}`} wardId={wardId} />}</RequireSession>
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
