import { ApiError } from '../api/http'
import { createScheduleSettingsApi } from '../api/scheduleSettingsApi'
import { ScheduleSettingsStore } from './ScheduleSettingsStore'
import { ADMIN_WARD, NURSE_A, TEAM_A, SHIFT, ROSTER } from '../test/scheduleFixtures'

function setup() {
  const api = { ...createScheduleSettingsApi(vi.fn(), ADMIN_WARD.wardId), shifts: vi.fn().mockResolvedValue({ value: { items: [SHIFT], policyRevision: 1 }, etag: '"policy-1"' }), rosters: vi.fn().mockResolvedValue({ items: [ROSTER] }), roster: vi.fn().mockResolvedValue({ value: ROSTER, etag: '"roster-r1"' }), absences: vi.fn().mockResolvedValue([]), preview: vi.fn() }
  const staff = { teams: vi.fn().mockResolvedValue([TEAM_A]), staff: vi.fn().mockResolvedValue([NURSE_A]) }
  const wards = vi.fn().mockResolvedValue({ items: [ADMIN_WARD] })
  const store = new ScheduleSettingsStore(api, staff, wards, ADMIN_WARD.wardId, '2026-11')
  return { store, api, wards, staff }
}
it('does not load management data for members', async () => {
  const { store, wards, api } = setup(); wards.mockResolvedValue({ items: [{ ...ADMIN_WARD, roles: ['WARD_MEMBER'] }] })
  await store.reload(); expect(store.getSnapshot().forbidden).toBe(true); expect(api.shifts).not.toHaveBeenCalled()
})
it('refuses to edit mixed revisions read across concurrent absence changes', async () => {
  const { store, api } = setup(); api.roster.mockResolvedValueOnce({ value: ROSTER, etag: '"roster-r1"' }).mockResolvedValueOnce({ value: { ...ROSTER, revision: 2 }, etag: '"roster-r2"' })
  expect(await store.reload()).toBe(false); expect(store.canWrite()).toBe(false)
})
it('serializes writes and reloads server revisions after success', async () => {
  const { store } = setup(); await store.reload()
  let complete!: () => void
  const operation = vi.fn(() => new Promise<void>(resolve => { complete = resolve }))
  const first = store.run('설정', operation); expect(await store.run('설정', operation)).toBe(false)
  expect(operation).toHaveBeenCalledOnce(); complete(); expect(await first).toBe(true); expect(store.canWrite()).toBe(true)
})
it.each(['STALE_POLICY_REVISION', 'STALE_ROSTER_REVISION', 'ASSIGNMENTS_EXIST'])('reloads without retrying rejected writes: %s', async code => {
  const { store, api } = setup(); await store.reload(); const operation = vi.fn().mockRejectedValue(new ApiError(code.startsWith('STALE') ? 412 : 409, code))
  const version = store.getSnapshot().version
  await store.run('설정', operation); expect(operation).toHaveBeenCalledOnce(); expect(api.shifts).toHaveBeenCalledTimes(2); expect(store.getSnapshot().uncertain).toBe(false); expect(store.getSnapshot().notice).toMatch(/변경|배치/)
  expect(store.getSnapshot().version).toBe(version + 1)
})
it.each(['DUPLICATE_REQUIREMENT', 'TEAM_REQUIRED_FOR_DEMAND', 'TOO_MANY_SLOTS', 'SHIFT_TYPE_CODE_DUPLICATE', 'INVALID_ABSENCE'])('preserves the editable snapshot after validation rejection: %s', async code => {
  const { store, api } = setup(); await store.reload()
  const before = store.getSnapshot()
  expect(await store.run('저장', async () => { throw new ApiError(400, code) })).toBe(false)
  expect(store.getSnapshot().data).toBe(before.data)
  expect(store.getSnapshot().version).toBe(before.version)
  expect(api.shifts).toHaveBeenCalledOnce()
  expect(store.canWrite()).toBe(true)
  expect(store.getSnapshot().uncertain).toBe(false)
})
it('preserves the editable snapshot after preview validation rejection', async () => {
  const { store, api } = setup(); await store.reload()
  const before = store.getSnapshot()
  api.preview.mockRejectedValue(new ApiError(400, 'INVALID_SHIFT_MINUTES'))
  expect(await store.preview(SHIFT.shiftTypeId, { ...SHIFT, kind: 'DAY' })).toBeNull()
  expect(store.getSnapshot().version).toBe(before.version)
  expect(api.shifts).toHaveBeenCalledOnce()
  expect(store.canWrite()).toBe(true)
})
it.each([new ApiError(0, 'NETWORK'), new ApiError(503, 'UNAVAILABLE'), new ApiError(200, 'INVALID_RESPONSE')])('blocks resending uncertain writes until a fresh result is acknowledged: %j', async error => {
  const { store } = setup(); await store.reload(); const operation = vi.fn().mockRejectedValue(error)
  await store.run('설정', operation); expect(store.getSnapshot().uncertain).toBe(true); expect(store.getSnapshot().data).not.toBeNull()
  await store.run('재전송', operation); expect(operation).toHaveBeenCalledOnce()
  store.acknowledge(); expect(store.canWrite()).toBe(true)
})
it('keeps writes locked when save succeeded but reloading failed', async () => {
  const { store, api } = setup(); await store.reload(); api.shifts.mockRejectedValue(new ApiError(503, 'UNAVAILABLE'))
  expect(await store.run('저장', async () => {})).toBe(false); expect(store.canWrite()).toBe(false); expect(store.getSnapshot().notice).toContain('재조회도 실패')
})
it.each([401, 403])('discards data immediately when write loses authorization: %i', async status => {
  const { store } = setup(); await store.reload(); await store.run('저장', async () => { throw new ApiError(status, 'FORBIDDEN') })
  expect(store.getSnapshot()).toMatchObject({ data: null, forbidden: true }); expect(store.canWrite()).toBe(false)
})
it('discards late reads after month or session changes', async () => {
  const { store, api } = setup(); let resolve!: (value: unknown) => void
  api.shifts.mockImplementationOnce(() => new Promise(r => { resolve = r }))
  const old = store.reload(); await vi.waitFor(() => expect(api.shifts).toHaveBeenCalledOnce())
  api.rosters.mockResolvedValue({ items: [] }); await store.setMonth('2026-12')
  resolve({ value: { items: [SHIFT], policyRevision: 1 }, etag: '"policy-1"' }); await old
  expect(store.getSnapshot().month).toBe('2026-12'); expect(store.getSnapshot().data?.draft).toBeNull()
  store.dispose(); expect(store.canWrite()).toBe(false)
})
it('does not show a preview from an outdated policy snapshot', async () => {
  const { store, api } = setup(); await store.reload(); api.preview.mockResolvedValue({ policyRevision: 2 })
  expect(await store.preview(SHIFT.shiftTypeId, { code: 'D', displayName: 'Day', kind: 'DAY', startTime: '07:00', endTime: '15:00', spansMidnight: false, breakMinutes: 0 })).toBeNull()
  expect(store.getSnapshot().notice).toContain('다른 화면에서 변경')
})
