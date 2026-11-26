import { useState, useSyncExternalStore } from 'react'
import { Link } from 'react-router'
import { useSessionManager } from '../session/SessionContext'
import { getWardCreationFlow } from '../ward/WardCreationFlow'

export function WardCreatePage({ epoch }: { epoch: number }) {
  const manager = useSessionManager()
  const flow = getWardCreationFlow(manager, epoch)
  const state = useSyncExternalStore(flow.subscribe, flow.getSnapshot)
  const [confirmNew, setConfirmNew] = useState(false)
  const locked = state.busy || state.uncertain

  if (state.created) return (
    <main className="page ward-create-page">
      <h1>병동을 만들었어요</h1>
      <section className="card" aria-label="생성한 병동">
        <h2>{state.created.wardName}</h2>
        {state.created.hospitalName && <p>{state.created.hospitalName}</p>}
        <p>이 병동의 관리자로 등록됐어요. 병원 선택은 재직 인증을 뜻하지 않아요.</p>
      </section>
      <section className="card" aria-label="설정 안내">
        <h2>근무표를 만들기 전에</h2>
        <p>아래 항목을 차례로 준비해 주세요. 설정 완료 여부는 아직 확인하지 않아요.</p>
        <ul className="hints">
          <li>구성원 초대: 초대 관리에서 진행할 수 있어요.</li>
          <li>팀 구성·간호사 경력·선임 자격: 웹 설정 화면 준비 중</li>
          <li>근무 유형·필요 인원·근무 규칙: 웹 설정 화면 준비 중</li>
          <li>초안 생성 전 점검: 준비 중</li>
        </ul>
        <div className="actions">
          <Link className="button" to={`/wards/${encodeURIComponent(state.created.wardId)}/invitations`}>초대 관리</Link>
          <Link className="button secondary" to="/wards">내 병동 목록 확인</Link>
          <button className="secondary" onClick={() => flow.startNew()}>다른 병동 만들기</button>
        </div>
      </section>
    </main>
  )

  const results = state.results
  return (
    <main className="page ward-create-page">
      <header className="toolbar"><h1>병동 만들기</h1><Link to="/wards">내 병동 목록 확인</Link></header>
      <p>공식 목록에서 병원을 고르고 병동 이름을 입력해 주세요. 병원 선택은 재직 인증이 아니에요.</p>
      <section className="card" aria-label="병원 검색">
        <h2>1. 병원 선택</h2>
        <form onSubmit={(event) => { event.preventDefault(); void flow.search() }}>
          <label className="field">병원 이름
            <input value={state.query} onChange={(event) => flow.setQuery(event.target.value)} disabled={locked} />
          </label>
          <p className="notice">문자·숫자 2자 이상, 전체 2~50자로 입력하고 검색해 주세요.</p>
          <button disabled={locked || state.searching}>검색</button>
        </form>
        {state.searching && <p role="status">병원을 검색하고 있어요…</p>}
        {state.searchError && <p role="alert" className="error">{state.searchError}</p>}
        {results && <>
          <p className="notice">{results.attribution}</p>
          {results.items.length === 0 ? <p role="status">검색 결과가 없어요. 병원 이름을 바꿔 검색해 주세요.</p> : <>
            <fieldset className="hospital-results" disabled={locked}>
              <legend>병원 검색 결과</legend>
              {results.items.map((hospital) => <label className="hospital-option" key={`${hospital.source}:${hospital.externalId}`}>
                <input type="radio" name="hospital" checked={state.selected?.externalId === hospital.externalId && state.selected.source === hospital.source}
                  onChange={() => flow.select(hospital)} />
                <span><strong>{hospital.name}</strong><span>{hospital.address}</span>{hospital.category && <span className="notice">{hospital.category}</span>}</span>
              </label>)}
            </fieldset>
            <nav className="actions" aria-label="검색 결과 페이지">
              <button className="secondary" disabled={locked || results.page <= 1} onClick={() => void flow.search(results.page - 1)}>이전 결과</button>
              <span>{results.page} 페이지 · 전체 {results.totalCount}곳</span>
              <button className="secondary" disabled={locked || results.page >= 100 || results.page * results.size >= results.totalCount}
                onClick={() => void flow.search(results.page + 1)}>다음 결과</button>
            </nav>
            {results.page >= 100 && <p className="notice">100페이지까지 볼 수 있어요. 병원 이름을 더 구체적으로 검색해 주세요.</p>}
          </>}
        </>}
      </section>
      <section className="card" aria-label="병동 정보">
        <h2>2. 병동 정보</h2>
        {state.selected ? <p>선택한 병원: <strong>{state.selected.name}</strong><br />{state.selected.address}</p> : <p>먼저 검색 결과에서 병원을 선택해 주세요.</p>}
        <form onSubmit={(event) => { event.preventDefault(); void flow.create() }}>
          <label className="field">병동 이름
            <input value={state.wardName} onChange={(event) => flow.setWardName(event.target.value)} disabled={locked} />
          </label>
          <p className={state.wardName.trim().length > 100 ? 'error' : 'notice'}>앞뒤 공백을 제외하고 1~100자로 입력해 주세요. ({state.wardName.trim().length}/100)</p>
          <p className="notice">병동 시간대: Asia/Seoul</p>
          {state.error && <p role="alert" className="error">{state.error}</p>}
          {locked && <p className="notice">재시도 정보는 이 탭의 현재 로그인 동안만 유지돼요. 새로고침·탭 닫기·로그아웃 전에 결과를 확인해 주세요.</p>}
          <button disabled={state.busy || !state.selected || !state.wardName.trim() || state.wardName.trim().length > 100}>
            {state.busy ? '생성 결과 확인 중…' : state.uncertain ? '같은 요청 재시도' : '병동 만들기'}
          </button>
        </form>
        {state.uncertain && <div className="actions">
          <Link className="button secondary" to="/wards">내 병동 목록 확인</Link>
          <button className="secondary" onClick={() => setConfirmNew(true)}>입력을 바꿔 새 요청</button>
        </div>}
        {confirmNew && state.uncertain && <section className="card" aria-label="새 요청 확인">
          <p>이전 요청으로 이미 병동이 만들어졌을 수 있어요. 목록을 먼저 확인해 주세요. 계속하면 이전 요청의 재시도 정보가 사라져요.</p>
          <div className="actions"><button onClick={() => { flow.startNew(); setConfirmNew(false) }}>확인하고 새 요청 시작</button>
            <button className="secondary" onClick={() => setConfirmNew(false)}>취소</button></div>
        </section>}
      </section>
    </main>
  )
}
