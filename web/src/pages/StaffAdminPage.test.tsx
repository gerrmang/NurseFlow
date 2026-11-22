import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from '../api/http'
import { setupApp } from '../test/harness'
import { json, USER_A, USER_B } from '../test/fakeAuthServer'
import { ADMIN, MEMBER, NURSE_A, NURSE_B, POLICY, TEAM_A, TEAM_B, WARD_1 } from '../test/staffFixtures'

function open(roles: ('WARD_ADMIN' | 'WARD_MEMBER')[] = ['WARD_ADMIN']) {
  const app = setupApp(`/wards/${WARD_1.wardId}/staff`, { configure: ({ server, backend }) => { server.signInAs(USER_A); backend.addMember(WARD_1.wardId, USER_A.userId, roles) } })
  const data = { staff: structuredClone([NURSE_A, NURSE_B]), teams: [TEAM_A, TEAM_B], members: [ADMIN, MEMBER], demoMode: false, eligibilityFails: false }
  const writes = vi.fn().mockImplementation(async () => json(200, NURSE_A))
  const base = app.backend.fetch
  vi.spyOn(app.backend, 'fetch').mockImplementation(async (url, init) => {
    if (url.startsWith(`/api/v1/wards/${WARD_1.wardId}/`)) {
      const path = url.slice(`/api/v1/wards/${WARD_1.wardId}`.length)
      if (init?.method && init.method !== 'GET') return writes(path, init)
      if (path === '/teams') return json(200, { items: data.teams })
      if (path === '/staff?includeInactive=true') return json(200, { items: data.staff })
      if (path === '/members') return json(200, { items: data.members })
      if (path === '/career-policy') return json(200, POLICY)
      if (path === '/roster-eligibility') return data.eligibilityFails ? json(503, { code: 'UNAVAILABLE' }) : json(200, { demoMode: data.demoMode, policyRevision: 0, items: [] })
      if (path.endsWith('/adjustments')) return json(200, { items: [{ adjustmentId: 'fictional-change', kind: 'STAGE_OVERRIDE', before: '2', after: '4', actorName: '가상 관리자', actorUserId: USER_A.userId, at: '2026-11-16T03:00:00Z' }] })
    }
    return base(url, init)
  })
  return { ...app, user: userEvent.setup(), writes, data }
}
const body = (app: ReturnType<typeof open>, index = 0) => JSON.parse(app.writes.mock.calls[index]![1].body)
it('labels server demo mode and replaces it after a successful refresh', async () => {
  const app = open()
  app.data.demoMode = true
  expect(await screen.findByText(/시연 모드 · 계정이 연결되지 않은/)).toBeInTheDocument()
  app.data.demoMode = false
  await app.user.click(screen.getByRole('button', { name: '최신 목록 불러오기' }))
  expect(await screen.findByText(/일반 모드 · 초대를 수락한/)).toBeInTheDocument()
  expect(screen.queryByText(/시연 모드 · 계정이 연결되지 않은/)).not.toBeInTheDocument()
})
it('does not label a failed mode refresh as normal mode', async () => {
  const app = open()
  app.data.demoMode = true
  await screen.findByText(/시연 모드 · 계정이 연결되지 않은/)
  app.data.eligibilityFails = true
  await app.user.click(screen.getByRole('button', { name: '최신 목록 불러오기' }))
  await screen.findByText(/시연 모드 확인 불가/)
  expect(screen.queryByText(/일반 모드 · 초대를 수락한/)).not.toBeInTheDocument()
  await app.user.type(screen.getByLabelText('새 팀 이름'), '가상 C팀')
  expect(screen.getByRole('button', { name: '팀 추가' })).toBeEnabled()
  expect(screen.getByRole('button', { name: NURSE_A.displayName })).toBeInTheDocument()
  app.data.eligibilityFails = false
  await app.user.click(screen.getByRole('button', { name: '최신 목록 불러오기' }))
  expect(await screen.findByText(/시연 모드 · 계정이 연결되지 않은/)).toBeInTheDocument()
})
it('keeps management available when only mode lookup fails on entry', async () => {
  const app = open()
  app.data.eligibilityFails = true
  await screen.findByText(/시연 모드 확인 불가/)
  expect(screen.getByRole('button', { name: NURSE_A.displayName })).toBeInTheDocument()
  expect(screen.queryByText(/일반 모드 · 초대를 수락한/)).not.toBeInTheDocument()
  await app.user.type(screen.getByLabelText('새 팀 이름'), '가상 C팀')
  expect(screen.getByRole('button', { name: '팀 추가' })).toBeEnabled()
})
async function detail(app: ReturnType<typeof open>) { await app.user.click(await screen.findByRole('button', { name: NURSE_A.displayName })) }

it('rejects direct management navigation for a member without loading staff lists', async () => {
  const app = open(['WARD_MEMBER'])
  expect(await screen.findByRole('alert')).toHaveTextContent('관리 권한이 없거나')
  expect(screen.queryByLabelText('새 팀 이름')).not.toBeInTheDocument()
  expect(vi.mocked(app.backend.fetch).mock.calls.some(([url]) => url.includes('/staff?'))).toBe(false)
})

it('shows active records by default and separates inactive records from membership status', async () => {
  const app = open()
  app.data.staff[1]!.active = false
  await screen.findByLabelText('새 팀 이름')
  expect(screen.queryByRole('button', { name: NURSE_B.displayName })).not.toBeInTheDocument()
  await app.user.click(screen.getByLabelText('비활성 기록 포함'))
  expect(screen.getByRole('button', { name: NURSE_B.displayName })).toBeInTheDocument()
})

it('preserves all active team members when saving an order while a search hides one record', async () => {
  const app = open()
  await app.user.type(await screen.findByLabelText('간호사 이름 찾기'), NURSE_A.displayName)
  const section = screen.getByRole('region', { name: `${TEAM_A.name} 경력순` })
  await app.user.click(within(section).getByRole('button', { name: `${NURSE_B.displayName} 위로` }))
  await app.user.click(within(section).getByRole('button', { name: '경력순 저장' }))
  await waitFor(() => expect(app.writes).toHaveBeenCalledOnce())
  expect(body(app)).toEqual({ nurseIds: [NURSE_B.nurseId, NURSE_A.nurseId] })
})

it('supports drag sorting and discards the edit when the server reports stale team membership', async () => {
  const app = open()
  app.writes.mockResolvedValueOnce(json(409, { code: 'TEAM_ORDER_STALE' }))
  const section = await screen.findByRole('region', { name: `${TEAM_A.name} 경력순` })
  const rows = within(section).getAllByRole('listitem')
  fireEvent.dragStart(rows[1]!, { dataTransfer: { setData: vi.fn() } })
  fireEvent.dragOver(rows[0]!)
  fireEvent.drop(rows[0]!)
  await app.user.click(within(section).getByRole('button', { name: '경력순 저장' }))
  expect(await screen.findByText(/다른 화면에서 팀 구성이 바뀌었어요/)).toBeInTheDocument()
  const fresh = screen.getByRole('region', { name: `${TEAM_A.name} 경력순` })
  expect(within(fresh).getAllByRole('listitem')[0]).toHaveTextContent(NURSE_A.displayName)
  expect(within(fresh).getByRole('button', { name: '경력순 저장' })).toBeDisabled()
})

it('sends senior eligibility together with the current stage override and explicitly clears to null', async () => {
  const app = open()
  await detail(app)
  await app.user.click(screen.getByLabelText('선임 자격 있음'))
  await app.user.click(screen.getByRole('button', { name: '편성 정보 저장' }))
  await waitFor(() => expect(app.writes).toHaveBeenCalledOnce())
  expect(body(app)).toEqual({ seniorEligible: true, stageOverride: 4 })
  await waitFor(() => expect(screen.getByLabelText('편성 단계 조정')).toBeEnabled())
  await app.user.selectOptions(screen.getByLabelText('편성 단계 조정'), '')
  await app.user.click(screen.getByRole('button', { name: '편성 정보 저장' }))
  await waitFor(() => expect(app.writes).toHaveBeenCalledTimes(2))
  expect(body(app, 1)).toEqual({ seniorEligible: false, stageOverride: null })
})

it('keeps record name and qualifications when toggling activation after confirmation', async () => {
  const app = open()
  await detail(app)
  await app.user.click(screen.getByRole('button', { name: '기록 비활성화' }))
  expect(app.writes).not.toHaveBeenCalled()
  expect(screen.getByRole('region', { name: '변경 확인' })).toHaveTextContent('구성원 접근 상태는 별도로 유지')
  await app.user.click(screen.getByRole('button', { name: '확인하고 실행' }))
  await waitFor(() => expect(app.writes).toHaveBeenCalledOnce())
  expect(body(app)).toEqual({ displayName: NURSE_A.displayName, qualifications: NURSE_A.qualifications, active: false })
})

it('edits actual career without overwriting the hospital join month and displays history', async () => {
  const app = open()
  await detail(app)
  expect(screen.getByText('2024-01')).toBeInTheDocument()
  const input = screen.getByLabelText('실제 경력 시작 연월')
  fireEvent.change(input, { target: { value: '2019-02' } })
  await app.user.click(screen.getByRole('button', { name: '실제 경력 저장' }))
  await waitFor(() => expect(app.writes).toHaveBeenCalledOnce())
  expect(body(app)).toEqual({ careerStartedOn: '2019-02', rank: '가상 직급' })
  const history = await screen.findByRole('region', { name: '조정 이력' })
  await waitFor(() => expect(history).toHaveTextContent('2 → 4'))
  expect(history).toHaveTextContent('가상 관리자')
})

it('sends teamId null when unassigning a nurse', async () => {
  const app = open()
  await detail(app)
  await app.user.selectOptions(screen.getByLabelText('소속 팀'), '')
  await app.user.click(screen.getByRole('button', { name: '팀 배치 저장' }))
  await waitFor(() => expect(app.writes).toHaveBeenCalledOnce())
  expect(body(app)).toEqual({ teamId: null })
})

it('explains member reactivation independently of nurse record reactivation', async () => {
  const app = open()
  app.data.members = [ADMIN, { ...MEMBER, active: false }]
  await app.user.click(await screen.findByRole('button', { name: '구성원' }))
  await app.user.click(screen.getByRole('button', { name: '접근 재활성화' }))
  expect(screen.getByRole('region', { name: '변경 확인' })).toHaveTextContent('병동 접근만 복구')
  await app.user.click(screen.getByRole('button', { name: '확인하고 실행' }))
  await waitFor(() => expect(app.writes).toHaveBeenCalledOnce())
  expect(body(app)).toEqual({ active: true })
})

it('removes all management controls after the caller relinquishes admin rights', async () => {
  const app = open()
  app.writes.mockImplementationOnce(async () => { app.backend.addMember(WARD_1.wardId, USER_A.userId, ['WARD_MEMBER']); return json(200, { ...ADMIN, roles: ['WARD_MEMBER'] }) })
  await app.user.click(await screen.findByRole('button', { name: '구성원' }))
  await app.user.click(screen.getByRole('button', { name: '관리자 해제' }))
  expect(screen.getByRole('region', { name: '변경 확인' })).toHaveTextContent('본인의 관리 권한을 잃으면')
  await app.user.click(screen.getByRole('button', { name: '확인하고 실행' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('관리 권한이 없거나')
  expect(screen.queryByRole('region', { name: '구성원 목록' })).not.toBeInTheDocument()
})

it('requires a fresh list and acknowledgement after a lost creation response, without resending', async () => {
  const app = open()
  app.writes.mockRejectedValueOnce(new ApiError(0, 'NETWORK'))
  await app.user.type(await screen.findByLabelText('새 팀 이름'), '가상 C팀')
  await app.user.click(screen.getByRole('button', { name: '팀 추가' }))
  await screen.findByRole('region', { name: '결과 확인' })
  await waitFor(() => expect(screen.getByRole('button', { name: '목록에서 결과를 확인했어요' })).toBeEnabled())
  expect(screen.getByLabelText('새 팀 이름')).toBeDisabled()
  expect(app.writes).toHaveBeenCalledOnce()
  await app.user.click(screen.getByRole('button', { name: '목록에서 결과를 확인했어요' }))
  expect(screen.getByLabelText('새 팀 이름')).toBeEnabled()
  expect(app.writes).toHaveBeenCalledOnce()
})

it('drops the previous account screen during an identity-changing refresh', async () => {
  const app = open()
  await detail(app)
  app.server.signInAs(USER_B)
  app.server.expireAllAccessTokens()
  await act(async () => { await app.manager.api.getMe().catch(() => undefined) })
  expect(await screen.findByRole('alert')).toHaveTextContent('관리 권한이 없거나')
  expect(screen.queryByLabelText('실제 경력 시작 연월')).not.toBeInTheDocument()
})
