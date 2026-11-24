import type { ReactNode } from 'react'
import { errorMessage } from '../messages'
import { useSessionManager, useSessionState } from '../session/SessionContext'

/** Shared view for the session states that are neither signed in nor signed out. */
export function SessionStatusView() {
  const manager = useSessionManager()
  const state = useSessionState()
  switch (state.status) {
    case 'restoring':
      return <StatusPanel message="로그인 정보를 확인하고 있어요…" busy />
    case 'exchanging':
      return <StatusPanel message="카카오 로그인을 마무리하고 있어요…" busy />
    case 'signingOut':
      return <StatusPanel message="로그아웃하고 있어요…" busy />
    case 'logoutFailed':
      return (
        <StatusPanel message="로그아웃을 완료하지 못했어요. 이 상태로 새로고침하면 다시 로그인될 수 있으니 로그아웃을 다시 시도해 주세요.">
          <button type="button" onClick={() => void manager.logout()}>
            로그아웃 재시도
          </button>
        </StatusPanel>
      )
    case 'restoreFailed':
      return (
        <StatusPanel message={errorMessage(state.code)}>
          <button type="button" onClick={() => void manager.restore()}>
            다시 시도
          </button>
          <button type="button" className="secondary" onClick={() => void manager.logout()}>
            로그아웃하고 다시 로그인
          </button>
        </StatusPanel>
      )
    default:
      return null
  }
}

function StatusPanel({ message, busy = false, children }: { message: string; busy?: boolean; children?: ReactNode }) {
  return (
    <main className="page">
      <section className="card" aria-busy={busy}>
        <p role={busy ? 'status' : 'alert'}>{message}</p>
        {children && <div className="actions">{children}</div>}
      </section>
    </main>
  )
}
