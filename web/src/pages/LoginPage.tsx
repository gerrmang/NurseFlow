import { useState } from 'react'
import { Navigate, useLocation } from 'react-router'
import { asApiError } from '../api/http'
import { cookiesDisabledMessage, errorMessage, noticeMessage } from '../messages'
import { useSessionManager, useSessionState } from '../session/SessionContext'
import { SessionStatusView } from './SessionStatusView'

export function LoginPage() {
  const manager = useSessionManager()
  const state = useSessionState()
  const location = useLocation()
  const [busy, setBusy] = useState(false)
  const [errorCode, setErrorCode] = useState<string | null>(null)

  if (state.status === 'signedIn') return <Navigate to="/wards" replace />
  if (state.status !== 'signedOut') return <SessionStatusView />

  const routeNotice = (location.state as { notice?: string } | null)?.notice
  const notice = routeNotice ?? state.notice
  const cookiesDisabled = typeof navigator !== 'undefined' && navigator.cookieEnabled === false

  const start = async () => {
    setBusy(true)
    setErrorCode(null)
    try {
      await manager.beginKakaoLogin()
      // The page is navigating to Kakao; keep the button disabled.
    } catch (error) {
      setErrorCode(asApiError(error).code)
      setBusy(false)
    }
  }

  return (
    <main className="page">
      <section className="card">
        <h1>NurseFlow</h1>
        <p>카카오로 로그인하고 내 병동을 확인하세요.</p>
        {notice && !errorCode && (
          <p role="status" className="notice">
            {noticeMessage(notice)}
          </p>
        )}
        {errorCode && (
          <p role="alert" className="error">
            {errorMessage(errorCode)}
          </p>
        )}
        {cookiesDisabled && (
          <p role="alert" className="error">
            {cookiesDisabledMessage}
          </p>
        )}
        <div className="actions">
          <button type="button" className="kakao" onClick={() => void start()} disabled={busy || cookiesDisabled}>
            {busy ? '카카오로 이동하는 중…' : '카카오로 로그인'}
          </button>
        </div>
      </section>
    </main>
  )
}
