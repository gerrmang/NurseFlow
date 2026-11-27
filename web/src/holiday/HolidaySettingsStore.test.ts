import { ApiError } from '../api/http'
import { policyInput, type HolidayDay } from '../api/holidaySettingsApi'
import { ADMIN_WARD } from '../test/staffFixtures'
import { HOLIDAY_CALENDAR as calendar, HOLIDAY_POLICY as policy } from '../test/holidayFixtures'
import { HolidaySettingsStore } from './HolidaySettingsStore'

function setup() {
  const tagged = <T,>(value: T) => ({ value: structuredClone(value), etag: '"p1"' })
  const api = { calendar: vi.fn(async () => tagged(calendar)), policy: vi.fn(async () => tagged(policy)), saveCalendar: vi.fn(async () => tagged(calendar)), savePolicy: vi.fn(async () => tagged(policy)), refresh: vi.fn(async () => tagged(calendar)) }
  const wards = vi.fn(async () => ({ items: [ADMIN_WARD] }))
  const store = new HolidaySettingsStore(api, wards, ADMIN_WARD.wardId, calendar.month)
  return { api, store, wards }
}
const day: HolidayDay = { date: '2026-11-03', kind: 'WARD_WORKDAY', name: null }
it('checks admin before protected reads', async () => {
  const { store, api, wards } = setup(); wards.mockResolvedValue({ items: [] })
  expect(await store.reload()).toBe(false); expect(api.calendar).not.toHaveBeenCalled(); expect(store.getSnapshot().forbidden).toBe(true)
})
it('blocks writes on mixed policy snapshots', async () => {
  const { store, api } = setup(); api.policy.mockResolvedValue({ value: { ...policy, policyRevision: 2 }, etag: '"p2"' })
  await store.reload(); expect(store.editable()).toBe(false); await store.save('calendar'); expect(api.saveCalendar).not.toHaveBeenCalled()
})
it('saves ward days only and preserves unsaved policy across calendar save', async () => {
  const { store, api } = setup(); await store.reload(); store.editDay(day); store.editPolicy({ ...policyInput(policy), dailyStandardMinutes: 420 })
  api.calendar.mockResolvedValue({ value: { ...calendar, days: [day], policyRevision: 2 }, etag: '"p2"' }); api.policy.mockResolvedValue({ value: { ...policy, policyRevision: 2 }, etag: '"p2"' })
  api.saveCalendar.mockResolvedValue({ value: { ...calendar, days: [day], policyRevision: 2 }, etag: '"p2"' })
  api.savePolicy.mockResolvedValue({ value: { ...policy, policyRevision: 2 }, etag: '"p2"' })
  await store.save('calendar'); expect(api.saveCalendar).toHaveBeenCalledWith(calendar.month, [day], '"p1"')
  expect(store.getSnapshot().policy?.dailyStandardMinutes).toBe(420); expect(store.getSnapshot().policyDirty).toBe(true)
  await store.save('policy'); expect(api.savePolicy).toHaveBeenCalledWith(expect.objectContaining({ dailyStandardMinutes: 420 }), '"p2"')
})
it('preserves unsaved calendar across policy save and refresh', async () => {
  const { store, api } = setup(); await store.reload(); store.editDay(day)
  await store.save('policy'); expect(store.getSnapshot().days).toEqual([day]); expect(store.getSnapshot().calendarDirty).toBe(true)
  await store.save('refresh'); expect(store.getSnapshot().days).toEqual([day]); expect(api.saveCalendar).not.toHaveBeenCalled()
})
it('does not rebase an unsaved draft onto another administrators post-save revision', async () => {
  const { store, api } = setup(); await store.reload(); store.editDay(day); store.editPolicy({ ...policyInput(policy), dailyStandardMinutes: 420 })
  api.saveCalendar.mockResolvedValue({ value: { ...calendar, policyRevision: 2 }, etag: '"p2"' })
  api.calendar.mockResolvedValue({ value: { ...calendar, policyRevision: 3 }, etag: '"p3"' }); api.policy.mockResolvedValue({ value: { ...policy, policyRevision: 3 }, etag: '"p3"' })
  await store.save('calendar'); expect(store.editable()).toBe(false); expect(store.getSnapshot().etag).toBe('"p1"')
  expect(store.getSnapshot().policy!.dailyStandardMinutes).toBe(420); await store.save('policy'); expect(api.savePolicy).not.toHaveBeenCalled()
})
it.each([400, 412])('keeps rejected input and prevents retry after conflict: %s', async status => {
  const { store, api } = setup(); await store.reload(); store.editDay(day)
  api.saveCalendar.mockRejectedValue(new ApiError(status, status === 412 ? 'STALE_POLICY_REVISION' : 'INVALID_HOLIDAY_CALENDAR'))
  await store.save('calendar'); expect(store.getSnapshot().days).toEqual([day]); expect(store.editable()).toBe(status === 400)
})
it.each([new ApiError(0, 'NETWORK'), new ApiError(503, 'UNAVAILABLE'), new ApiError(200, 'INVALID_RESPONSE')])('requires fresh result and acknowledgement after uncertain write %j', async error => {
  const { store, api } = setup(); await store.reload(); api.saveCalendar.mockRejectedValue(error)
  await store.save('calendar'); await store.save('calendar'); expect(api.saveCalendar).toHaveBeenCalledTimes(1)
  store.acknowledge(); expect(store.editable()).toBe(false)
  await store.reload(); expect(store.editable()).toBe(false); store.acknowledge(); expect(store.editable()).toBe(true)
})
it('keeps refresh failures editable and all local edits intact', async () => {
  const { store, api } = setup(); await store.reload(); store.editDay(day); api.refresh.mockRejectedValue(new ApiError(503, 'HOLIDAY_SOURCE_UNAVAILABLE'))
  await store.save('refresh'); expect(store.editable()).toBe(true); expect(store.getSnapshot().days).toEqual([day]); expect(store.getSnapshot().calendar).toEqual(calendar)
})
it('clears all protected data after revoked membership', async () => {
  const { store, api } = setup(); await store.reload(); store.editDay(day); api.saveCalendar.mockRejectedValue(new ApiError(403, 'FORBIDDEN'))
  await store.save('calendar'); expect(store.getSnapshot()).toMatchObject({ calendar: null, policy: null, days: [], forbidden: true })
})
it('ignores late reads after leaving the session', async () => {
  const { store, api } = setup(); let complete!: (v: Awaited<ReturnType<typeof api.calendar>>) => void
  api.calendar.mockImplementation(() => new Promise(resolve => { complete = resolve }))
  const load = store.reload(); await vi.waitFor(() => expect(api.calendar).toHaveBeenCalled()); store.dispose(); complete({ value: calendar, etag: '"p1"' }); await load
  expect(store.getSnapshot().calendar).toBeNull()
})
it('serializes saves and keeps writes locked after failed post-save reads', async () => {
  const { store, api } = setup(); await store.reload(); let complete!: () => void
  api.saveCalendar.mockImplementation(async () => { await new Promise<void>(resolve => { complete = resolve }); return { value: calendar, etag: '"p1"' } })
  const save = store.save('calendar'); await store.save('policy'); expect(api.savePolicy).not.toHaveBeenCalled()
  api.calendar.mockRejectedValue(new ApiError(503, 'UNAVAILABLE')); complete(); await save; expect(store.editable()).toBe(false)
})
