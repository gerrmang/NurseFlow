import { useEffect, useMemo, useState, useSyncExternalStore } from 'react'
import { Link } from 'react-router'
import { createStaffAdminApi } from '../api/staffAdminApi'
import type { StaffAdjustment, StaffMember, Team, WardMember } from '../api/types'
import { useSessionManager } from '../session/SessionContext'
import { StaffAdminStore, type StaffData } from '../staff/StaffAdminStore'

type Confirm = (label: string, explanation: string, operation: () => Promise<unknown>) => void
type Run = (label: string, operation: () => Promise<unknown>) => Promise<boolean>
const stage = (value: number | null | undefined) => value == null ? '미설정' : `${value}단계`
const profileStatus = { MISSING: '미입력', PENDING: '확인 대기', CONFIRMED: '확인 완료' }

export function StaffAdminPage({ wardId }: { wardId: string }) {
  const manager = useSessionManager()
  const store = useMemo(() => new StaffAdminStore(createStaffAdminApi((path, init) => manager.authorizedFetch(path, init), wardId), () => manager.api.getWards(), wardId), [manager, wardId])
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot)
  const [tab, setTab] = useState<'staff' | 'detail' | 'members'>('staff')
  const [selected, setSelected] = useState('')
  const [confirmation, setConfirmation] = useState<{ label: string; explanation: string; operation: () => Promise<unknown> } | null>(null)
  useEffect(() => { store.activate(); void store.reload(); return () => store.dispose() }, [store])
  const run: Run = (label, op) => store.run(label, op)
  const confirm: Confirm = (label, explanation, operation) => setConfirmation({ label, explanation, operation })
  const locked = state.loading || state.busy || state.stale || state.uncertain || state.forbidden || confirmation !== null
  const data = state.data
  const nurse = data?.staff.find((item) => item.nurseId === selected)
  const session = manager.getState()
  return <main className="page staff-page">
    <header className="toolbar"><h1>팀·구성원 관리{data && ` · ${data.wardName}`}</h1><Link to="/wards">내 병동</Link></header>
    {!state.forbidden && <p role="status" className={data?.eligibility?.demoMode && !state.stale ? 'error' : 'notice'}>
      {state.stale || !data ? '시연 모드 상태를 확인하고 있어요. 최신 목록을 불러와 주세요.' : !data.eligibility
        ? '시연 모드 확인 불가 · 새로고침해 다시 확인해 주세요.' : data.eligibility.demoMode
        ? '시연 모드 · 계정이 연결되지 않은 가상 간호사도 편성에 포함돼요. 실제 근무표로 사용하지 마세요.'
        : '일반 모드 · 초대를 수락한 활성 간호사만 편성에 포함돼요.'}
    </p>}
    <p className="notice">저장 후 서버의 최신 목록을 다시 불러와요. 새로고침하면 미저장 입력과 순서는 초기화돼요.</p>
    <div className="actions"><button className="secondary" disabled={state.busy || state.loading || !!confirmation} onClick={() => void store.reload()}>최신 목록 불러오기</button></div>
    {state.loading && <p role="status">최신 정보를 불러오고 있어요…</p>}
    {state.busy && <p role="status">변경 결과를 확인하고 있어요…</p>}
    {state.notice && !state.forbidden && <p role={state.noticeTone === 'error' ? 'alert' : 'status'} className={state.noticeTone === 'error' ? 'error' : 'notice'}>{state.notice}</p>}
    {state.forbidden && <section className="card"><p role="alert">병동 관리 권한이 없거나 소속이 변경됐어요.</p><Link to="/wards">내 병동 목록 확인</Link></section>}
    {state.uncertain && !state.forbidden && <section className="card" aria-label="결과 확인">
      <p>이미 처리됐을 수 있으니 목록과 해당 기록을 확인해 주세요. 생성 요청을 다시 보내면 중복될 수 있어요.</p>
      <button disabled={state.stale || state.loading || state.busy} onClick={() => store.acknowledge()}>목록에서 결과를 확인했어요</button>
    </section>}
    {confirmation && !state.forbidden && <section className="card confirm" aria-label="변경 확인">
      <h2>{confirmation.label}</h2><p>{confirmation.explanation}</p>
      <div className="actions"><button onClick={() => { const action = confirmation; setConfirmation(null); void run(action.label, action.operation) }}>확인하고 실행</button><button className="secondary" onClick={() => setConfirmation(null)}>취소</button></div>
    </section>}
    {data && !state.forbidden && <>
      <nav className="actions" aria-label="관리 화면">
        <button className={tab === 'staff' ? '' : 'secondary'} disabled={!!confirmation} aria-pressed={tab === 'staff'} onClick={() => setTab('staff')}>팀·간호사</button>
        <button className={tab === 'detail' ? '' : 'secondary'} disabled={!!confirmation} aria-pressed={tab === 'detail'} onClick={() => setTab('detail')}>간호사 상세</button>
        <button className={tab === 'members' ? '' : 'secondary'} disabled={!!confirmation} aria-pressed={tab === 'members'} onClick={() => setTab('members')}>구성원</button>
      </nav>
      <fieldset className="admin-fields" disabled={locked}>
        <legend className="sr-only">병동 관리 입력</legend>
        {tab === 'staff' && <>
          <Teams key={`teams:${state.version}`} data={data} store={store} run={run} confirm={confirm} />
          <StaffList key={`list:${state.version}`} data={data} store={store} run={run} onSelect={(id) => { setSelected(id); setTab('detail') }} />
        </>}
        {tab === 'detail' && <>
          <label className="field">간호사 선택<select value={selected} onChange={(event) => setSelected(event.target.value)}><option value="">선택해 주세요</option>{data.staff.map((item) => <option value={item.nurseId} key={item.nurseId}>{item.displayName}{item.active ? '' : ' · 기록 비활성'}</option>)}</select></label>
          {nurse ? <StaffDetail key={`${nurse.nurseId}:${state.version}`} nurse={nurse} data={data} store={store} run={run} confirm={confirm} /> : <p>목록에서 간호사를 선택해 주세요.</p>}
        </>}
        {tab === 'members' && <Members data={data} selfId={session.status === 'signedIn' ? session.user.userId : ''} store={store} confirm={confirm} />}
      </fieldset>
      <p className="notice">팀 구성 변경은 서버에서 충돌을 확인해요. 일반 정보의 동시 수정은 마지막 저장이 반영될 수 있어요.</p>
    </>}
  </main>
}

function Teams({ data, store, run, confirm }: { data: StaffData; store: StaffAdminStore; run: Run; confirm: Confirm }) {
  const [name, setName] = useState('')
  return <section className="card" aria-label="팀 설정"><h2>팀 설정 · {data.teams.length}개</h2>
    <form onSubmit={(event) => { event.preventDefault(); void run('팀 생성', () => store.api.createTeam({ name: name.trim() })) }}>
      <label className="field">새 팀 이름<input value={name} maxLength={50} onChange={(event) => setName(event.target.value)} /></label>
      <button disabled={!name.trim()}>팀 추가</button>
    </form>
    {data.teams.length === 0 && <p>아직 팀이 없어요. 팀을 먼저 추가해 주세요.</p>}
    {data.teams.map((team) => <TeamEditor key={team.teamId} team={team} store={store} run={run} confirm={confirm} />)}
  </section>
}

function TeamEditor({ team, store, run, confirm }: { team: Team; store: StaffAdminStore; run: Run; confirm: Confirm }) {
  const [name, setName] = useState(team.name)
  const [order, setOrder] = useState(String(team.sortOrder))
  return <section className="team-editor" aria-label={`${team.name} 팀 설정`}>
    <form onSubmit={(event) => { event.preventDefault(); void run('팀 수정', () => store.api.updateTeam(team.teamId, { name: name.trim(), sortOrder: Number(order) })) }}>
      <div className="staff-grid"><label className="field">팀 이름<input value={name} maxLength={50} onChange={(event) => setName(event.target.value)} /></label>
        <label className="field">팀 표시 순서<input type="number" min={1} max={1000} required value={order} onChange={(event) => setOrder(event.target.value)} /></label></div>
      <div className="actions"><button disabled={!name.trim() || !order || Number(order) < 1 || Number(order) > 1000}>팀 저장</button>
        <button className="secondary" type="button" onClick={() => confirm('팀 삭제', `${team.name} 팀을 삭제할까요? 활성 간호사가 있으면 서버에서 거절해요. 비활성 기록의 팀 지정도 해제돼요.`, () => store.api.deleteTeam(team.teamId))}>팀 삭제</button></div>
    </form>
  </section>
}

function StaffList({ data, store, run, onSelect }: { data: StaffData; store: StaffAdminStore; run: Run; onSelect: (id: string) => void }) {
  const [query, setQuery] = useState('')
  const [includeInactive, setIncludeInactive] = useState(false)
  const [name, setName] = useState('')
  const [teamId, setTeamId] = useState('')
  const visible = data.staff.filter((item) => (includeInactive || item.active) && item.displayName.includes(query.trim()))
  return <section className="card" aria-label="간호사 목록"><h2>간호사 기록</h2>
    <p className="notice">로그인 계정과 연결되지 않은 기록도 보여요. 편성 대상 여부는 서버가 판단해요.</p>
    <form onSubmit={(event) => { event.preventDefault(); void run('간호사 기록 등록', () => store.api.createStaff({ displayName: name.trim(), qualifications: [], active: true, ...(teamId ? { teamId } : {}) })) }}>
      <div className="staff-grid"><label className="field">새 간호사 이름<input value={name} maxLength={100} onChange={(event) => setName(event.target.value)} /></label>
        <TeamPicker label="등록할 팀" teams={data.teams} value={teamId} onChange={setTeamId} /></div>
      <button disabled={!name.trim()}>계정 연결 전 기록 등록</button>
    </form>
    <div className="staff-grid"><label className="field">간호사 이름 찾기<input value={query} onChange={(event) => setQuery(event.target.value)} /></label>
      <label className="check"><input type="checkbox" checked={includeInactive} onChange={(event) => setIncludeInactive(event.target.checked)} />비활성 기록 포함</label></div>
    {visible.length === 0 && <p>표시할 간호사 기록이 없어요.</p>}
    <ul className="staff-list">{visible.map((item) => <li key={item.nurseId}>
      <button className="link" onClick={() => onSelect(item.nurseId)}>{item.displayName}</button>
      <span>{item.team?.name ?? '미배정'} · {item.active ? '기록 활성' : '기록 비활성'} · {item.linkedAccount ? '계정 연결됨' : '계정 미연결'}</span>
      <span>팀 내 순서 {item.teamOrder ?? '미지정'} · {item.seniorEligible ? '선임 자격 있음' : '선임 자격 없음'} · 편성 {stage(item.rosterStage)}</span>
    </li>)}</ul>
    <Link to={`/wards/${encodeURIComponent(store.wardId)}/invitations`}>초대 관리에서 기록과 계정 연결</Link>
    <h3>팀 안 경력순</h3><p className="notice">위가 더 선임이에요. 검색과 관계없이 팀의 활성 간호사 전체를 정렬해요. 드래그하거나 위로·아래로 버튼을 사용한 뒤 저장해 주세요.</p>
    {data.teams.map((team) => <TeamOrder key={team.teamId} team={team} nurses={data.staff.filter((item) => item.active && item.team?.teamId === team.teamId)} store={store} run={run} />)}
  </section>
}

function TeamOrder({ team, nurses, store, run }: { team: Team; nurses: StaffMember[]; store: StaffAdminStore; run: Run }) {
  const [ids, setIds] = useState(nurses.map((item) => item.nurseId))
  const [dragging, setDragging] = useState<string | null>(null)
  const original = nurses.map((item) => item.nurseId)
  const dirty = ids.some((id, index) => id !== original[index])
  const move = (from: number, to: number) => {
    const state = store.getSnapshot()
    if (state.busy || state.loading || state.stale || state.uncertain || state.forbidden) return
    if (from < 0 || to < 0 || from >= ids.length || to >= ids.length) return
    const next = [...ids]; const [id] = next.splice(from, 1); next.splice(to, 0, id!); setIds(next)
  }
  return <section aria-label={`${team.name} 경력순`} className="team-editor"><h4>{team.name} · 활성 {ids.length}명</h4>
    {ids.length === 0 ? <p>활성 간호사가 없어요.</p> : <ol className="order-list">{ids.map((id, index) => {
      const item = nurses.find((nurse) => nurse.nurseId === id)!
      return <li key={id} draggable onDragStart={(event) => { setDragging(id); event.dataTransfer.setData('text/plain', id) }} onDragEnd={() => setDragging(null)}
        onDragOver={(event) => event.preventDefault()} onDrop={(event) => { event.preventDefault(); if (dragging) move(ids.indexOf(dragging), index); setDragging(null) }}>
        <span>{item.displayName}{item.teamOrder == null ? ' · 순서 미지정' : ''}</span>
        <div className="actions"><button className="secondary" aria-label={`${item.displayName} 위로`} disabled={index === 0} onClick={() => move(index, index - 1)}>위로</button>
          <button className="secondary" aria-label={`${item.displayName} 아래로`} disabled={index === ids.length - 1} onClick={() => move(index, index + 1)}>아래로</button></div>
      </li>
    })}</ol>}
    <button disabled={ids.length === 0 || ids.length > 200 || (!dirty && nurses.every((item) => item.teamOrder != null))} onClick={() => void run('팀 경력순 저장', () => store.api.order(team.teamId, ids))}>경력순 저장</button>
    {ids.length > 200 && <p className="error">현재 API는 팀당 200명까지 정렬할 수 있어요.</p>}
  </section>
}

function TeamPicker({ label, teams, value, onChange }: { label: string; teams: Team[]; value: string; onChange: (value: string) => void }) {
  return <label className="field">{label}<select value={value} onChange={(event) => onChange(event.target.value)}><option value="">미배정</option>{teams.map((team) => <option key={team.teamId} value={team.teamId}>{team.name}</option>)}</select></label>
}

function StaffDetail({ nurse, data, store, run, confirm }: { nurse: StaffMember; data: StaffData; store: StaffAdminStore; run: Run; confirm: Confirm }) {
  const [name, setName] = useState(nurse.displayName)
  const [qualifications, setQualifications] = useState(nurse.qualifications.join('\n'))
  const [teamId, setTeamId] = useState(nurse.team?.teamId ?? '')
  const [career, setCareer] = useState(nurse.profile.careerStartedOn ?? '')
  const [rank, setRank] = useState(nurse.profile.rank ?? '')
  const [senior, setSenior] = useState(nurse.seniorEligible)
  const [override, setOverride] = useState(nurse.stageOverride == null ? '' : String(nurse.stageOverride))
  const [history, setHistory] = useState<StaffAdjustment[] | null>(null)
  const [historyError, setHistoryError] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const values = qualifications.split('\n').map((value) => value.trim()).filter(Boolean)
  useEffect(() => {
    let active = true
    store.api.adjustments(nurse.nurseId).then((items) => { if (active) setHistory(items) }, () => { if (active) setHistoryError(true) })
    return () => { active = false }
  }, [store, nurse.nurseId, attempt])
  return <section className="card" aria-label="간호사 상세"><h2>{nurse.displayName}</h2>
    <p>{nurse.active ? '간호사 기록 활성' : '간호사 기록 비활성'} · {nurse.linkedAccount ? '계정 연결됨' : '계정 미연결'}</p>
    <dl className="summary"><dt>병원 입사 연월</dt><dd>{nurse.profile.hospitalJoinedOn ?? '미입력'}</dd><dt>실제 경력 상태</dt><dd>{profileStatus[nurse.profile.status]}</dd>
      <dt>확정 경력 시작</dt><dd>{nurse.profile.confirmedCareerStartedOn ?? '미입력'}</dd><dt>확정 직급</dt><dd>{nurse.profile.confirmedRank ?? '미입력'}</dd>
      <dt>경력 개월 수</dt><dd>{nurse.profile.experienceMonths == null ? '미설정' : `${nurse.profile.experienceMonths}개월`}</dd><dt>서버 계산 단계</dt><dd>{stage(nurse.profile.scoringStage)}</dd>
      <dt>관리자 조정 단계</dt><dd>{nurse.stageOverride == null ? '계산값 사용' : stage(nurse.stageOverride)}</dd><dt>최종 편성 단계</dt><dd>{stage(nurse.rosterStage)}</dd></dl>
    <form onSubmit={(event) => { event.preventDefault(); void run('간호사 정보 저장', () => store.api.updateStaff(nurse.nurseId, { displayName: name.trim(), qualifications: values, active: nurse.active })) }}>
      <label className="field">간호사 이름<input maxLength={100} value={name} onChange={(event) => setName(event.target.value)} /></label>
      <label className="field">보유 자격 · 한 줄에 하나<textarea value={qualifications} onChange={(event) => setQualifications(event.target.value)} /></label>
      <p className="notice">자격은 최대 50개, 각 64자까지 입력해요.</p><button disabled={!name.trim() || values.length > 50 || values.some((value) => value.length > 64)}>기본 정보 저장</button>
    </form>
    <form onSubmit={(event) => { event.preventDefault(); void run('팀 이동', () => store.api.assignTeam(nurse.nurseId, teamId || null)) }}><TeamPicker label="소속 팀" teams={data.teams} value={teamId} onChange={setTeamId} /><p className="notice">팀을 옮기면 기존 팀 내 순서가 지워져요.</p><button disabled={teamId === (nurse.team?.teamId ?? '')}>팀 배치 저장</button></form>
    <form onSubmit={(event) => { event.preventDefault(); void run('실제 경력 저장', () => store.api.setProfile(nurse.nurseId, { careerStartedOn: career, rank })) }}>
      <h3>실제 경력 정보</h3><p className="notice">병원 입사 연월과 별도로 관리해요. 저장 즉시 확인 상태가 돼요.</p>
      <label className="field">실제 경력 시작 연월<input type="month" min="1950-01" required value={career} onChange={(event) => setCareer(event.target.value)} /></label>
      <label className="field">직급<select value={rank} onChange={(event) => setRank(event.target.value)}><option value="">선택해 주세요</option>{data.policy.ranks.map((value) => <option key={value}>{value}</option>)}</select></label>
      <p className="notice">현재 단계 기준(개월): {data.policy.stageThresholdMonths.join(' / ')}. 미래 연월 판단은 서버 병동 시간대를 따라요.</p>
      <div className="actions"><button disabled={!/^\d{4}-(0[1-9]|1[0-2])$/.test(career) || career < '1950-01' || !data.policy.ranks.includes(rank)}>실제 경력 저장</button>
        <button className="secondary" type="button" disabled={nurse.profile.status !== 'PENDING'} onClick={() => confirm('기존 경력 확인', `${nurse.displayName}의 현재 제출된 실제 경력과 직급을 확인할까요? 입력 중인 수정값은 이 작업에 포함되지 않아요.`, () => store.api.confirmProfile(nurse.nurseId))}>기존 경력 확인</button></div>
    </form>
    <form onSubmit={(event) => { event.preventDefault(); void run('편성 정보 저장', () => store.api.settings(nurse.nurseId, { seniorEligible: senior, stageOverride: override === '' ? null : Number(override) })) }}>
      <h3>편성 정보</h3><label className="check"><input type="checkbox" checked={senior} onChange={(event) => setSenior(event.target.checked)} />선임 자격 있음</label>
      <label className="field">편성 단계 조정<select value={override} onChange={(event) => setOverride(event.target.value)}><option value="">계산값 사용</option>{[1, 2, 3, 4, 5].map((value) => <option key={value} value={value}>{value}단계</option>)}</select></label><button>편성 정보 저장</button>
    </form>
    <section><h3>간호사 기록 활성 상태</h3><p className="notice">기록 비활성화는 계정의 병동 접근 권한을 없애지 않아요. 접근 차단은 구성원 탭에서 관리해요.</p>
      <button className="secondary" onClick={() => confirm(nurse.active ? '간호사 기록 비활성화' : '간호사 기록 활성화', `${nurse.displayName}의 간호사 기록을 ${nurse.active ? '비활성화' : '활성화'}할까요? 구성원 접근 상태는 별도로 유지돼요.`, () => store.api.updateStaff(nurse.nurseId, { displayName: nurse.displayName, qualifications: nurse.qualifications, active: !nurse.active }))}>{nurse.active ? '기록 비활성화' : '기록 활성화'}</button>
    </section>
    <section aria-label="조정 이력"><h3>조정 이력</h3>
      {historyError ? <><p role="alert">이력을 불러오지 못했어요.</p><button className="secondary" onClick={() => { setHistoryError(false); setHistory(null); setAttempt((value) => value + 1) }}>이력 다시 불러오기</button></> : history === null ? <p role="status">이력을 불러오고 있어요…</p> : history.length === 0 ? <p>아직 조정 이력이 없어요.</p> : <ul className="history-list">{history.map((item) => <li key={item.adjustmentId}><strong>{adjustmentLabel[item.kind]}</strong><span>{adjustmentValue(item.kind, item.before)} → {adjustmentValue(item.kind, item.after)}</span><span>{item.actorName ?? '이름 미확인'} · {new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Seoul' }).format(new Date(item.at))} (한국 시간)</span></li>)}</ul>}
    </section>
  </section>
}

const adjustmentLabel: Record<StaffAdjustment['kind'], string> = { TEAM_ORDER: '팀 안 순서', SENIOR_ELIGIBLE: '선임 자격', STAGE_OVERRIDE: '편성 단계', TEAM_ASSIGNED: '팀 배치', PROFILE_SET_BY_ADMIN: '관리자 경력 입력', PROFILE_CONFIRMED: '경력 확인' }
function adjustmentValue(kind: StaffAdjustment['kind'], value: string | null | undefined) {
  if (value == null) return kind === 'STAGE_OVERRIDE' ? '계산값 사용' : '없음'
  if (kind === 'SENIOR_ELIGIBLE') return value === 'true' ? '있음' : '없음'
  return value
}

function Members({ data, selfId, store, confirm }: { data: StaffData; selfId: string; store: StaffAdminStore; confirm: Confirm }) {
  const change = (member: WardMember, kind: 'admin' | 'active') => {
    const current = kind === 'admin' ? member.roles.includes('WARD_ADMIN') : member.active
    const label = kind === 'admin' ? current ? '관리자 해제' : '관리자 지정' : current ? '구성원 비활성화' : '구성원 재활성화'
    const impact = kind === 'admin' ? '관리자는 편성 대상에서 제외돼요. 활성 간호사 기록이 있으면 먼저 기록을 비활성화해 주세요.' : current ? '병동 접근을 즉시 차단하고 연결된 간호사 기록도 비활성화해요.' : '병동 접근만 복구해요. 간호사 기록 활성화는 상세 화면에서 별도로 진행해 주세요.'
    confirm(label, `${member.displayName}${member.userId === selfId ? ' (본인)' : ''}: ${impact}${member.userId === selfId && current ? ' 본인의 관리 권한을 잃으면 이 화면에서 나가게 돼요.' : ''}`, () => store.api.membership(member.userId, { [kind]: !current }))
  }
  return <section className="card" aria-label="구성원 목록"><h2>구성원 접근과 역할</h2><p className="notice">비활성 구성원도 포함해요. 계정 접근과 간호사 기록 상태는 서로 달라요.</p>
    {data.members.length === 0 && <p>표시할 구성원이 없어요.</p>}
    <ul className="staff-list">{data.members.map((member) => {
      const nurse = data.staff.find((item) => item.nurseId === member.nurseId)
      return <li key={member.userId}><strong>{member.displayName}{member.userId === selfId ? ' (본인)' : ''}</strong><span>{member.roles.map((role) => role === 'WARD_ADMIN' ? '관리자' : '간호사').join(' · ')} · 접근 {member.active ? '활성' : '비활성'} · {nurse ? `간호사 기록 ${nurse.active ? '활성' : '비활성'}` : '연결된 간호사 기록 없음'}</span>
        <div className="actions"><button className="secondary" onClick={() => change(member, 'admin')}>{member.roles.includes('WARD_ADMIN') ? '관리자 해제' : '관리자 지정'}</button><button className="secondary" onClick={() => change(member, 'active')}>{member.active ? '접근 비활성화' : '접근 재활성화'}</button></div>
      </li>
    })}</ul>
  </section>
}
