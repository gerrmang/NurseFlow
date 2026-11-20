import { ApiError } from '../api/http'
import { ADMIN, ADMIN_WARD, MEMBER, NURSE_A, POLICY, TEAM_A, WARD_1 } from '../test/staffFixtures'
import { StaffAdminStore } from './StaffAdminStore'

function gate<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => { resolve = done })
  return { promise, resolve }
}
function setup() {
  const api = {
    teams: vi.fn().mockResolvedValue([TEAM_A]), staff: vi.fn().mockResolvedValue([NURSE_A]), members: vi.fn().mockResolvedValue([ADMIN, MEMBER]), policy: vi.fn().mockResolvedValue(POLICY),
    eligibility: vi.fn().mockResolvedValue({ demoMode: false, policyRevision: 0, items: [] }),
    createTeam: vi.fn(), updateTeam: vi.fn(), deleteTeam: vi.fn(), createStaff: vi.fn(), updateStaff: vi.fn(), assignTeam: vi.fn(), order: vi.fn(), setProfile: vi.fn(), confirmProfile: vi.fn(), settings: vi.fn(), adjustments: vi.fn(), membership: vi.fn(),
  }
  const wards = vi.fn().mockResolvedValue({ items: [ADMIN_WARD] })
  const store = new StaffAdminStore(api, wards, WARD_1.wardId)
  return { api, wards, store }
}

it('checks admin membership before any sensitive lists are loaded', async () => {
  const { store, wards, api } = setup()
  wards.mockResolvedValue({ items: [{ ...ADMIN_WARD, roles: ['WARD_MEMBER'] }] })
  await store.reload()
  expect(store.getSnapshot().forbidden).toBe(true)
  expect(store.getSnapshot().data).toBeNull()
  expect(api.staff).not.toHaveBeenCalled()
})

it('discards late reads, including reads after leaving the screen', async () => {
  const { store, api } = setup()
  const old = gate<typeof TEAM_A[]>()
  api.teams.mockReturnValueOnce(old.promise)
  const first = store.reload()
  await vi.waitFor(() => expect(api.teams).toHaveBeenCalledOnce())
  api.teams.mockResolvedValueOnce([{ ...TEAM_A, name: '새 팀' }])
  await store.reload()
  old.resolve([TEAM_A])
  await first
  expect(store.getSnapshot().data?.teams[0]?.name).toBe('새 팀')
  const pending = gate<typeof TEAM_A[]>()
  api.teams.mockReturnValueOnce(pending.promise)
  const next = store.reload()
  await vi.waitFor(() => expect(api.teams).toHaveBeenCalledTimes(3))
  store.dispose()
  pending.resolve([])
  await next
  expect(store.getSnapshot().data?.teams).toHaveLength(1)
})

it('serializes writes before React renders and refreshes all dependent lists', async () => {
  const { store, api } = setup()
  await store.reload()
  const response = gate<void>()
  const operation = vi.fn(() => response.promise)
  const first = store.run('저장', operation)
  await store.run('저장', operation)
  expect(operation).toHaveBeenCalledOnce()
  response.resolve()
  await first
  expect(api.staff).toHaveBeenCalledTimes(2)
  expect(api.members).toHaveBeenCalledTimes(2)
  expect(store.getSnapshot().busy).toBe(false)
  expect(store.getSnapshot().stale).toBe(false)
})

it.each([new ApiError(0, 'NETWORK'), new ApiError(503, 'HTTP_503'), new ApiError(200, 'INVALID_RESPONSE')])('does not resend or unlock an uncertain write until explicit acknowledgement: %s', async (error) => {
  const { store } = setup()
  await store.reload()
  const operation = vi.fn().mockRejectedValue(error)
  await store.run('등록', operation)
  expect(store.getSnapshot().uncertain).toBe(true)
  expect(store.getSnapshot().notice).toContain('이미 처리됐을 수')
  await store.run('등록', operation)
  expect(operation).toHaveBeenCalledOnce()
  await store.reload()
  expect(store.getSnapshot().uncertain).toBe(true)
  store.acknowledge()
  expect(store.getSnapshot().uncertain).toBe(false)
})

it('blocks edits when post-write reload fails and retains the known save result', async () => {
  const { store, api } = setup()
  await store.reload()
  api.staff.mockRejectedValueOnce(new ApiError(0, 'NETWORK'))
  await store.run('저장', async () => undefined)
  expect(store.getSnapshot().notice).toContain('저장 완료')
  expect(store.getSnapshot().notice).toContain('최신 목록도 불러오지')
  expect(store.getSnapshot().stale).toBe(true)
  const operation = vi.fn()
  await store.run('저장', operation)
  expect(operation).not.toHaveBeenCalled()
  await store.reload()
  expect(store.getSnapshot().stale).toBe(false)
  expect(store.getSnapshot().notice).toBeNull()
})

it('does not publish a late write result or start post-write reads after leaving the screen', async () => {
  const { store, api } = setup()
  await store.reload()
  const response = gate<void>()
  const pending = store.run('저장', () => response.promise)
  store.dispose()
  response.resolve()
  await pending
  expect(api.staff).toHaveBeenCalledOnce()
  expect(await store.reload()).toBe(false)
  expect(api.staff).toHaveBeenCalledOnce()
})

it('cannot acknowledge an unknown outcome using an old list', async () => {
  const { store, api } = setup()
  await store.reload()
  api.staff.mockRejectedValue(new ApiError(0, 'NETWORK'))
  await store.run('등록', async () => { throw new ApiError(0, 'NETWORK') })
  store.acknowledge()
  expect(store.getSnapshot().uncertain).toBe(true)
})

it('clears all management data when the actor loses access after their own change', async () => {
  const { store, wards } = setup()
  await store.reload()
  wards.mockResolvedValue({ items: [] })
  await store.run('본인 비활성화', async () => undefined)
  expect(store.getSnapshot().forbidden).toBe(true)
  expect(store.getSnapshot().data).toBeNull()
})

it.each([
  ['TEAM_ORDER_STALE', '다시 정렬'], ['TEAM_NOT_EMPTY', '먼저 팀을 옮겨'], ['TEAM_NAME_DUPLICATE', '같은 이름'],
  ['LAST_WARD_ADMIN', '마지막 활성 관리자'], ['MEMBER_ROLE_REQUIRED', '관리자 전용'],
  ['ADMIN_NURSE_RECORD_CONFLICT', '먼저 비활성화'], ['MEMBER_INACTIVE', '접근을 먼저 재활성화'],
  ['RANK_NOT_IN_POLICY', '직급 목록'], ['INVALID_CAREER_START', '경력 시작 연월'],
])('explains %s without auto-retrying the write', async (code, message) => {
  const { store } = setup()
  await store.reload()
  const operation = vi.fn().mockRejectedValue(new ApiError(409, code))
  await store.run('저장', operation)
  expect(operation).toHaveBeenCalledOnce()
  expect(store.getSnapshot().notice).toContain(message)
  expect(store.getSnapshot().uncertain).toBe(false)
})
