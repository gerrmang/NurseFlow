import { useEffect, useState, useSyncExternalStore } from 'react'
import { Link } from 'react-router'
import { createPublishedRosterApi, type WardCalendar } from '../api/publishedRosterApi'
import { errorMessage } from '../messages'
import { monthDates, shiftTime } from '../roster/calendar'
import { moveMonth, PublishedRosterStore, validMonth, type RosterResult } from '../roster/PublishedRosterStore'
import { useSessionManager } from '../session/SessionContext'
import { ShiftChip } from '../ui/ShiftChip'
import sidebarMark from '../assets/sidebar-mark.svg'
import rosterIcon from '../assets/nav-roster.svg'

export function PublishedRosterPage({ wardId }: { wardId: string }) {
  const manager = useSessionManager()
  const [store] = useState(() => new PublishedRosterStore(wardId, createPublishedRosterApi((path, init) => manager.authorizedFetch(path, init)), manager.api))
  const state = useSyncExternalStore(store.subscribe, store.getState)
  const [selectedDates, setSelectedDates] = useState<Record<string, string>>({})
  useEffect(() => { void store.load(); return store.stop }, [store])
  return (
    <main className="page roster-page">
      <aside className="roster-sidebar" aria-label="병동 메뉴">
        <div className="desktop-wordmark"><span className="sidebar-app-icon"><img src={sidebarMark} alt="" /></span><span>Nurse<span>Flow</span></span></div>
        <Link className="sidebar-ward" to="/wards">{state.ward?.hospitalName && <span>{state.ward.hospitalName}</span>}<strong>{state.ward?.wardName ?? '내 병동'}</strong><span>병동 선택 ▾</span></Link>
        <div className="sidebar-current"><img src={rosterIcon} alt="" />근무표</div>
      </aside>
      <div className="roster-main">
      <header className="toolbar roster-context">
        <h1 className="sr-only">공개 근무표</h1>
        <Link to="/wards">{state.ward ? `${state.ward.hospitalName ? `${state.ward.hospitalName} · ` : ''}${state.ward.wardName} ▾` : '내 병동으로'}</Link>
        <button type="button" className="link" onClick={() => void manager.logout()}>로그아웃</button>
      </header>
      <div className="roster-controls"><div className="roster-month actions">
        <button type="button" className="month-arrow" aria-label="이전 달" disabled={!validMonth(moveMonth(state.month, -1))} onClick={() => store.selectMonth(moveMonth(state.month, -1))}>‹</button>
        <label className="month-picker"><span className="sr-only">대상 월</span><input aria-label="대상 월" type="month" min="0001-01" max="9999-12" value={state.month} onChange={event => store.selectMonth(event.target.value)} /></label>
        <button type="button" className="month-arrow" aria-label="다음 달" disabled={!validMonth(moveMonth(state.month, 1))} onClick={() => store.selectMonth(moveMonth(state.month, 1))}>›</button>
        <button type="button" className="secondary compact-button" onClick={() => void store.load()} disabled={state.status === 'loading'}>새로고침</button>
      </div>
      {state.ward && <div className="actions segmented" role="group" aria-label="근무표 보기">
        <button type="button" className="secondary" aria-pressed={state.mode === 'mine'} onClick={() => store.selectMode('mine')}>본인</button>
        {state.ward.canViewWardCalendar && <button type="button" className="secondary" aria-pressed={state.mode === 'ward'} onClick={() => store.selectMode('ward')}>병동 전체</button>}
      </div>}
      </div>
      {state.status === 'loading' && <p role="status">{state.month} 근무표를 불러오고 있어요…</p>}
      {state.status === 'error' && <section className="card">
        <p role={state.error?.code === 'PUBLISHED_ROSTER_NOT_FOUND' ? 'status' : 'alert'}>{rosterError(state.error!.code, state.error!.status, state.month)}</p>
        {state.error?.code === 'NURSE_IDENTITY_REQUIRED' && state.ward?.canViewWardCalendar && <button type="button" onClick={() => store.selectMode('ward')}>전체 보기</button>}
        <button type="button" className="secondary" onClick={() => void store.load()}>다시 시도</button>
      </section>}
      {state.result && <RosterContent result={state.result} selected={selectedDates[state.month] ?? `${state.month}-01`} onSelect={date => setSelectedDates(current => ({ ...current, [state.month]: date }))} />}
      </div>
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

function RosterContent({ result, selected, onSelect }: { result: RosterResult; selected: string; onSelect: (date: string) => void }) {
  const { calendar } = result
  const items = result.mode === 'mine'
    ? result.calendar.assignments.map((item, i) => ({ id: `mine-${i}`, date: item.date, code: item.shiftType, start: item.startsAt, end: item.endsAt, name: null }))
    : result.calendar.slots.map(item => ({ id: item.slotId, date: item.date, code: item.shiftCode, start: item.startAt, end: item.endAt, name: item.assignment?.displayName ?? '미충원' }))
  const sorted = [...items].sort((a, b) => a.start.localeCompare(b.start) || a.id.localeCompare(b.id))
  return <section aria-label={result.mode === 'mine' ? '본인 공개 근무표' : '병동 전체 공개 근무표'}>
    <details className="roster-metadata"><summary>{calendar.month} 공개 근무표 · 공개 정보</summary><p className="notice">{calendar.month} · 병동 시간대 {calendar.timeZone} · 공개 revision {calendar.revision}</p></details>
    {result.mode === 'mine' && items.length === 0 && <p role="status">이번 달 배정된 근무가 없습니다.</p>}
    {result.mode === 'ward' && <p>필요 {result.calendar.staffing.required} · 배정 {result.calendar.staffing.assigned} · 미충원 {result.calendar.staffing.unfilled}</p>}
    <div className={`roster-workspace roster-workspace-${result.mode}`}>
    <div className={result.mode === 'ward' ? 'mobile-roster-calendar' : ''}>
    <div className="roster-calendar" aria-label={`${calendar.month} 날짜 선택`}>
      {['일', '월', '화', '수', '목', '금', '토'].map(day => <span className="roster-weekday" key={day}>{day}</span>)}
      {monthDates(calendar.month).map((date, i) => date === null ? <span key={`blank-${i}`} /> : <button type="button" key={date} className="roster-day" aria-label={`${date} 보기`} aria-pressed={date === selected} onClick={() => onSelect(date)}>
        <span>{Number(date.slice(-2))}</span>
        <span className="roster-day-code">{items.some(item => item.date === date) ? [...new Set(items.filter(item => item.date === date).map(item => item.code))].map(code => <ShiftChip key={code} code={code} />) : '—'}</span>
        {result.mode === 'mine' && <span className="desktop-shift-times">{items.filter(item => item.date === date).map(item => <span key={item.id}>{shiftTime(item.start, item.end, calendar.timeZone)}</span>)}</span>}
      </button>)}
    </div>
    </div>
    {result.mode === 'ward' && <WardMonthTable calendar={result.calendar} selected={selected} onSelect={onSelect} />}
    <aside className="roster-day-panel">
    <section className="card" aria-label="선택 날짜 근무">
      <h2>{selected}</h2>
      {!items.some(item => item.date === selected) && <p>표시할 근무가 없습니다.</p>}
      <ul className="roster-shifts">{sorted.filter(item => item.date === selected).map(item => <li key={item.id}>
        <ShiftChip code={item.code} />{item.name !== null && <span className="person-chip">{item.name}</span>}
        <span>{shiftTime(item.start, item.end, calendar.timeZone)}</span>
      </li>)}</ul>
    </section>
    {result.mode === 'mine' && <section className="card roster-counts" aria-label="월 배정 건수"><h2>{Number(calendar.month.slice(-2))}월 배정</h2>{[...new Set(items.map(item => item.code))].map(code => <p key={code}><ShiftChip code={code} /><span>{items.filter(item => item.code === code).length}건</span></p>)}</section>}
    <p className="notice roster-note">근무가 표시되지 않은 날짜는 휴가·오프 여부를 뜻하지 않습니다.</p>
    </aside>
    </div>
    <details className="card"><summary>월 전체 근무 목록</summary>
      {sorted.length === 0 ? <p>표시할 근무가 없습니다.</p> : <ul className="roster-shifts">{sorted.map(item => <li key={item.id}>
        <time dateTime={item.date}>{item.date}</time><ShiftChip code={item.code} />{item.name !== null && <span>{item.name}</span>}<span>{shiftTime(item.start, item.end, calendar.timeZone)}</span>
      </li>)}</ul>}
    </details>
  </section>
}

function WardMonthTable({ calendar, selected, onSelect }: { calendar: WardCalendar; selected: string; onSelect: (date: string) => void }) {
  const dates = monthDates(calendar.month).filter(date => date !== null)
  const nurses = new Map<string, { name: string; days: Map<string, WardCalendar['slots']> }>()
  for (const slot of calendar.slots) {
    if (!slot.assignment) continue
    const { nurseId, displayName } = slot.assignment
    const nurse = nurses.get(nurseId) ?? { name: displayName, days: new Map<string, WardCalendar['slots']>() }
    nurse.days.set(slot.date, [...(nurse.days.get(slot.date) ?? []), slot])
    nurses.set(nurseId, nurse)
  }
  return <div className="desktop-ward-grid" tabIndex={0} aria-label="병동 월간 근무표 가로 스크롤">
    <table>
      <thead><tr><th scope="col">간호사</th>{dates.map(date => <th key={date} scope="col">
        <button type="button" aria-label={`${date} 선택`} aria-pressed={date === selected} onClick={() => onSelect(date)}>{Number(date.slice(-2))}</button>
      </th>)}</tr></thead>
      <tbody>{[...nurses].map(([id, nurse]) => <tr key={id}>
        <th scope="row">{nurse.name}</th>{dates.map(date => <td key={date} className={date === selected ? 'selected-column' : ''}>
          {nurse.days.get(date)?.map(slot => <ShiftChip key={slot.slotId} code={slot.shiftCode} />)}
        </td>)}
      </tr>)}</tbody>
    </table>
    <p className="notice">공개된 근무 배정이 있는 간호사만 표시합니다.</p>
  </div>
}
