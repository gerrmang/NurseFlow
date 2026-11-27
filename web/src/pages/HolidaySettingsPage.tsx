import { useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import { Link } from 'react-router'
import { createHolidaySettingsApi, dayLabels, policyFields, validPolicy, type HolidayDay } from '../api/holidaySettingsApi'
import { HolidaySettingsStore } from '../holiday/HolidaySettingsStore'
import { useSessionManager } from '../session/SessionContext'
import { monthDates } from '../roster/calendar'
import { Brand } from '../ui/Brand'
import '../schedule/schedule-settings.css'
import '../holiday/holiday-settings.css'

export function HolidaySettingsPage({ wardId }: { wardId: string }) {
  const manager = useSessionManager()
  const store = useMemo(() => {
    const now = new Date()
    return new HolidaySettingsStore(createHolidaySettingsApi((p, i) => manager.authorizedFetch(p, i), wardId), () => manager.api.getWards(), wardId, `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`)
  }, [manager, wardId])
  const s = useSyncExternalStore(store.subscribe, store.getSnapshot)
  const [date, setDate] = useState('')
  const [kind, setKind] = useState<HolidayDay['kind']>('WARD_REST_DAY')
  const [name, setName] = useState('')
  const [confirmation, setConfirmation] = useState<{ text: string; action: () => void } | null>(null)
  const panel = useRef<HTMLElement>(null)
  useEffect(() => { store.activate(); void store.reload(); return () => store.dispose() }, [store])
  useEffect(() => { if (confirmation) panel.current?.focus() }, [confirmation])
  const locked = !store.editable() || confirmation !== null
  const dirty = s.calendarDirty || s.policyDirty
  const confirm = (text: string, action: () => void) => setConfirmation({ text, action })
  const reload = (month = s.month) => {
    const action = () => { setDate(''); void store.reload(month) }
    if (dirty) confirm('미저장 달력·목표시간 입력을 버리고 최신 설정을 조회할까요?', action)
    else action()
  }
  const calendar = s.calendar
  const target = calendar?.target
  const minutes = (n: number) => `${Math.floor(n / 60)}시간 ${n % 60}분`
  return <main className="page settings-page holiday-page">
    <aside className="settings-sidebar" aria-label="병동 메뉴"><Brand /><Link to="/wards">내 병동</Link><Link to={`/wards/${encodeURIComponent(wardId)}/schedule-settings`}>근무 설정</Link><span className="settings-current">휴일·목표시간</span></aside>
    <div className="settings-main">
      <header className="toolbar"><div><h1>휴일·목표시간 설정</h1><p>{s.wardName || '병동'} · 관리자 설정</p></div><button className="secondary" disabled={s.loading || s.busy || confirmation !== null} onClick={() => reload()}>최신 설정 조회</button></header>
      <p className="notice">월 목표는 저장된 정책과 달력을 기준으로 서버가 계산해요. 미저장 입력은 화면을 떠나면 사라져요.</p>
      {s.loading && <p role="status">최신 설정 조회 중…</p>}
      {s.notice && <p role="status" className="notice">{s.notice}</p>}
      {s.forbidden && <p role="alert">관리 권한 또는 세션을 확인해 주세요. <Link to="/wards">내 병동</Link></p>}
      {s.stale && calendar && !s.forbidden && <p role="alert">최신 설정 조회 전까지 저장할 수 없어요.</p>}
      {s.uncertain && !s.forbidden && <button disabled={s.stale || s.loading || s.busy} onClick={() => store.acknowledge()}>최신 저장 결과를 확인했어요</button>}
      {confirmation && !s.forbidden && <section className="card confirm" ref={panel} tabIndex={-1} aria-label="휴일 설정 변경 확인"><h2>변경 확인</h2><p>{confirmation.text}</p><div className="actions"><button onClick={() => { const action = confirmation.action; setConfirmation(null); action() }}>확인</button><button className="secondary" onClick={() => setConfirmation(null)}>취소</button></div></section>}
      {calendar && !s.forbidden && <>
        <section className="card"><label className="field">대상 월<input type="month" min="0001-01" max="9999-12" value={s.month} disabled={s.loading || s.busy || s.uncertain || confirmation !== null} onChange={e => reload(e.target.value)} /></label>
          <h2>저장된 월 목표</h2><p className="holiday-total">{minutes(target!.generalTargetMinutes)}</p><p>목표일 {target!.targetDays}일 · 휴식일 {target!.restDays}일 · 하루 {minutes(target!.dailyStandardMinutes)}</p>
          <p>주말 {target!.weekendDays}일 · 평일 공휴일 {target!.holidayDays}일 · 병동 휴무 {target!.wardRestDays}일 · 근무일 전환 {target!.wardWorkdays}일</p>
          {dirty && <p role="status">미저장 변경이 있어요. 위 요약은 저장된 값이에요.</p>}
          <p>{calendar.holidaySource.configured ? '국가 공휴일 자동 갱신 사용' : '자동 공휴일이 꺼져 있어 직접 입력이 필요해요.'}</p>
          <p>마지막 갱신: {calendar.holidaySource.fetchedAt ? new Intl.DateTimeFormat('ko-KR', { timeZone: s.timeZone, dateStyle: 'medium', timeStyle: 'short' }).format(new Date(calendar.holidaySource.fetchedAt)) + ` (${s.timeZone})` : '아직 가져오지 않음'}</p>
          <details><summary>국가 공휴일 갱신</summary><p>임시공휴일 확인 등에 사용해요. 작년~내년만 가능하며, 10분 내 재요청은 저장값을 반환해요.</p><button className="secondary" disabled={locked || !calendar.holidaySource.configured} onClick={() => void store.save('refresh')}>공휴일 지금 가져오기</button></details>
        </section>
        <section className="card"><h2>휴일 달력</h2><p>날짜를 선택해 병동 항목을 편집하세요. 병동 지정이 국가 공휴일·주말보다 우선해요.</p>
          <div className="holiday-grid" aria-label={`${s.month} 휴일 달력`}>{['일', '월', '화', '수', '목', '금', '토'].map(d => <span className="holiday-weekday" key={d}>{d}</span>)}
          {monthDates(s.month).map((d, i) => {
            if (!d) return <span key={`blank${i}`} />
            const ward = s.days.find(v => v.date === d), national = calendar.publicHolidays.find(v => v.date === d)
            const final = ward ?? national
            const label = final ? dayLabels[final.kind] : [0, 6].includes(i % 7) ? '주말' : '평일'
            const description = `${d} · ${label}${ward ? ' · 병동 지정' : national ? ' · 국가 공휴일' : ''}${final?.name ? ` · ${final.name}` : ''}${ward && national ? ` · 국가 공휴일 ${national.name ?? dayLabels[national.kind]}` : ''}`
            return <button key={d} className={`holiday-day ${final?.kind ?? ''}`} aria-label={description} aria-pressed={date === d} disabled={locked} onClick={() => { setDate(d); setKind(ward?.kind ?? 'WARD_REST_DAY'); setName(ward?.name ?? '') }}><strong>{Number(d.slice(-2))}</strong><span>{label}</span>{final?.name && <small>{final.name}</small>}{ward && national && <small>국가: {national.name ?? dayLabels[national.kind]}</small>}</button>
          })}</div>
          {date && <fieldset disabled={locked} className="admin-fields"><legend>{date} 병동 항목</legend><label className="field">날짜 구분<select value={kind} onChange={e => setKind(e.target.value as HolidayDay['kind'])}>{Object.entries(dayLabels).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label><label className="field">공개 휴일 이름 (선택)<input maxLength={50} value={name} onChange={e => setName(e.target.value)} /></label><p>공휴일 이름 등 공개 정보만 입력해 주세요.</p><div className="actions"><button onClick={() => store.editDay({ date, kind, name: name.trim() || null })}>달력에 적용</button><button className="secondary" disabled={!s.days.some(d => d.date === date)} onClick={() => store.editDay({ date, kind }, true)}>병동 지정 삭제</button></div></fieldset>}
          <button disabled={locked || !s.calendarDirty} onClick={() => confirm(`${s.month} 병동 항목 전체를 ${s.days.length}건으로 교체할까요?${s.days.length === 0 ? ' 모든 병동 지정이 삭제돼요.' : ''} 국가 공휴일은 유지돼요.`, () => void store.save('calendar'))}>달력 저장</button>
        </section>
        {s.policy && <section className="card"><h2>목표시간 정책</h2><p>모든 입력은 분 단위예요. Night 전담의 빈 값은 일반 월 목표 또는 근무 유형 환산시간을 사용해요.</p><form onSubmit={e => { e.preventDefault(); if (validPolicy(s.policy)) confirm('병동 목표시간 정책을 저장할까요? 달력의 미저장 편집은 유지해요.', () => void store.save('policy')) }}><fieldset className="admin-fields staff-grid" disabled={locked}>
          {policyFields.map(([key, label, min, max], i) => { const value = s.policy![key]; return <label className="field" key={key}>{label} (분)<input aria-label={`${label} (분)`} type="number" required={i < 4} min={min} max={max} step={1} value={value === null || value === undefined || Number.isNaN(value) ? '' : value} onChange={e => store.editPolicy({ ...s.policy!, [key]: e.target.value === '' ? i >= 4 ? null : NaN : Number(e.target.value) })} /><span>{typeof value === 'number' && Number.isFinite(value) ? minutes(value) : i >= 4 ? '일반 값 사용' : '필수 입력'}</span></label> })}
          </fieldset><button disabled={locked || !s.policyDirty || !validPolicy(s.policy)}>목표시간 정책 저장</button></form></section>}
      </>}
    </div>
  </main>
}
