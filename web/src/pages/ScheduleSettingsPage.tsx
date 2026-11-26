import { useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import { Link } from 'react-router'
import { createScheduleSettingsApi, type Shift, type ShiftInput, type Preview, type Requirement, type Absence, type Pattern } from '../api/scheduleSettingsApi'
import { createStaffAdminApi } from '../api/staffAdminApi'
import { ScheduleSettingsStore, type SettingsData } from '../schedule/ScheduleSettingsStore'
import { compareInstants, localTime, toInstant } from '../schedule/timeZone'
import { useSessionManager } from '../session/SessionContext'
import { monthDates } from '../roster/calendar'
import { Brand } from '../ui/Brand'
import { ShiftCatalog, DemandSummary } from '../schedule/ScheduleViews'
import '../schedule/schedule-settings.css'

type Confirm = (label: string, text: string, operation: () => Promise<unknown>, preview?: Preview) => void
type Run = (label: string, operation: () => Promise<unknown>) => Promise<boolean>
const patterns: Record<Pattern['workPattern'], string> = { GENERAL: '일반', DAY_EVENING_ONLY: 'D/E 전담', NIGHT_ONLY: 'N 전담' }

export function ScheduleSettingsPage({ wardId }: { wardId: string }) {
  const manager = useSessionManager()
  const store = useMemo(() => {
    const fetch = (path: string, init?: RequestInit) => manager.authorizedFetch(path, init)
    const now = new Date()
    const month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
    return new ScheduleSettingsStore(createScheduleSettingsApi(fetch, wardId), createStaffAdminApi(fetch, wardId), () => manager.api.getWards(), wardId, month)
  }, [manager, wardId])
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot)
  const [tab, setTab] = useState('shifts')
  const [confirmation, setConfirmation] = useState<{ label: string; text: string; operation: () => Promise<unknown>; preview?: Preview } | null>(null)
  const confirmationPanel = useRef<HTMLElement>(null)
  useEffect(() => { if (confirmation) confirmationPanel.current?.focus() }, [confirmation])
  useEffect(() => { store.activate(); void store.reload(); return () => store.dispose() }, [store])
  const confirm: Confirm = (label, text, operation, preview) => setConfirmation({ label, text, operation, preview })
  const run: Run = (label, operation) => store.run(label, operation)
  const data = state.data
  const locked = state.loading || state.busy || state.stale || state.uncertain || state.forbidden || confirmation !== null
  return <main className="page settings-page">
    <aside className="settings-sidebar" aria-label="병동 메뉴"><Brand /><Link className="settings-ward" to="/wards"><strong>{data?.wardName ?? '내 병동'}</strong><span>병동 선택 ▾</span></Link><span className="settings-current">설정</span></aside>
    <div className="settings-main">
    <header className="toolbar"><div><h1>근무 설정</h1><p className="settings-subtitle">{data?.wardName ?? '병동'} · 근무 유형과 편성 기준</p></div><div className="actions"><Link to="/wards">내 병동</Link><button className="secondary" disabled={state.loading || state.busy || confirmation !== null} onClick={() => void store.reload()}>새로고침</button></div></header>
    <p className="notice">입력한 설정은 서버가 검증해요. 변경 후 초안 재검증이 필요할 수 있고, 공개본은 재확정 전까지 유지돼요.</p>
    {state.loading && <p role="status">최신 설정을 불러오고 있어요…</p>}
    {state.notice && <p role="status" className="notice">{state.notice}</p>}
    {state.forbidden && <p role="alert">관리 권한 또는 세션이 변경됐어요. <Link to="/wards">내 병동에서 다시 확인해 주세요.</Link></p>}
    {state.stale && data && !state.forbidden && <p role="alert">이전 조회 결과예요. 최신 설정을 다시 불러올 때까지 편집할 수 없어요.</p>}
    {state.uncertain && !state.forbidden && <section className="card" aria-label="저장 결과 확인"><p>이미 처리됐을 수 있어요. 선택한 월의 초안과 해당 설정을 확인해 주세요.</p><button disabled={state.loading || state.busy || state.stale} onClick={() => store.acknowledge()}>최신 결과를 확인했어요</button></section>}
    {confirmation && !state.forbidden && <section ref={confirmationPanel} tabIndex={-1} className="card confirm" aria-label="설정 변경 확인"><h2>{confirmation.label}</h2><p>{confirmation.text}</p>
      {confirmation.preview && <div><p>재검증 대상 초안 {confirmation.preview.affectedDrafts.length}개 · 칸 시각 재계산 {confirmation.preview.rewritesDraftSlotTimes ? '있음' : '없음'}</p><ul>{confirmation.preview.affectedDrafts.map(d => <li key={d.rosterId}>{d.month} · 해당 근무 {d.slots}칸 · 배치 {d.assignedSlots}칸</li>)}</ul><p>공개 당시 값을 유지하는 근무표 {confirmation.preview.publishedRostersUsingType}개</p></div>}
      <div className="actions"><button disabled={state.busy || state.loading || state.stale || state.uncertain} onClick={() => { const action = confirmation; setConfirmation(null); void run(action.label, action.operation) }}>확인하고 저장</button><button className="secondary" onClick={() => setConfirmation(null)}>취소</button></div>
    </section>}
    {data && !state.forbidden && <>
      <nav className="settings-tabs" aria-label="근무 설정 메뉴">{[['shifts', '근무 유형'], ['patterns', '근무 형태'], ['demand', '팀별 필요 인원'], ['absences', '승인 VY·반차']].map(([id, label]) => <button key={id} className="secondary" aria-pressed={tab === id} disabled={state.busy || confirmation !== null} onClick={() => setTab(id!)}>{label}</button>)}</nav>
      {(tab === 'demand' || tab === 'absences') && <section className="card"><label className="field">대상 월<input aria-label="대상 월" type="month" value={state.month} min="0001-01" max="9999-12" disabled={state.busy || state.loading || state.uncertain || confirmation !== null} onChange={e => void store.setMonth(e.target.value)} /></label>
        {data.draft ? <p>초안 revision {data.draft.value.revision} · {data.draft.value.timeZone}{data.draft.value.needsRevalidation ? ' · 재검증 필요' : ''}</p> : <><p>이 달의 수정 가능한 초안이 없어요.</p><button disabled={locked} onClick={() => confirm('초안 생성', `${state.month} 초안을 만들까요? 기존 공개본은 유지해요.`, () => store.api.createRoster(state.month, crypto.randomUUID()))}>이 달 초안 만들기</button></>}
      </section>}
      <fieldset className="admin-fields" disabled={locked}><legend className="sr-only">근무 설정 입력</legend>
        {tab === 'shifts' && <ShiftSettings key={`shifts:${state.version}`} data={data} store={store} run={run} confirm={confirm} />}
        {tab === 'patterns' && <PatternSettings version={state.version} data={data} store={store} confirm={confirm} />}
        {tab === 'demand' && data.draft && <DemandSettings key={`demand:${state.version}`} data={data} store={store} confirm={confirm} />}
        {tab === 'absences' && data.draft && <AbsenceSettings key={`absences:${state.version}`} data={data} store={store} confirm={confirm} />}
      </fieldset>
    </>}
    </div>
  </main>
}

function ShiftSettings({ data, store, run, confirm }: { data: SettingsData; store: ScheduleSettingsStore; run: Run; confirm: Confirm }) {
  const [id, setId] = useState('')
  const selected = data.shifts.value.items.find(s => s.shiftTypeId === id)
  const deactivate = async (shift: Shift) => {
    // Legacy OTHER cannot be submitted to PUT/preview; DELETE is supported.
    const preview = shift.kind === 'OTHER' ? undefined : await store.preview(shift.shiftTypeId, shiftInput(shift, false))
    if (preview === null) return
    confirm('근무 유형 비활성화', `${shift.code}를 새 수요에서 사용할 수 없게 해요. 기존 초안은 재검증 대상이 되며 공개본은 유지해요.`, () => store.api.deactivateShift(shift.shiftTypeId, data.shifts.etag), preview)
  }
  return <section className="card settings-shifts"><h2>근무 유형</h2><p className="notice">등록된 유형을 선택해 시간과 환산 기준을 확인해 주세요.</p><div className="settings-shift-layout">
    <ShiftCatalog shifts={data.shifts.value.items} selected={id} onSelect={setId} />
    <div className="settings-shift-editor">
    <label className="field">편집할 근무 유형<select value={id} onChange={e => setId(e.target.value)}><option value="">새 근무 유형</option>{data.shifts.value.items.map(s => <option value={s.shiftTypeId} key={s.shiftTypeId}>{s.code} · {s.displayName}{s.active ? '' : ' · 비활성'}</option>)}</select></label>
    {selected?.kind === 'OTHER' ? <p>기존 특수 유형은 조회와 비활성화만 지원해요. {selected.startTime}~{selected.endTime}{selected.spansMidnight ? ' (다음 날)' : ''} · 휴게 {selected.breakMinutes}분 · 인정 {selected.recognizedMinutes}분 · 환산 {selected.targetCreditMinutes}분</p> : <ShiftForm key={id} shift={selected} submit={async input => {
      if (!selected) { await run('근무 유형 생성', () => store.api.createShift(input, data.shifts.etag, crypto.randomUUID())); return }
      const preview = await store.preview(selected.shiftTypeId, input)
      if (preview) confirm('근무 유형 변경', '영향받는 초안을 확인하고 저장해 주세요.', () => store.api.updateShift(selected.shiftTypeId, input, data.shifts.etag), preview)
    }} />}
    {selected?.active && <button className="secondary" onClick={() => void deactivate(selected)}>선택 유형 비활성화</button>}
    </div></div>
  </section>
}
function shiftInput(shift: Shift, active = shift.active): ShiftInput {
  if (shift.kind === 'OTHER') throw new Error('Unsupported legacy kind')
  return { code: shift.code, displayName: shift.displayName, kind: shift.kind, startTime: shift.startTime, endTime: shift.endTime, spansMidnight: shift.spansMidnight, breakMinutes: shift.breakMinutes, recognizedMinutes: shift.recognizedMinutes, targetCreditMinutes: shift.targetCreditMinutes, active }
}
function ShiftForm({ shift, submit }: { shift?: Shift; submit: (input: ShiftInput) => Promise<void> }) {
  const [input, setInput] = useState<ShiftInput>(() => shift ? shiftInput(shift) : { code: '', displayName: '', kind: 'DAY', startTime: '07:00', endTime: '15:00', spansMidnight: false, breakMinutes: 0, recognizedMinutes: 480, targetCreditMinutes: 480, active: true })
  const [error, setError] = useState('')
  const text = (key: 'code' | 'displayName' | 'startTime' | 'endTime', label: string, type = 'text') => <label className="field">{label}<input required type={type} value={input[key]} maxLength={key === 'code' ? 32 : key === 'displayName' ? 64 : undefined} pattern={key === 'code' ? '[A-Z0-9_]{1,32}' : undefined} onChange={e => setInput({ ...input, [key]: e.target.value })} /></label>
  return <form onSubmit={e => { e.preventDefault(); const minutes = (s: string) => Number(s.slice(0, 2)) * 60 + Number(s.slice(3)); const duration = minutes(input.endTime) - minutes(input.startTime) + (input.spansMidnight ? 1440 : 0)
    if (!input.displayName.trim() || input.spansMidnight !== (input.endTime < input.startTime) || duration <= 0 || (input.breakMinutes ?? 0) >= duration || (input.recognizedMinutes ?? 0) > duration - (input.breakMinutes ?? 0)) { setError('이름·시작/종료·자정 경과·휴게·인정 시간을 확인해 주세요.'); return }
    setError(''); void submit({ ...input, displayName: input.displayName.trim() })
  }}><div className="staff-grid">{text('code', '근무 코드')}{text('displayName', '표시 이름')}
    <label className="field">근무 종류<select value={input.kind} onChange={e => setInput({ ...input, kind: e.target.value as ShiftInput['kind'] })}><option value="DAY">Day</option><option value="EVENING">Evening</option><option value="NIGHT">Night</option></select></label>
    {text('startTime', '시작 시각', 'time')}{text('endTime', '종료 시각', 'time')}
    {([['breakMinutes', '휴게 시간(분)', 0, 240], ['recognizedMinutes', '실제 인정시간(분)', 1, 1440], ['targetCreditMinutes', '월 환산시간(분)', 0, 1440]] as const).map(([key, label, min, max]) => <label className="field" key={key}>{label}<input required type="number" min={min} max={max} step={1} value={input[key] ?? ''} onChange={e => setInput({ ...input, [key]: e.target.value === '' ? undefined : Number(e.target.value) })} /></label>)}
    </div><label className="check"><input type="checkbox" checked={input.spansMidnight} onChange={e => setInput({ ...input, spansMidnight: e.target.checked })} />종료는 다음 날</label>
    {shift && <label className="check"><input type="checkbox" checked={input.active} onChange={e => setInput({ ...input, active: e.target.checked })} />활성 (비활성 유형은 체크하여 재활성화)</label>}
    {error && <p role="alert" className="error">{error}</p>}<button>{shift ? '변경 영향 확인' : '근무 유형 생성'}</button>
  </form>
}

function PatternSettings({ data, store, confirm, version }: { data: SettingsData; store: ScheduleSettingsStore; confirm: Confirm; version: number }) {
  const [id, setId] = useState('')
  return <section className="card"><h2>간호사 근무 형태</h2><p className="notice">근무 형태 변경은 모든 초안을 재검증 대상으로 표시해요. 동시 편집 시 마지막 저장이 반영돼요. Night 전담 기간·순환·최소 횟수 설정은 후속 기능이에요.</p>
    <label className="field">간호사<select value={id} onChange={e => setId(e.target.value)}><option value="">선택해 주세요</option>{data.staff.map(n => <option key={n.nurseId} value={n.nurseId}>{n.displayName}{n.active ? '' : ' · 비활성'}</option>)}</select></label>
    {id && <PatternEditor key={`${id}:${version}`} id={id} store={store} confirm={confirm} />}
  </section>
}
function PatternEditor({ id, store, confirm }: { id: string; store: ScheduleSettingsStore; confirm: Confirm }) {
  const [value, setValue] = useState<Pattern['workPattern'] | ''>('')
  const [error, setError] = useState(false)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => { let active = true; store.api.pattern(id).then(p => { if (active) { setValue(p.workPattern); setError(false) } }, e => { if (active) { setError(true); store.readDenied(e) } }); return () => { active = false } }, [id, store, attempt])
  return <>{error && <div><p role="alert">근무 형태를 불러오지 못했어요. 저장할 수 없어요.</p><button className="secondary" onClick={() => { setValue(''); setError(false); setAttempt(attempt + 1) }}>다시 조회</button></div>}
    {!value && !error && <p role="status">근무 형태 조회 중…</p>}
    {value && !error && <><label className="field">근무 형태<select value={value} onChange={e => setValue(e.target.value as Pattern['workPattern'])}>{Object.entries(patterns).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label><button onClick={() => confirm('근무 형태 저장', `${patterns[value]}으로 저장하고 모든 초안을 재검증 대상으로 표시해요. 공개본은 유지해요.`, () => store.api.savePattern(id, { workPattern: value }))}>근무 형태 저장</button></>}
  </>
}

function DemandSettings({ data, store, confirm }: { data: SettingsData; store: ScheduleSettingsStore; confirm: Confirm }) {
  const draft = data.draft!
  const [rows, setRows] = useState<(Requirement & { key: string })[]>([])
  const [error, setError] = useState('')
  const [ack, setAck] = useState(false)
  const assigned = draft.value.slots.some(s => s.assignment || s.status === 'FILLED')
  const groups = new Map<string, { date: string; team: string; code: string; count: number; senior: number }>()
  for (const s of draft.value.slots) { const key = `${s.date}:${s.teamId}:${s.shiftTypeId}`; const g = groups.get(key) ?? { date: s.date, team: data.teams.find(t => t.teamId === s.teamId)?.name ?? (s.teamId ? '기존 팀' : '팀 미지정'), code: s.shiftCode, count: 0, senior: 0 }; ++g.count; if (s.seniorRequired) ++g.senior; groups.set(key, g) }
  const shifts = data.shifts.value.items.filter(s => s.active && (data.teams.length === 0 || s.kind !== 'OTHER'))
  const change = (key: string, patch: Partial<Requirement>) => { setAck(false); setRows(rows.map(r => r.key === key ? { ...r, ...patch } : r)) }
  return <section className="card"><h2>팀별 필요 인원</h2><p>현재 {draft.value.slots.length}칸 · 선임 미충원 {draft.value.staffing.seniorUnfilled ?? 0}칸</p>
    <details><summary>현재 수요 보기 (읽기 전용)</summary><DemandSummary groups={[...groups].map(([key, group]) => ({ key, ...group }))} /></details>
    <p className="notice">저장은 이 달의 수요 전체를 아래 목록으로 교체해요. 기존 필요 자격은 조회 API에 없어 자동 복사하지 않아요. 필요한 모든 날짜·팀·근무와 자격을 새로 입력해 주세요. 팀 지정 D/E/N 양수 수요에는 선임 칸 1개가 포함돼요.</p>
    {assigned && <p role="alert">이미 배치가 있는 초안이에요. 수요 교체는 서버에서 거절돼요. 배치 편집에서 먼저 해제해야 해요.</p>}
    <fieldset className="admin-fields" disabled={assigned}><legend className="sr-only">전체 교체할 수요</legend>
    <form onSubmit={e => { e.preventDefault(); const seen = new Set<string>(); let total = 0
      const items = rows.map(({ key: _key, ...r }) => ({ ...r, requiredQualifications: r.requiredQualifications.map(q => q.trim()).filter(Boolean) }))
      for (const r of items) { const key = `${r.date}:${r.teamId}:${r.shiftTypeId}`; total += r.requiredCount; if (!r.date.startsWith(`${draft.value.month}-`) || seen.has(key) || (data.teams.length > 0 && r.requiredCount > 0 && !r.teamId) || !r.shiftTypeId || r.requiredQualifications.length > 50 || r.requiredQualifications.some(q => q.length > 64)) { setError('월·중복·팀·근무 유형·필요 자격(각 64자, 최대 50개)을 확인해 주세요.'); return } seen.add(key) }
      if (total > 10000) { setError('총 필요 인원은 10,000명 이하여야 해요.'); return }
      setError('')
      confirm('수요 전체 교체', `${draft.value.month}의 기존 ${draft.value.slots.length}칸과 필요 자격을 새 목록 ${rows.length}건 · ${total}명으로 모두 교체할까요?`, () => store.api.requirements(draft.value.rosterId, items, draft.etag))
    }}>
      {rows.map((r, index) => <section className="team-editor" key={r.key} aria-label={`새 수요 ${index + 1}`}><h3>새 수요 {index + 1}</h3><div className="staff-grid">
        <label className="field">날짜<input required type="date" min={`${draft.value.month}-01`} max={monthDates(draft.value.month).filter(Boolean).at(-1)!} value={r.date} onChange={e => change(r.key, { date: e.target.value })} /></label>
        <label className="field">팀<select required={data.teams.length > 0 && r.requiredCount > 0} value={r.teamId ?? ''} onChange={e => change(r.key, { teamId: e.target.value || null })}><option value="">팀 미지정</option>{data.teams.map(t => <option value={t.teamId} key={t.teamId}>{t.name}</option>)}</select></label>
        <label className="field">근무 유형<select required value={r.shiftTypeId} onChange={e => change(r.key, { shiftTypeId: e.target.value })}><option value="">선택해 주세요</option>{shifts.map(s => <option key={s.shiftTypeId} value={s.shiftTypeId}>{s.code} · {s.displayName}</option>)}</select></label>
        <label className="field">필요 인원<input required type="number" min={0} max={100} step={1} value={Number.isNaN(r.requiredCount) ? '' : r.requiredCount} onChange={e => change(r.key, { requiredCount: e.target.value === '' ? NaN : Number(e.target.value) })} /></label>
        <label className="field">필요 자격 (쉼표 구분)<input value={r.requiredQualifications.join(',')} onChange={e => change(r.key, { requiredQualifications: e.target.value ? e.target.value.split(',') : [] })} /></label>
      </div><button className="secondary" type="button" onClick={() => { setRows(rows.filter(x => x.key !== r.key)); setAck(false) }}>이 수요 제거</button></section>)}
      <div className="actions"><button className="secondary" type="button" disabled={rows.length >= 1000 || shifts.length === 0} onClick={() => { setRows([...rows, { key: crypto.randomUUID(), date: `${draft.value.month}-01`, teamId: data.teams[0]?.teamId ?? null, shiftTypeId: shifts[0]!.shiftTypeId, requiredCount: 1, requiredQualifications: [] }]); setAck(false) }}>수요 항목 추가</button></div>
      {shifts.length === 0 && <p>먼저 활성 D/E/N 근무 유형을 등록해 주세요.</p>}
      <label className="check"><input type="checkbox" checked={ack} onChange={e => setAck(e.target.checked)} />기존 수요·필요 자격 전체를 교체함을 확인했어요. 빈 목록은 전체 삭제예요.</label>
      {error && <p role="alert" className="error">{error}</p>}<button disabled={!ack}>수요 전체 교체 확인</button>
    </form></fieldset>
  </section>
}

function AbsenceSettings({ data, store, confirm }: { data: SettingsData; store: ScheduleSettingsStore; confirm: Confirm }) {
  const [selected, setSelected] = useState<Absence | undefined>()
  const draft = data.draft!
  return <section className="card"><h2>승인 VY·반차</h2><p>이미 승인된 일정만 등록해요. 신청·결재 화면이 아니며 사유는 입력하지 않아요. 시각은 병동 시간대 {draft.value.timeZone} 기준이에요.</p>
    <AbsenceForm key={selected?.absenceId ?? 'new'} data={data} selected={selected} submit={input => confirm(selected ? '승인 부재 수정' : '승인 부재 등록', '이 시간 구간에는 근무를 배치할 수 없어요. 서버에서 초안에 반영해요.', () => store.api.saveAbsence(draft.value.rosterId, input, draft.etag, selected?.absenceId, selected ? undefined : crypto.randomUUID()))} />
    {selected && <button className="secondary" onClick={() => setSelected(undefined)}>새 승인 부재 입력</button>}
    <ul className="staff-list">{data.absences.map(a => <li key={a.absenceId}>{data.staff.find(n => n.nurseId === a.nurseId)?.displayName ?? '기존 간호사'} · {a.kind === 'APPROVED_LEAVE' ? '승인 VY' : '승인 반차'}<span>{localTime(a.startsAt, draft.value.timeZone).replace('T', ' ')} ~ {localTime(a.endsAt, draft.value.timeZone).replace('T', ' ')}</span><div className="actions"><button className="secondary" onClick={() => setSelected(a)}>부재 수정</button><button className="secondary" onClick={() => confirm('승인 부재 삭제', '이 승인 부재 구간을 초안에서 삭제할까요?', () => store.api.deleteAbsence(draft.value.rosterId, a.absenceId, draft.etag))}>부재 삭제</button></div></li>)}</ul>
    {data.absences.length === 0 && <p>등록된 승인 부재가 없어요.</p>}
  </section>
}
function AbsenceForm({ data, selected, submit }: { data: SettingsData; selected?: Absence; submit: (input: { nurseId: string; kind: Absence['kind']; startsAt: string; endsAt: string }) => void }) {
  const zone = data.draft!.value.timeZone
  const [nurseId, setNurse] = useState(selected?.nurseId ?? '')
  const [kind, setKind] = useState<Absence['kind']>(selected?.kind ?? 'APPROVED_LEAVE')
  const [start, setStart] = useState(selected ? localTime(selected.startsAt, zone) : '')
  const [end, setEnd] = useState(selected ? localTime(selected.endsAt, zone) : '')
  const [error, setError] = useState('')
  return <form onSubmit={e => {
    e.preventDefault()
    try {
      // Keep server precision and the original DST occurrence unless this endpoint changed.
      const startsAt = selected && start === localTime(selected.startsAt, zone) ? selected.startsAt : toInstant(start, zone)
      const endsAt = selected && end === localTime(selected.endsAt, zone) ? selected.endsAt : toInstant(end, zone)
      if (compareInstants(startsAt, endsAt) >= 0) throw new Error('종료는 시작보다 뒤여야 해요.')
      setError('')
      submit({ nurseId, kind, startsAt, endsAt })
    } catch (failure) { setError(failure instanceof Error ? failure.message : '날짜와 병동 시간대를 확인해 주세요.') }
  }}>
    <div className="staff-grid"><label className="field">부재 간호사<select required value={nurseId} onChange={e => setNurse(e.target.value)}><option value="">선택해 주세요</option>{data.staff.map(n => <option key={n.nurseId} value={n.nurseId}>{n.displayName}{n.active ? '' : ' · 비활성'}</option>)}</select></label>
      <label className="field">부재 구분<select value={kind} onChange={e => setKind(e.target.value as Absence['kind'])}><option value="APPROVED_LEAVE">승인 VY</option><option value="APPROVED_HALF_DAY">승인 반차</option></select></label>
      <label className="field">부재 시작<input type="datetime-local" required step={60} value={start} onChange={e => setStart(e.target.value)} /></label><label className="field">부재 종료<input type="datetime-local" required step={60} value={end} onChange={e => setEnd(e.target.value)} /></label>
    </div>{error && <p role="alert" className="error">{error}</p>}<button>{selected ? '부재 수정 확인' : '부재 등록 확인'}</button>
  </form>
}
