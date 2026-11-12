import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { asApiError, ClientErrorCode } from '../api/http'
import type { CurrentUser, WardMembership } from '../api/types'
import { PasteInviteForm, PendingInviteBanner, RestoreNotice } from '../invite/InviteWidgets'
import { errorMessage, roleLabel } from '../messages'
import { useSessionManager } from '../session/SessionContext'
import type { SessionUser } from '../session/SessionManager'

type LoadState =
  | { status: 'loading' }
  | { status: 'error'; code: string; httpStatus: number }
  | { status: 'ready'; me: CurrentUser; wards: WardMembership[] }

/** Rendered with key=userId:epoch, so a new session always starts from an empty screen state. */
export function WardsPage({ user }: { user: SessionUser }) {
  const manager = useSessionManager()
  const [load, setLoad] = useState<LoadState>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    Promise.all([manager.api.getMe(), manager.api.getWards()]).then(
      ([me, wards]) => {
        if (active) setLoad({ status: 'ready', me, wards: wards.items })
      },
      (error: unknown) => {
        const apiError = asApiError(error)
        // Session ended or replaced: the router leaves this page, nothing to show here.
        if (!active || apiError.code === ClientErrorCode.ABORTED || apiError.code === ClientErrorCode.SESSION_EXPIRED) return
        setLoad({ status: 'error', code: apiError.code, httpStatus: apiError.status })
      },
    )
    return () => {
      active = false
    }
  }, [manager, attempt])

  const reload = () => {
    setLoad({ status: 'loading' })
    setAttempt((value) => value + 1)
  }

  const name = load.status === 'ready' ? load.me.displayName : user.displayName
  // Set by the invitation screen after a successful join; non-sensitive (ward id and name only).
  const joined = (useLocation().state as { joinedWardId?: string; joinedWardName?: string } | null) ?? null

  return (
    <main className="page">
      <header className="toolbar">
        <h1>내 병동</h1>
        <div className="actions">
          <button type="button" className="secondary" onClick={reload} disabled={load.status === 'loading'}>
            새로고침
          </button>
          <button type="button" className="secondary" onClick={() => void manager.logout()}>
            로그아웃
          </button>
        </div>
      </header>
      <p>{name}님, 안녕하세요.</p>
      <RestoreNotice />
      {joined?.joinedWardName && (
        <p role="status" className="notice">
          {joined.joinedWardName} 병동에 가입했어요.
        </p>
      )}
      <PendingInviteBanner />
      {load.status === 'loading' && <p role="status">병동 목록을 불러오고 있어요…</p>}
      {load.status === 'error' && (
        <section className="card">
          <p role="alert" className="error">
            {errorMessage(load.code, load.httpStatus)}
          </p>
          <div className="actions">
            <button type="button" onClick={reload}>
              다시 시도
            </button>
          </div>
        </section>
      )}
      {load.status === 'ready' && load.wards.length === 0 && (
        <section className="card">
          <p>아직 소속된 병동이 없어요.</p>
          <ul className="hints">
            <li>병동 만들기: 준비 중이에요.</li>
            <li>초대 수락: 받은 초대 링크를 열거나 아래에 붙여넣어 가입해요.</li>
          </ul>
        </section>
      )}
      {load.status === 'ready' && (
        <section className="card" aria-label="초대 링크로 가입">
          <h2>초대 링크로 가입</h2>
          <PasteInviteForm />
        </section>
      )}
      {load.status === 'ready' && load.wards.length > 0 && (
        <ul className="ward-list" aria-label="소속 병동">
          {load.wards.map((ward) => (
            <li key={ward.wardId} className={`card${joined?.joinedWardId === ward.wardId ? ' highlight' : ''}`}>
              <div>
                <span className="ward-name">{ward.wardName}</span>
                {ward.hospitalName && <div className="notice">{ward.hospitalName}</div>}
              </div>
              <span className="roles">
                {ward.roles.map((role) => (
                  <span key={role} className="badge">
                    {roleLabel(role)}
                  </span>
                ))}
                {ward.roles.includes('WARD_ADMIN') && (
                  <Link className="button secondary" to={`/wards/${encodeURIComponent(ward.wardId)}/invitations`}>
                    초대 관리
                  </Link>
                )}
              </span>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
