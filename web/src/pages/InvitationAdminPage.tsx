import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { asApiError, ClientErrorCode } from '../api/http'
import type { CreatedInvitation, Invitation, InvitationRole, InvitationStatus, StaffMember } from '../api/types'
import { buildAppPasteText, buildWebInviteLink, isLocalOrigin } from '../invite/inviteLink'
import {
  classifyInviteError,
  formatInviteTime,
  inviteFailureMessage,
  invitationRoleLabel,
  invitationStatusLabel,
  invitationTargetLabel,
  isManageable,
  isResultUnknown,
  linkableNurses,
  type InviteFailure,
} from '../invite/invitationMessages'
import { errorMessage } from '../messages'
import { useSessionManager } from '../session/SessionContext'

type Action = 'issue' | 'reissue' | 'revoke'

type LoadState =
  | { status: 'loading' }
  | { status: 'error'; code: string; httpStatus: number }
  | { status: 'forbidden' }
  | { status: 'ready'; wardName: string; items: Invitation[]; staff: StaffMember[] }

/** One-time link panel state. Held only in this component's memory: never stored or logged. */
interface IssuedLink {
  invitationId: string
  token: string
  /** What the last successful list read said about this invitation. Only PENDING links may still be copied. */
  availability: InvitationStatus | 'MISSING'
  role: InvitationRole
  reissued: boolean
}

type Reload = 'ok' | 'failed' | 'forbidden' | 'stale'

type Fetched =
  | { kind: 'ready'; wardName: string; items: Invitation[]; staff: StaffMember[] }
  | { kind: 'forbidden' }
  | { kind: 'failed'; code: string; httpStatus: number }
  | { kind: 'ignored' }

const actionDone: Record<Action, string> = {
  issue: '초대 링크를 만들었어요.',
  reissue: '새 링크를 만들었어요. 이전 링크는 철회됐어요.',
  revoke: '초대를 철회했어요.',
}

const actionName: Record<Action, string> = { issue: '초대 발급', reissue: '재발급', revoke: '철회' }

/**
 * `/wards/:wardId/invitations`. Rendered with key=userId:epoch:wardId, so another account or session
 * starts from an empty screen state. The server decides who may do what; the screen only reflects it.
 *
 * Writes are never retried automatically and run one at a time. After every write the list is read
 * again, and the message says exactly what is known: a lost response is described as unknown, and
 * "목록을 새로 불러왔어요" is shown only when that reload really succeeded.
 */
export function InvitationAdminPage({ wardId }: { wardId: string }) {
  const manager = useSessionManager()
  const alive = useRef(true)
  const ticket = useRef(0)
  const busyRef = useRef(false)
  const [load, setLoad] = useState<LoadState>({ status: 'loading' })
  const [stale, setStale] = useState(false)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState<{ text: string; tone: 'info' | 'error' } | null>(null)
  const [issued, setIssued] = useState<IssuedLink | null>(null)
  const [role, setRole] = useState<InvitationRole>('WARD_MEMBER')
  const [nurseId, setNurseId] = useState('')
  const [confirming, setConfirming] = useState<{ invitationId: string; action: 'reissue' | 'revoke' } | null>(null)

  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
    }
  }, [])

  /** Reads wards, invitations and staff. Touches no React state: callers apply the result. */
  const fetchAll = useCallback(async (): Promise<Fetched> => {
    try {
      const wards = await manager.api.getWards()
      const ward = wards.items.find((item) => item.wardId === wardId)
      if (!ward || !ward.roles.includes('WARD_ADMIN')) return { kind: 'forbidden' }
      const [invitations, staff] = await Promise.all([manager.api.listInvitations(wardId), manager.api.listActiveStaff(wardId)])
      return { kind: 'ready', wardName: ward.wardName, items: invitations.items, staff }
    } catch (error) {
      const apiError = asApiError(error)
      if (apiError.code === ClientErrorCode.ABORTED || apiError.code === ClientErrorCode.SESSION_EXPIRED) return { kind: 'ignored' }
      if (classifyInviteError(apiError.code, apiError.status) === 'forbidden') return { kind: 'forbidden' }
      return { kind: 'failed', code: apiError.code, httpStatus: apiError.status }
    }
  }, [manager, wardId])

  const applyFetched = useCallback((result: Fetched): Reload => {
    switch (result.kind) {
      case 'ready':
        setLoad({ status: 'ready', wardName: result.wardName, items: result.items, staff: result.staff })
        setStale(false)
        // A confirmed list is the truth about the link on screen (revoked, used, expired, or gone).
        setIssued((previous) => {
          if (!previous) return previous
          const found = result.items.find((item) => item.invitationId === previous.invitationId)
          const availability = found ? found.status : 'MISSING'
          // A link already known to be unusable is never revived by a list that still says PENDING.
          if (availability === 'PENDING' && previous.availability !== 'PENDING') return previous
          return availability === previous.availability ? previous : { ...previous, availability }
        })
        return 'ok'
      case 'forbidden':
        setLoad({ status: 'forbidden' })
        return 'forbidden'
      case 'failed':
        // Keep the last good list on screen (marked as possibly outdated); only a first load becomes an error screen.
        setLoad((previous) =>
          previous.status === 'ready' ? previous : { status: 'error', code: result.code, httpStatus: result.httpStatus },
        )
        setStale(true)
        return 'failed'
      case 'ignored':
        return 'stale'
    }
  }, [])

  /** Reloads after a write or a manual refresh. A superseded or unmounted request is dropped. */
  const reload = useCallback(async (): Promise<Reload> => {
    const mine = ++ticket.current
    const result = await fetchAll()
    if (!alive.current || mine !== ticket.current) return 'stale'
    return applyFetched(result)
  }, [fetchAll, applyFetched])

  useEffect(() => {
    const mine = ++ticket.current
    void fetchAll().then((result) => {
      if (alive.current && mine === ticket.current) applyFetched(result)
    })
  }, [fetchAll, applyFetched])

  /** `revokedId` is the invitation a revoke targets: a 204 for it is a confirmed result, independent of the reload. */
  const run = async (action: Action, operation: () => Promise<CreatedInvitation | void>, revokedId?: string) => {
    if (busyRef.current) return
    busyRef.current = true
    setBusy(true)
    setNotice(null)
    setConfirming(null)
    let created: CreatedInvitation | null = null
    let failure: InviteFailure | null = null
    try {
      const result = await operation()
      if (result) created = result
    } catch (error) {
      const apiError = asApiError(error)
      failure = classifyInviteError(apiError.code, apiError.status)
    }
    try {
      if (!alive.current || failure === 'sessionEnded') return
      if (failure === 'userNotFound') {
        void manager.logout()
        return
      }
      if (action === 'revoke' && failure === null && revokedId !== undefined) {
        setIssued((previous) =>
          previous && previous.invitationId === revokedId && previous.availability === 'PENDING' ? { ...previous, availability: 'REVOKED' } : previous,
        )
      }
      if (created) {
        // A link that could not be copied or seen again cannot be recovered: only a reissue helps.
        setIssued({
          invitationId: created.invitation.invitationId,
          availability: 'PENDING',
          token: created.token,
          role: created.invitation.role,
          reissued: action === 'reissue',
        })
        if (action === 'issue') setNurseId('')
      }
      const reloaded = await reload()
      if (!alive.current || reloaded === 'stale' || reloaded === 'forbidden') return
      setNotice(composeNotice(action, failure, reloaded === 'ok'))
    } finally {
      busyRef.current = false
      if (alive.current) setBusy(false)
    }
  }

  const issue = () => {
    // Send only what the screen shows: a nurse that is no longer a candidate counts as "not linked".
    const shownNurse =
      load.status === 'ready' && linkableNurses(load.staff, load.items).some((member) => member.nurseId === nurseId) ? nurseId : ''
    const request = role === 'WARD_MEMBER' && shownNurse !== '' ? { role, nurseId: shownNurse } : { role }
    void run('issue', () => manager.api.createInvitation(wardId, request))
  }

  const confirmed = () => {
    if (!confirming) return
    const { invitationId, action } = confirming
    void run(
      action,
      () => (action === 'reissue' ? manager.api.reissueInvitation(wardId, invitationId) : manager.api.revokeInvitation(wardId, invitationId)),
      action === 'revoke' ? invitationId : undefined,
    )
  }

  if (load.status === 'loading') {
    return (
      <Shell wardName={null}>
        <p role="status">초대 목록을 불러오고 있어요…</p>
      </Shell>
    )
  }
  if (load.status === 'forbidden') {
    return (
      <Shell wardName={null}>
        <p role="alert" className="error">
          이 병동의 관리자만 초대를 관리할 수 있어요.
        </p>
        <div className="actions">
          <Link className="button" to="/wards">
            내 병동으로
          </Link>
        </div>
      </Shell>
    )
  }
  if (load.status === 'error') {
    return (
      <Shell wardName={null}>
        <p role="alert" className="error">
          {errorMessage(load.code, load.httpStatus)}
        </p>
        <div className="actions">
          <button type="button" onClick={() => {
              setLoad({ status: 'loading' })
              void reload()
            }}>
            다시 시도
          </button>
        </div>
      </Shell>
    )
  }

  const candidates = linkableNurses(load.staff, load.items)
  // A nurse chosen before a reload may no longer be linkable (someone else invited or linked them).
  const selectedNurse = candidates.some((member) => member.nurseId === nurseId) ? nurseId : ''

  return (
    <Shell wardName={load.wardName}>
      {notice && (
        <p role={notice.tone === 'error' ? 'alert' : 'status'} className={notice.tone === 'error' ? 'error' : 'notice'}>
          {notice.text}
        </p>
      )}
      {stale && (
        <p role="status" className="notice">
          최신 목록이 아닐 수 있어요. 새로고침으로 다시 불러와 주세요.
        </p>
      )}
      {issued && <IssuedLinkPanel key={issued.invitationId} link={issued} onClose={() => setIssued(null)} />}

      <section className="card" aria-label="초대 만들기">
        <h2>새 초대 만들기</h2>
        <div className="field">
          <label htmlFor="invite-role">역할</label>
          <select id="invite-role" value={role} onChange={(event) => setRole(event.target.value as InvitationRole)} disabled={busy}>
            <option value="WARD_MEMBER">간호사</option>
            <option value="WARD_ADMIN">관리자</option>
          </select>
        </div>
        {role === 'WARD_MEMBER' && (
          <div className="field">
            <label htmlFor="invite-nurse">연결할 간호사 기록</label>
            <select id="invite-nurse" value={selectedNurse} onChange={(event) => setNurseId(event.target.value)} disabled={busy}>
              <option value="">연결하지 않음 · 가입 시 새 기록 생성</option>
              {candidates.map((member) => (
                <option key={member.nurseId} value={member.nurseId}>
                  {member.displayName}
                </option>
              ))}
            </select>
          </div>
        )}
        <div className="actions">
          <button type="button" onClick={issue} disabled={busy}>
            {busy ? '처리하는 중…' : '초대 링크 만들기'}
          </button>
        </div>
        <p className="notice">링크는 24시간 동안 한 번만 사용할 수 있어요. 만든 직후에만 복사할 수 있고 다시 볼 수 없어요.</p>
      </section>

      <section aria-label="초대 목록">
        <div className="toolbar">
          <h2>초대 목록</h2>
          <div className="actions">
            <button type="button" className="secondary" onClick={() => void reload()} disabled={busy}>
              새로고침
            </button>
          </div>
        </div>
        <p className="notice">상태는 불러온 시점 기준이에요. 새로고침하면 갱신돼요.</p>
        {load.items.length === 0 && <p>아직 만든 초대가 없어요.</p>}
        <ul className="invite-list">
          {load.items.map((item) => (
            <li key={item.invitationId} className="card">
              <div>
                <span className="ward-name">
                  {invitationStatusLabel(item.status)} · {invitationRoleLabel(item.role)}
                </span>
                <div>{invitationTargetLabel(item, load.staff)}</div>
                <div className="notice">
                  발급 {formatInviteTime(item.createdAt)} · 만료 {formatInviteTime(item.expiresAt)}
                </div>
              </div>
              {isManageable(item.status) && confirming?.invitationId !== item.invitationId && (
                <div className="actions">
                  <button
                    type="button"
                    className="secondary"
                    disabled={busy}
                    onClick={() => setConfirming({ invitationId: item.invitationId, action: 'reissue' })}
                  >
                    재발급
                  </button>
                  <button
                    type="button"
                    className="secondary"
                    disabled={busy}
                    onClick={() => setConfirming({ invitationId: item.invitationId, action: 'revoke' })}
                  >
                    철회
                  </button>
                </div>
              )}
              {confirming?.invitationId === item.invitationId && (
                <div className="confirm" role="group" aria-label={`${actionName[confirming.action]} 확인`}>
                  <p>
                    {confirming.action === 'reissue'
                      ? '새 링크를 만들고 이전 링크는 철회할게요. 계속할까요?'
                      : '이 초대를 철회하면 링크를 더 이상 사용할 수 없어요. 계속할까요?'}
                  </p>
                  <div className="actions">
                    <button type="button" onClick={confirmed} disabled={busy}>
                      {actionName[confirming.action]} 확인
                    </button>
                    <button type="button" className="secondary" onClick={() => setConfirming(null)} disabled={busy}>
                      취소
                    </button>
                  </div>
                </div>
              )}
            </li>
          ))}
        </ul>
      </section>
    </Shell>
  )
}

function composeNotice(action: Action, failure: InviteFailure | null, reloaded: boolean): { text: string; tone: 'info' | 'error' } {
  if (failure === null) {
    return {
      text: reloaded ? actionDone[action] : `${actionDone[action]} 다만 목록을 새로 불러오지 못했어요. 새로고침으로 확인해 주세요.`,
      tone: reloaded ? 'info' : 'error',
    }
  }
  if (isResultUnknown(failure)) {
    const base = `${actionName[action]} 결과를 확인하지 못했어요.`
    if (!reloaded) return { text: `${base} 목록도 불러오지 못했어요. 새로고침으로 결과를 확인해 주세요.`, tone: 'error' }
    if (action === 'revoke') return { text: `${base} 목록을 새로 불러왔어요. 상태를 확인하고 필요하면 다시 철회해 주세요.`, tone: 'error' }
    return {
      text: `${base} 목록을 새로 불러왔어요. 새로 생긴 '사용 대기' 초대가 보이는데 링크를 받지 못했다면 그 초대를 재발급해 주세요. 링크는 다시 볼 수 없어요.`,
      tone: 'error',
    }
  }
  return {
    text: `${inviteFailureMessage(failure)} ${reloaded ? '목록을 새로 불러왔어요.' : '목록을 새로 불러오지 못했어요. 새로고침으로 확인해 주세요.'}`,
    tone: 'error',
  }
}

function Shell({ wardName, children }: { wardName: string | null; children: ReactNode }) {
  return (
    <main className="page">
      <header className="toolbar">
        <h1>초대 관리{wardName ? ` · ${wardName}` : ''}</h1>
        <div className="actions">
          <Link className="button secondary" to="/wards">
            내 병동
          </Link>
        </div>
      </header>
      {children}
    </main>
  )
}

function unusableReason(availability: IssuedLink['availability']): string {
  switch (availability) {
    case 'REVOKED':
      return '철회돼서'
    case 'REDEEMED':
      return '이미 사용돼서'
    case 'EXPIRED':
      return '만료돼서'
    default:
      return '목록에서 확인되지 않아서'
  }
}

/** Shows copy buttons for the freshly issued link. The raw link is shown only if copying fails. */
function IssuedLinkPanel({ link, onClose }: { link: IssuedLink; onClose: () => void }) {
  const [copied, setCopied] = useState<'web' | 'app' | null>(null)
  const [manualText, setManualText] = useState<string | null>(null)
  const origin = window.location.origin
  const webLink = buildWebInviteLink(origin, link.token)

  // The panel is keyed by invitation, so a new link starts with empty copy state. Within one panel only the
  // latest copy attempt may update the screen. A link that is no longer usable renders no copy state at all.
  const attempt = useRef(0)
  const usable = link.availability === 'PENDING'
  const copy = async (kind: 'web' | 'app') => {
    if (!usable) return
    const mine = ++attempt.current
    const text = kind === 'web' ? webLink : buildAppPasteText(link.token)
    try {
      await navigator.clipboard.writeText(text)
      if (mine !== attempt.current) return
      setCopied(kind)
      setManualText(null)
    } catch {
      if (mine !== attempt.current) return
      setCopied(null)
      setManualText(text)
    }
  }

  if (!usable) {
    return (
      <section className="card" aria-label="발급된 초대 링크">
        <h2>
          {link.reissued ? '새 링크' : '초대 링크'} · {invitationRoleLabel(link.role)}
        </h2>
        <p role="status">이 링크는 {unusableReason(link.availability)} 더 이상 복사하거나 사용할 수 없어요. 필요하면 새 초대를 만들어 주세요.</p>
        <div className="actions">
          <button type="button" className="secondary" onClick={onClose}>
            닫기
          </button>
        </div>
      </section>
    )
  }

  return (
    <section className="card" aria-label="발급된 초대 링크">
      <h2>{link.reissued ? '새 링크' : '초대 링크'} · {invitationRoleLabel(link.role)}</h2>
      <p>이 링크는 지금만 복사할 수 있어요. 닫으면 다시 볼 수 없고, 필요하면 재발급해야 해요. 24시간 동안 한 번만 사용할 수 있어요.</p>
      {isLocalOrigin(origin) && (
        <p role="note" className="notice">
          지금 주소({origin})는 이 PC에서만 열려요. 다른 기기에서는 이 링크가 열리지 않아요.
        </p>
      )}
      <div className="actions">
        <button type="button" onClick={() => void copy('web')}>
          웹 링크 복사
        </button>
        <button type="button" className="secondary" onClick={() => void copy('app')}>
          앱 붙여넣기용 복사
        </button>
        <button type="button" className="secondary" onClick={onClose}>
          닫기
        </button>
      </div>
      {copied && (
        <p role="status" className="notice">
          {copied === 'web' ? '웹 링크를 복사했어요.' : '앱 붙여넣기용 링크를 복사했어요.'}
        </p>
      )}
      {manualText !== null && (
        <div className="field">
          <p role="alert" className="error">
            자동으로 복사하지 못했어요. 아래 내용을 직접 복사해 주세요.
          </p>
          <label htmlFor="manual-link">링크</label>
          <input id="manual-link" type="text" readOnly value={manualText} onFocus={(event) => event.target.select()} />
        </div>
      )}
    </section>
  )
}
