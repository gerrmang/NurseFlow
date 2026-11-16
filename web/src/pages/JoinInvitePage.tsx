import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { asApiError } from '../api/http'
import type { InvitationPreview } from '../api/types'
import { INVITE_PATH, readInviteFragment } from '../invite/inviteLink'
import {
  classifyInviteError,
  inviteFailureMessage,
  invitationRoleLabel,
  isResultUnknown,
  isTerminalInvitation,
  saveFailedMessage,
  validateJoinMonth,
  formatInviteTime,
  type InviteFailure,
} from '../invite/invitationMessages'
import { useInviteStore, usePendingInvite } from '../invite/InviteContext'
import { PasteInviteForm, RestoreNotice } from '../invite/InviteWidgets'
import type { PendingInvite } from '../invite/pendingInvite'
import { cookiesDisabledMessage, errorMessage, noticeMessage } from '../messages'
import { useSessionManager, useSessionState } from '../session/SessionContext'
import type { SessionUser } from '../session/SessionManager'
import { SessionStatusView } from './SessionStatusView'

/**
 * `/invite`. Steps are kept apart so React StrictMode (double initialization, effects run twice)
 * cannot lose or duplicate the invitation:
 *  1. render: a pure read of location.hash (no navigation, storage or store writes);
 *  2. effect: hand the token to the store (idempotent for the same token) and remove the hash.
 */
export function JoinInvitePage() {
  const store = useInviteStore()
  const state = useSessionState()
  const location = useLocation()
  const navigate = useNavigate()
  const fragment = useMemo(() => readInviteFragment(location.hash), [location.hash])
  // Non-sensitive flag carried by route state; the token itself never is.
  const linkBusy = (location.state as { linkBusy?: boolean } | null)?.linkBusy === true
  const linkInvalid = (location.state as { linkInvalid?: boolean } | null)?.linkInvalid === true

  useEffect(() => {
    const busy = fragment.kind === 'token' && store.offer(fragment.token) === 'busy'
    if (fragment.kind !== 'none') {
      navigate(INVITE_PATH, { replace: true, state: fragment.kind === 'invalid' ? { linkInvalid: true } : busy ? { linkBusy: true } : null })
    }
  }, [fragment, navigate, store])

  const invite = usePendingInvite()

  if (state.status === 'signedIn') {
    if (!invite) return <NoInvite linkInvalid={linkInvalid} signedIn />
    return <JoinFlow key={`${state.user.userId}:${state.epoch}:${invite.seq}`} invite={invite} user={state.user} linkBusy={linkBusy} />
  }
  if (state.status === 'signedOut') {
    if (!invite) return <NoInvite linkInvalid={linkInvalid} signedIn={false} />
    return <AnonymousInvite key={invite.seq} notice={state.notice} />
  }
  return <SessionStatusView />
}

function NoInvite({ linkInvalid, signedIn }: { linkInvalid: boolean; signedIn: boolean }) {
  const navigate = useNavigate()
  return (
    <main className="page">
      <section className="card">
        <h1>초대로 가입</h1>
        <RestoreNotice />
        {linkInvalid && (
          <p role="alert" className="error">
            올바른 초대 링크가 아니에요. 받은 링크를 다시 확인해 주세요.
          </p>
        )}
        <p>새로고침이나 로그인 화면에서 돌아온 뒤 초대가 보이지 않으면, 받은 초대 링크를 다시 열거나 아래에 붙여넣어 주세요.</p>
        <PasteInviteForm />
        <div className="actions">
          <button type="button" className="secondary" onClick={() => navigate(signedIn ? '/wards' : '/login')}>
            {signedIn ? '내 병동으로' : '로그인 화면으로'}
          </button>
        </div>
      </section>
    </main>
  )
}

/** Preview needs a login, so ward details are not shown yet. The login button saves the token for the round trip. */
function AnonymousInvite({ notice }: { notice?: string }) {
  const store = useInviteStore()
  const manager = useSessionManager()
  const location = useLocation()
  const [busy, setBusy] = useState(false)
  const [errorCode, setErrorCode] = useState<string | null>(null)
  const [saveFailed, setSaveFailed] = useState(false)
  const cookiesDisabled = typeof navigator !== 'undefined' && navigator.cookieEnabled === false
  const routeNotice = (location.state as { notice?: string } | null)?.notice
  const shownNotice = routeNotice ?? notice

  const login = async (allowUnsaved: boolean) => {
    if (!allowUnsaved && !store.saveForLogin()) {
      setSaveFailed(true)
      return
    }
    setSaveFailed(false)
    setBusy(true)
    setErrorCode(null)
    try {
      await manager.beginKakaoLogin()
    } catch (error) {
      setErrorCode(asApiError(error).code)
      setBusy(false)
    }
  }

  return (
    <main className="page">
      <section className="card">
        <h1>초대를 받았어요</h1>
        <RestoreNotice />
        <p>로그인하면 초대받은 병동과 역할을 확인하고 가입할 수 있어요.</p>
        {shownNotice && !errorCode && (
          <p role="status" className="notice">
            {noticeMessage(shownNotice)}
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
        {saveFailed && (
          <p role="alert" className="error">
            {saveFailedMessage}
          </p>
        )}
        <div className="actions">
          {saveFailed ? (
            <button type="button" className="kakao" onClick={() => void login(true)} disabled={busy || cookiesDisabled}>
              그래도 카카오로 로그인
            </button>
          ) : (
            <button type="button" className="kakao" onClick={() => void login(false)} disabled={busy || cookiesDisabled}>
              {busy ? '카카오로 이동하는 중…' : '카카오로 로그인'}
            </button>
          )}
          <button type="button" className="secondary" onClick={() => store.discard()} disabled={busy}>
            초대 취소
          </button>
        </div>
      </section>
    </main>
  )
}

type Phase =
  | { kind: 'previewing' }
  | { kind: 'previewError'; failure: InviteFailure }
  | { kind: 'ready'; preview: InvitationPreview; error?: InviteFailure; note?: 'notJoinedStillValid' }
  | { kind: 'submitting'; preview: InvitationPreview }
  | { kind: 'uncertain'; preview: InvitationPreview; checking: boolean; note?: 'checkFailed' | 'conflict' }
  | { kind: 'blocked'; preview: InvitationPreview; failure: InviteFailure }

const uncertainNotes: Record<'checkFailed' | 'conflict' | 'initial', string> = {
  initial: '가입 요청 결과를 확인하지 못했어요. 아래 "결과 확인"으로 가입됐는지 확인해 주세요. 가입 요청을 자동으로 다시 보내지 않아요.',
  checkFailed: '결과를 아직 확인하지 못했어요. 네트워크를 확인하고 "결과 확인"을 다시 눌러 주세요.',
  conflict:
    '이 초대는 사용된 것으로 보이지만 내 병동 목록에서는 확인되지 않았어요. 다시 확인하거나, 계속 문제가 있으면 관리자에게 문의해 주세요.',
}

/**
 * Signed-in flow. Rendered with key=userId:epoch:seq, so a different account, session or invitation
 * always starts from a fresh screen state; results that arrive after that are discarded.
 */
function JoinFlow({ invite, user, linkBusy }: { invite: PendingInvite; user: SessionUser; linkBusy: boolean }) {
  const store = useInviteStore()
  const manager = useSessionManager()
  const navigate = useNavigate()
  const alive = useRef(true)
  const submitting = useRef(false)
  const [phase, setPhase] = useState<Phase>({ kind: 'previewing' })
  const [attempt, setAttempt] = useState(0)
  const [month, setMonth] = useState('')

  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
    }
  }, [])

  const seq = invite.seq
  const current = useCallback(() => alive.current && store.getInvite()?.seq === seq, [store, seq])
  const endAccount = useCallback(() => {
    store.discard()
    void manager.logout()
  }, [store, manager])

  useEffect(() => {
    let active = true
    manager.api.previewInvitation(invite.token).then(
      (preview) => {
        if (active && current()) setPhase({ kind: 'ready', preview })
      },
      (error: unknown) => {
        if (!active || !current()) return
        const failure = classifyInviteError(asApiError(error).code, asApiError(error).status)
        if (failure === 'sessionEnded') return
        if (failure === 'userNotFound') return void endAccount()
        setPhase({ kind: 'previewError', failure })
      },
    )
    return () => {
      active = false
    }
  }, [manager, invite.token, attempt, current, endAccount])

  const finishJoined = (preview: InvitationPreview) => {
    store.discard()
    navigate('/wards', { replace: true, state: { joinedWardId: preview.wardId, joinedWardName: preview.wardName } })
  }

  const cancel = () => {
    store.discard()
    navigate('/wards', { replace: true })
  }

  const submit = async (preview: InvitationPreview) => {
    if (submitting.current) return
    if (preview.role === 'WARD_MEMBER') {
      const problem = validateJoinMonth(month)
      if (problem) {
        setPhase({ kind: 'ready', preview, error: problem })
        return
      }
    }
    submitting.current = true
    store.setRedeeming(invite.seq, true)
    setPhase({ kind: 'submitting', preview })
    // Admin invitations never send hospitalJoinedOn: the server ignores it but still checks its format.
    const body = preview.role === 'WARD_MEMBER' ? { token: invite.token, hospitalJoinedOn: month.trim() } : { token: invite.token }
    try {
      await manager.api.redeemInvitation(body)
      if (current()) finishJoined(preview)
    } catch (error) {
      if (!current()) return
      const apiError = asApiError(error)
      const failure = classifyInviteError(apiError.code, apiError.status)
      if (failure === 'sessionEnded') return
      if (failure === 'userNotFound') return endAccount()
      if (isResultUnknown(failure)) setPhase({ kind: 'uncertain', preview, checking: false })
      else if (failure === 'redeemed') await verifyOutcome(preview)
      else if (failure === 'alreadyMember' || failure === 'nurseLinked' || failure === 'adminConflict') {
        setPhase({ kind: 'blocked', preview, failure })
      } else if (isTerminalInvitation(failure)) setPhase({ kind: 'previewError', failure })
      else setPhase({ kind: 'ready', preview, error: failure })
    } finally {
      submitting.current = false
      store.setRedeeming(invite.seq, false)
    }
  }

  /**
   * After a lost or ambiguous join response: the ward list is the source of truth. A missing ward is
   * not proof the join failed, so the invitation is previewed again before deciding what to tell the user.
   */
  const verifyOutcome = async (preview: InvitationPreview) => {
    setPhase({ kind: 'uncertain', preview, checking: true })
    try {
      const wards = await manager.api.getWards()
      if (!current()) return
      if (wards.items.some((ward) => ward.wardId === preview.wardId)) return finishJoined(preview)
    } catch {
      if (current()) setPhase({ kind: 'uncertain', preview, checking: false, note: 'checkFailed' })
      return
    }
    try {
      const again = await manager.api.previewInvitation(invite.token)
      if (current()) setPhase({ kind: 'ready', preview: again, note: 'notJoinedStillValid' })
    } catch (error) {
      if (!current()) return
      const apiError = asApiError(error)
      const failure = classifyInviteError(apiError.code, apiError.status)
      if (failure === 'sessionEnded') return
      if (failure === 'redeemed') setPhase({ kind: 'uncertain', preview, checking: false, note: 'conflict' })
      else if (isTerminalInvitation(failure)) setPhase({ kind: 'previewError', failure })
      else setPhase({ kind: 'uncertain', preview, checking: false, note: 'checkFailed' })
    }
  }

  return (
    <main className="page">
      <section className="card" aria-busy={phase.kind === 'previewing' || phase.kind === 'submitting'}>
        <h1>초대로 가입</h1>
        <p className="notice">{user.displayName}님으로 로그인되어 있어요.</p>
        {linkBusy && <p role="alert" className="error">다른 초대를 처리하는 중이라 새 링크를 받지 않았어요. 끝난 뒤 새 링크를 다시 열거나 붙여넣어 주세요.</p>}
        {phase.kind === 'previewing' && <p role="status">초대를 확인하고 있어요…</p>}
        {phase.kind === 'previewError' && (
          <>
            <p role="alert" className="error">
              {inviteFailureMessage(phase.failure)}
            </p>
            <div className="actions">
              {isTerminalInvitation(phase.failure) ? (
                <button type="button" onClick={cancel}>
                  닫기
                </button>
              ) : (
                <>
                  <button
                    type="button"
                    onClick={() => {
                      setPhase({ kind: 'previewing' })
                      setAttempt((value) => value + 1)
                    }}
                  >
                    다시 시도
                  </button>
                  <button type="button" className="secondary" onClick={cancel}>
                    초대 취소
                  </button>
                </>
              )}
            </div>
          </>
        )}
        {(phase.kind === 'ready' || phase.kind === 'submitting' || phase.kind === 'uncertain' || phase.kind === 'blocked') && (
          <PreviewSummary preview={phase.preview} />
        )}
        {phase.kind === 'ready' && (
          <>
            {phase.note === 'notJoinedStillValid' && (
              <p role="status" className="notice">
                가입이 확인되지 않았어요. 초대가 아직 유효하니 다시 가입할 수 있어요.
              </p>
            )}
            {phase.preview.role === 'WARD_MEMBER' ? (
              <div className="field">
                <label htmlFor="join-month">병원 입사 연월 (예: 2021-03)</label>
                <input
                  id="join-month"
                  type="text"
                  inputMode="numeric"
                  maxLength={7}
                  value={month}
                  onChange={(event) => setMonth(event.target.value)}
                  autoComplete="off"
                  aria-invalid={phase.error === 'joinMonthRequired' || phase.error === 'invalidJoinMonth'}
                />
              </div>
            ) : (
              <p className="notice">관리자 초대는 입사 연월 입력 없이 가입해요.</p>
            )}
            {phase.error && (
              <p role="alert" className="error">
                {inviteFailureMessage(phase.error)}
              </p>
            )}
            <div className="actions">
              <button type="button" onClick={() => void submit(phase.preview)}>
                가입하기
              </button>
              <button type="button" className="secondary" onClick={cancel}>
                초대 취소
              </button>
            </div>
          </>
        )}
        {phase.kind === 'submitting' && <p role="status">가입하고 있어요…</p>}
        {phase.kind === 'uncertain' && (
          <>
            <p role="alert" className="error">
              {uncertainNotes[phase.note ?? 'initial']}
            </p>
            <div className="actions">
              <button type="button" onClick={() => void verifyOutcome(phase.preview)} disabled={phase.checking}>
                {phase.checking ? '확인하는 중…' : '결과 확인'}
              </button>
              <button type="button" className="secondary" onClick={cancel} disabled={phase.checking}>
                내 병동으로
              </button>
            </div>
          </>
        )}
        {phase.kind === 'blocked' && (
          <>
            <p role="alert" className="error">
              {inviteFailureMessage(phase.failure)}
            </p>
            <div className="actions">
              <button type="button" onClick={cancel}>
                내 병동으로
              </button>
            </div>
          </>
        )}
      </section>
    </main>
  )
}

function PreviewSummary({ preview }: { preview: InvitationPreview }) {
  return (
    <dl className="summary">
      {preview.hospitalName && (
        <>
          <dt>병원</dt>
          <dd>{preview.hospitalName}</dd>
        </>
      )}
      <dt>병동</dt>
      <dd>{preview.wardName}</dd>
      <dt>역할</dt>
      <dd>{invitationRoleLabel(preview.role)}</dd>
      <dt>만료</dt>
      <dd>{formatInviteTime(preview.expiresAt)}</dd>
      {preview.linksExistingNurse && (
        <>
          <dt>안내</dt>
          <dd>관리자가 등록한 간호사 기록과 연결돼요.</dd>
        </>
      )}
    </dl>
  )
}
