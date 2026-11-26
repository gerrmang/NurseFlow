import { useState } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { asApiError } from '../api/http'
import { RestoreNotice } from '../invite/InviteWidgets'
import { cookiesDisabledMessage, errorMessage, noticeMessage } from '../messages'
import { useSessionManager, useSessionState } from '../session/SessionContext'
import { SessionStatusView } from './SessionStatusView'
import { Brand } from '../ui/Brand'
import brandMark from '../assets/brand-mark-white.svg'
import calendarIcon from '../assets/feature-calendar.svg'
import shieldIcon from '../assets/feature-shield.svg'
import peopleIcon from '../assets/feature-people.svg'
import kakaoSymbol from '../assets/kakao-symbol.svg'
import { ShiftChip } from '../ui/ShiftChip'

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
    <main className="page login-page">
      <aside className="login-desktop-brand" aria-label="NurseFlow 소개">
        <div className="desktop-wordmark"><span className="desktop-app-icon"><img src={brandMark} alt="" /></span><span>Nurse<span>Flow</span></span></div>
        <div className="login-benefits"><h2>병동 근무표,<br />규칙 확인은 시스템이 대신해요</h2>
          {[[calendarIcon, '승인된 휴가 · 반차와 Night 다음날 Day 규칙을 자동으로 확인'], [shieldIcon, '초안은 확정 전까지 간호사에게 보이지 않아요'], [peopleIcon, '간호사는 확정된 근무표를 휴대전화와 웹에서 바로 확인']].map(([icon, text]) => <p key={text}><span><img src={icon} alt="" /></span>{text}</p>)}
        </div>
        <div className="login-mini-grid" aria-hidden="true">{['D', 'D', 'E', 'E', 'N', 'OF', 'OF', 'E', 'N', 'N', 'OF', 'D', 'D', 'E', 'N', 'OF', 'D', 'D', 'E', 'E', 'OF'].map((code, index) => <ShiftChip key={index} code={code} />)}</div>
      </aside>
      <div className="login-entry">
      <section className="login-brand" aria-label="NurseFlow 로그인">
        <h1><Brand /></h1>
        <p>병동 근무표를 한곳에서 확인해요</p>
      </section>
      <section className="login-actions" aria-label="로그인">
        <div className="login-desktop-title"><h1>로그인</h1><p>NurseFlow를 쓰려면 로그인해 주세요.</p></div>
        <RestoreNotice />
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
          <button type="button" className="kakao" aria-label={busy ? '카카오로 이동하는 중…' : '카카오로 로그인'} onClick={() => void start()} disabled={busy || cookiesDisabled}>
            <img src={kakaoSymbol} alt="" />
            {busy ? '카카오로 이동하는 중…' : '카카오로 시작하기'}
          </button>
        </div>
        <Link className="login-invite" to="/invite">초대 링크가 있나요? 링크 붙여넣기</Link>
      </section>
      </div>
    </main>
  )
}
