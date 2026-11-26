import { useEffect, useRef, useState, useSyncExternalStore, type FormEvent } from 'react'
import { Link } from 'react-router'
import { createOffRequestApi, validDate, type Decision, type OffRequest } from '../api/offRequestApi'
import { deadlinePassed, isAdmin, OffRequestStore, ownCount, type OffData } from '../offrequest/OffRequestStore'
import { moveMonth, validMonth } from '../roster/PublishedRosterStore'
import { monthDates } from '../roster/calendar'
import { useSessionManager } from '../session/SessionContext'
import { Brand } from '../ui/Brand'
import { OffCalendar, OffRequestGrid } from '../offrequest/OffRequestViews'
import '../offrequest/off-requests.css'

export const offStatus = { SUBMITTED: '신청됨', CONFIRMED: '확정됨', REJECTED: '반려됨', WITHDRAWN: '철회됨' }
const actionLabel = { confirm: '확정', reject: '반려', withdraw: '철회' }
// A12 returns instants but no ward zone. Explicit browser zone avoids guessing the ward's local date.
function timestamp(value: string) {
  const zone = Intl.DateTimeFormat().resolvedOptions().timeZone
  return `${new Intl.DateTimeFormat('ko-KR', { timeZone: zone, dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))} (${zone})`
}

export function OffRequestsPage({ wardId }: { wardId: string }) {
  const manager = useSessionManager()
  const [store] = useState(() => new OffRequestStore(wardId, createOffRequestApi((path, init) => manager.authorizedFetch(path, init), wardId), manager.api))
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot)
  useEffect(() => { store.activate(); void store.reload(); return () => store.dispose() }, [store])
  const locked = state.loading || state.busy || state.stale || state.uncertain
  return <main className={`page off-page${isAdmin(state.data) ? ' off-page-admin' : ''}`}>
    <aside className="off-sidebar" aria-label="병동 메뉴"><Brand /><Link className="off-ward-switch" to="/wards">{state.data?.ward.hospitalName}<strong>{state.data?.ward.wardName ?? '내 병동'}</strong><span>병동 선택 ▾</span></Link><span className="off-sidebar-current">신청</span></aside>
    <div className="off-main">
    <Link className="off-back" to="/wards">‹ 내 병동으로</Link>
    <header className="toolbar"><div><h1>{isAdmin(state.data) ? '희망 오프 취합' : '희망 오프'}</h1>{state.data && <p className="off-subtitle">{state.data.ward.hospitalName && `${state.data.ward.hospitalName} · `}{state.data.ward.wardName}</p>}</div><button type="button" className="link" onClick={() => void manager.logout()}>로그아웃</button></header>
    <p className="notice off-intro">희망 오프는 승인 연차·반차와 별도예요. 신청만으로 휴무가 보장되지 않으며 관리자가 확정해야 해당 근무 시작일의 배치가 금지됩니다.</p>
    <div className="actions off-month">
      <button type="button" className="secondary" disabled={state.busy || state.uncertain || !validMonth(moveMonth(state.month, -1))} onClick={() => store.selectMonth(moveMonth(state.month, -1))} aria-label="이전 달">‹</button>
      <label className="off-month-picker"><span className="sr-only">대상 월</span><input aria-label="대상 월" type="month" min="0001-01" max="9999-12" value={state.month} disabled={state.busy || state.uncertain} onChange={e => store.selectMonth(e.target.value)} /></label>
      <button type="button" className="secondary" disabled={state.busy || state.uncertain || !validMonth(moveMonth(state.month, 1))} onClick={() => store.selectMonth(moveMonth(state.month, 1))} aria-label="다음 달">›</button>
      <button type="button" disabled={state.busy || state.loading} onClick={() => void store.reload()}>새로고침</button>
    </div>
    {state.loading && <p role="status">희망 오프를 불러오고 있어요…</p>}
    {state.busy && <p role="status">처리 중이에요. 잠시 기다려 주세요.</p>}
    {state.notice && <p role={state.error ? 'alert' : 'status'} className={state.error ? 'error' : 'notice'}>{state.notice}</p>}
    {state.uncertain && <section className="card" aria-label="처리 결과 확인">
      <p>요청이 처리됐을 수 있어요. 최신 목록과 이력에서 결과를 확인한 뒤 편집을 재개해 주세요.</p>
      <button type="button" disabled={state.loading || state.busy || state.stale} onClick={() => store.acknowledge()}>목록에서 결과를 확인했어요</button>
    </section>}
    {state.data && <OffContent key={`${state.month}:${state.version}`} data={state.data} month={state.month} locked={locked} store={store} />}
    </div>
  </main>
}

function OffContent({ data, month, locked, store }: { data: OffData; month: string; locked: boolean; store: OffRequestStore }) {
  const admin = isAdmin(data)
  const [now, setNow] = useState(Date.now)
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer) }, [])
  const late = deadlinePassed(data.list, now)
  const { date, nurseId, filterNurse, filterStatus, exceptions } = store.getSnapshot().draft
  const [pending, setPending] = useState<{ item: OffRequest; action: Decision } | null>(null)
  const confirmation = useRef<HTMLElement>(null)
  useEffect(() => { if (pending) confirmation.current?.focus() }, [pending])
  const [limit, setLimit] = useState(String(data.policy?.value.monthlyLimit ?? ''))
  const [day, setDay] = useState(String(data.policy?.value.deadlineDay ?? ''))
  const nurses = data.eligibility?.items ?? []
  const name = (id: string) => nurses.find(item => item.nurseId === id)?.displayName ?? '현재 목록에 없는 간호사'
  const count = ownCount(data.list.items)
  const list = [...data.list.items].filter(item => (!filterNurse || item.nurseId === filterNurse) && (!filterStatus || item.status === filterStatus) && (!exceptions || item.afterDeadline))
    .sort((a, b) => a.date.localeCompare(b.date) || b.submittedAt.localeCompare(a.submittedAt) || a.requestId.localeCompare(b.requestId))
  const submit = (event: FormEvent) => { event.preventDefault(); void store.submit(date, admin ? nurseId : undefined) }
  const limitValid = /^\d+$/.test(limit) && Number(limit) <= 31
  const dayValid = /^\d+$/.test(day) && Number(day) >= 1 && Number(day) <= 28
  const requestsPanel = <section className="card off-requests" id="off-history" aria-label="희망 오프 목록">
      <div className="off-section-heading"><h2>{admin ? '병동 요청 취합' : '내 신청 내역'}</h2><a className="off-entry-link" href="#off-entry">{admin ? '대리 입력' : '희망 날짜 선택'}</a></div>
      <div className="off-status-summary" aria-label="신청 상태 요약">{Object.entries(offStatus).map(([status, label]) => <span className={`off-badge off-status-${status.toLowerCase()}`} key={status}>{label} {data.list.items.filter(item => item.status === status).length}</span>)}<span className="off-badge off-exception">예외 {data.list.items.filter(item => item.afterDeadline).length}</span></div>
      <div className="staff-grid">
        {admin && <label className="field">간호사 필터<select value={filterNurse} onChange={e => store.updateDraft({ filterNurse: e.target.value })}><option value="">전체</option>{[...new Set(data.list.items.map(item => item.nurseId))].map(id => <option key={id} value={id}>{name(id)}</option>)}</select></label>}
        <label className="field">상태 필터<select value={filterStatus} onChange={e => store.updateDraft({ filterStatus: e.target.value })}><option value="">전체</option>{Object.entries(offStatus).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      </div>
      <label className="check"><input type="checkbox" checked={exceptions} onChange={e => store.updateDraft({ exceptions: e.target.checked })} />마감 후 예외만 보기</label>
      {list.length === 0 && <p role="status">표시할 요청이 없어요.</p>}
      {admin && <OffRequestGrid month={month} items={list} name={name} />}
      <ul className="history-list off-request-list">{list.map(item => <li id={`off-request-${item.requestId}`} key={item.requestId}>
        <strong>{item.date}{admin && ` · ${name(item.nurseId)}`}</strong>
        <span className={`off-request-state off-status-${item.status.toLowerCase()}`}>{offStatus[item.status]} · {item.source === 'ADMIN' ? '관리자 대리 입력' : '간호사 본인 입력'}{item.afterDeadline && ' · 마감 후 예외'}</span>
        <span className="notice">신청 {timestamp(item.submittedAt)}{item.decidedAt && ` · 판단 ${timestamp(item.decidedAt)}`}</span>
        <div className="actions">
          {admin && item.status === 'SUBMITTED' && <><button type="button" disabled={locked} onClick={() => setPending({ item, action: 'confirm' })}>확정</button><button type="button" className="secondary" disabled={locked} onClick={() => setPending({ item, action: 'reject' })}>반려</button></>}
          {(admin ? ['SUBMITTED', 'CONFIRMED'].includes(item.status) : item.status === 'SUBMITTED' && !late) && <button type="button" className="secondary" disabled={locked} onClick={() => setPending({ item, action: 'withdraw' })}>철회</button>}
        </div>
      </li>)}</ul>
    </section>
  const entryPanel = <aside className="off-rail" aria-label={admin ? '대리 입력과 정책' : '희망 날짜 신청'}>
    {pending && <section ref={confirmation} tabIndex={-1} className="card off-confirmation" aria-label="요청 처리 확인">
      <h2>{pending.item.date} {actionLabel[pending.action]} 확인</h2>
      {admin && <p>{name(pending.item.nurseId)}</p>}
      {(pending.action === 'confirm' || pending.item.status === 'CONFIRMED') && <p>해당 월 초안은 재검증 대상이 됩니다. 공개 근무표는 재확정 전까지 유지돼요. 확정 희망 오프는 연속근무를 초기화하는 OF·SOF와 별도예요.</p>}
      <p>최신 상태에 따라 서버가 처리를 거부할 수 있어요.</p>
      <div className="actions"><button type="button" disabled={locked || (!admin && late)} onClick={() => { setPending(null); void store.decide(pending.item.requestId, pending.action) }}>{actionLabel[pending.action]} 실행</button><button type="button" className="secondary" disabled={locked} onClick={() => setPending(null)}>취소</button></div>
    </section>}
    <form className="card off-entry" id="off-entry" aria-label={admin ? '대리 입력' : '본인 신청'} onSubmit={submit}>
      <h2>{admin ? '간호사 대리 입력' : late ? '예외 요청' : '희망 날짜 신청'}</h2>
      <fieldset className="admin-fields" disabled={locked}>
        {admin && <label className="field">대상 간호사<select required value={nurseId} onChange={e => store.updateDraft({ nurseId: e.target.value })}><option value="">선택해 주세요</option>{nurses.filter(item => item.eligible).map(item => <option key={item.nurseId} value={item.nurseId}>{item.displayName}</option>)}</select></label>}
        {admin && data.eligibility?.demoMode && <p className="notice">시연 모드 · 가상 데이터가 편성 대상에 포함됩니다.</p>}
        {admin && !nurses.some(item => item.eligible) && <p>대리 입력 가능한 간호사가 없어요. 팀·구성원 관리에서 확인해 주세요.</p>}
        <OffCalendar month={month} items={data.list.items} selected={date} disabled={locked} onSelect={date => store.updateDraft({ date })} />
        <label className="field off-date-field">희망 날짜<input type="date" required min={`${month}-01`} max={monthDates(month).at(-1)!} value={date} onChange={e => store.updateDraft({ date: e.target.value })} /></label>
        <p className="notice">날짜 변경은 철회 후 다시 신청해 주세요. 확정된 요청과 마감 후 요청의 변경은 관리자에게 문의해 주세요.</p>
        <button disabled={locked || !validDate(date) || !date.startsWith(`${month}-`) || (admin ? !nurses.some(item => item.nurseId === nurseId && item.eligible) : count >= data.list.monthlyLimit)}>{admin ? late ? '예외 대리 접수' : '대리 입력' : late ? '예외 요청 제출' : '신청'}</button>
      </fieldset>
    </form>
    {admin && <form className="card off-policy" aria-label="희망 오프 정책" onSubmit={event => { event.preventDefault(); if (limitValid && dayValid) void store.savePolicy({ monthlyLimit: Number(limit), deadlineDay: Number(day) }) }}>
      <h2>신청 한도·마감일 설정</h2>
      <fieldset className="admin-fields" disabled={locked}>
        <label className="field">월 한도 (0~31일)<input type="number" required min="0" max="31" step="1" value={limit} onChange={e => setLimit(e.target.value)} /></label>
        <label className="field">전달 마감일 (1~28일)<input type="number" required min="1" max="28" step="1" value={day} onChange={e => setDay(e.target.value)} /></label>
        <p className="notice">마감일이 끝나는 병동 현지 자정부터 예외 접수입니다. 변경은 이후 신청·철회에 적용되며 기존 요청과 초안은 변경하지 않아요.</p>
        <button disabled={locked || !limitValid || !dayValid}>정책 저장</button>
      </fieldset>
    </form>}

    </aside>
  return <>
    <section className={`card off-deadline${late ? ' off-deadline-late' : ''}`} aria-label="마감과 한도">
      <div className="off-section-heading"><h2>{Number(month.slice(5))}월 희망 오프</h2><span className="off-badge">{late ? '예외 접수 중' : '신청 접수 중'}</span></div>
      <p>일반 신청 마감: <time dateTime={data.list.deadline}>{timestamp(data.list.deadline)}</time>부터 예외 접수</p>
      <p>{admin ? `간호사 본인 신청 한도: 월 ${data.list.monthlyLimit}일` : `본인 신청 ${count} / ${data.list.monthlyLimit}일`}</p>
      <p className="notice">본인이 입력한 신청·확정 건만 한도에 포함되며 관리자 대리 입력은 제외됩니다. 시각은 표시된 기기 시간대 기준이며 실제 마감·지난 날짜·한도는 서버가 판단해요.</p>
      {late && <p role="status">마감됐어요. 새 신청은 예외 요청이며 간호사 본인 철회는 할 수 없어요. 간호사 예외 요청은 관리자 확정 전에는 편성 선호에도 반영되지 않아요.</p>}
    </section>
    <div className="off-workspace">
      {admin ? <>{requestsPanel}{entryPanel}</> : <>{entryPanel}{requestsPanel}</>}

    </div>
  </>
}
