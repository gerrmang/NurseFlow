import { useEffect, useState, useSyncExternalStore } from 'react'
import { Link } from 'react-router'
import { createPublishedRosterApi } from '../api/publishedRosterApi'
import { errorMessage } from '../messages'
import { monthDates, shiftTime } from '../roster/calendar'
import { moveMonth, PublishedRosterStore, validMonth, type RosterResult } from '../roster/PublishedRosterStore'
import { useSessionManager } from '../session/SessionContext'

export function PublishedRosterPage({ wardId }: { wardId: string }) {
  const manager = useSessionManager()
  const [store] = useState(() => new PublishedRosterStore(wardId, createPublishedRosterApi((path, init) => manager.authorizedFetch(path, init)), manager.api))
  const state = useSyncExternalStore(store.subscribe, store.getState)
  useEffect(() => { void store.load(); return store.stop }, [store])
  return (
    <main className="page roster-page">
      <Link to="/wards">내 병동으로</Link>
      <header className="toolbar">
        <h1>공개 근무표</h1>
        <button type="button" className="secondary" onClick={() => void manager.logout()}>로그아웃</button>
      </header>
      {state.ward && <p>{state.ward.hospitalName && `${state.ward.hospitalName} · `}{state.ward.wardName}</p>}
      <div className="roster-month actions">
        <button type="button" className="secondary" aria-label="이전 달" disabled={!validMonth(moveMonth(state.month, -1))} onClick={() => store.selectMonth(moveMonth(state.month, -1))}>이전 달</button>
        <label>대상 월 <input type="month" min="0001-01" max="9999-12" value={state.month} onChange={event => store.selectMonth(event.target.value)} /></label>
        <button type="button" className="secondary" aria-label="다음 달" disabled={!validMonth(moveMonth(state.month, 1))} onClick={() => store.selectMonth(moveMonth(state.month, 1))}>다음 달</button>
        <button type="button" onClick={() => void store.load()} disabled={state.status === 'loading'}>새로고침</button>
      </div>
      {state.ward && <div className="actions" role="group" aria-label="근무표 보기">
        <button type="button" className="secondary" aria-pressed={state.mode === 'mine'} onClick={() => store.selectMode('mine')}>본인</button>
        {state.ward.canViewWardCalendar && <button type="button" className="secondary" aria-pressed={state.mode === 'ward'} onClick={() => store.selectMode('ward')}>병동 전체</button>}
      </div>}
      {state.status === 'loading' && <p role="status">{state.month} 근무표를 불러오고 있어요…</p>}
      {state.status === 'error' && <section className="card">
        <p role={state.error?.code === 'PUBLISHED_ROSTER_NOT_FOUND' ? 'status' : 'alert'}>{rosterError(state.error!.code, state.error!.status, state.month)}</p>
        {state.error?.code === 'NURSE_IDENTITY_REQUIRED' && state.ward?.canViewWardCalendar && <button type="button" onClick={() => store.selectMode('ward')}>전체 보기</button>}
        <button type="button" className="secondary" onClick={() => void store.load()}>다시 시도</button>
      </section>}
      {state.result && <RosterContent key={`${state.month}:${state.result.mode}`} result={state.result} />}
    </main>
  )
}

function rosterError(code: string, status: number, month: string) {
  if (code === 'PUBLISHED_ROSTER_NOT_FOUND') return `${month} · 아직 확정된 근무표가 없습니다.`
  if (code === 'NURSE_IDENTITY_REQUIRED') return '계정에 연결된 활성 간호사 기록이 없어 본인 근무표를 조회할 수 없습니다. 관리자에게 확인해 주세요.'
  if (code === 'WARD_NOT_FOUND') return '소속 병동을 확인할 수 없습니다. 내 병동 목록에서 소속을 확인해 주세요.'
  if (status === 403) return '이 근무표를 조회할 권한이 없습니다. 내 병동 목록에서 권한을 확인해 주세요.'
  return errorMessage(code, status)
}

function RosterContent({ result }: { result: RosterResult }) {
  const { calendar } = result
  const [selected, setSelected] = useState(`${calendar.month}-01`)
  const items = result.mode === 'mine'
    ? result.calendar.assignments.map((item, i) => ({ id: `mine-${i}`, date: item.date, code: item.shiftType, start: item.startsAt, end: item.endsAt, name: null }))
    : result.calendar.slots.map(item => ({ id: item.slotId, date: item.date, code: item.shiftCode, start: item.startAt, end: item.endAt, name: item.assignment?.displayName ?? '미충원' }))
  const sorted = [...items].sort((a, b) => a.start.localeCompare(b.start) || a.id.localeCompare(b.id))
  return <section aria-label={result.mode === 'mine' ? '본인 공개 근무표' : '병동 전체 공개 근무표'}>
    <p className="notice">{calendar.month} · 병동 시간대 {calendar.timeZone} · 공개 revision {calendar.revision}</p>
    {result.mode === 'mine' && items.length === 0 && <p role="status">이번 달 배정된 근무가 없습니다.</p>}
    {result.mode === 'ward' && <p>필요 {result.calendar.staffing.required} · 배정 {result.calendar.staffing.assigned} · 미충원 {result.calendar.staffing.unfilled}</p>}
    <div className="roster-calendar" aria-label={`${calendar.month} 날짜 선택`}>
      {['일', '월', '화', '수', '목', '금', '토'].map(day => <span className="roster-weekday" key={day}>{day}</span>)}
      {monthDates(calendar.month).map((date, i) => date === null ? <span key={`blank-${i}`} /> : <button type="button" key={date} className="roster-day secondary" aria-label={`${date} 보기`} aria-pressed={date === selected} onClick={() => setSelected(date)}>
        <span>{Number(date.slice(-2))}</span>
        <span className="roster-day-code">{[...new Set(items.filter(item => item.date === date).map(item => item.code))].join(' · ') || '—'}</span>
      </button>)}
    </div>
    <section className="card" aria-label="선택 날짜 근무">
      <h2>{selected}</h2>
      {!items.some(item => item.date === selected) && <p>표시할 근무가 없습니다.</p>}
      <ul className="roster-shifts">{sorted.filter(item => item.date === selected).map(item => <li key={item.id}>
        <strong>{item.code}</strong>{item.name !== null && <span>{item.name}</span>}
        <span>{shiftTime(item.start, item.end, calendar.timeZone)}</span>
      </li>)}</ul>
    </section>
    <details className="card"><summary>월 전체 근무 목록</summary>
      {sorted.length === 0 ? <p>표시할 근무가 없습니다.</p> : <ul className="roster-shifts">{sorted.map(item => <li key={item.id}>
        <time dateTime={item.date}>{item.date}</time><strong>{item.code}</strong>{item.name !== null && <span>{item.name}</span>}<span>{shiftTime(item.start, item.end, calendar.timeZone)}</span>
      </li>)}</ul>}
    </details>
    <p className="notice">근무가 표시되지 않은 날짜는 휴가·오프 여부를 뜻하지 않습니다.</p>
  </section>
}
