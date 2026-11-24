import { ApiError } from '../api/http'
import type { PublishedRosterApi, PersonalCalendar, WardCalendar } from '../api/publishedRosterApi'
import type { WardMembership } from '../api/types'
import { moveMonth, PublishedRosterStore, validMonth } from './PublishedRosterStore'
import { monthDates, shiftTime } from './calendar'

const ward: WardMembership = { wardId: 'ward-a', wardName: '가상 병동', roles: ['WARD_MEMBER'], canViewWardCalendar: true }
const mine = (month = '2026-11', revision = 1): PersonalCalendar => ({ wardId: ward.wardId, month, revision, timeZone: 'Asia/Seoul', assignments: [] })
const whole = (): WardCalendar => ({ ...mine(), staffing: { required: 0, assigned: 0, unfilled: 0, hardRuleViolations: 0 }, slots: [] })
function setup(member = ward) {
  const api = { mine: vi.fn<PublishedRosterApi['mine']>().mockResolvedValue(mine()), ward: vi.fn<PublishedRosterApi['ward']>().mockResolvedValue(whole()) }
  const memberships = { getWards: vi.fn().mockResolvedValue({ items: [member] }) }
  return { api, memberships, store: new PublishedRosterStore(ward.wardId, api, memberships, new Date(2026, 10, 1)) }
}
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done }); return { promise, resolve } }

it('defaults to mine for nurses and ward for admins with calendar permission', async () => {
  const nurse = setup(); await nurse.store.load()
  expect(nurse.api.mine).toHaveBeenCalledWith('ward-a', '2026-11')
  const admin = setup({ ...ward, roles: ['WARD_ADMIN'] }); await admin.store.load()
  expect(admin.api.ward).toHaveBeenCalledOnce()
})
it('checks membership before accessing a calendar, including direct URL access', async () => {
  const { store, api, memberships } = setup()
  memberships.getWards.mockResolvedValue({ items: [] }); await store.load()
  expect(api.mine).not.toHaveBeenCalled(); expect(api.ward).not.toHaveBeenCalled()
  expect(store.getState().error?.code).toBe('WARD_NOT_FOUND')
})
it('blocks ward mode without permission and adapts when permission is removed on reload', async () => {
  const { store, memberships, api } = setup({ ...ward, roles: ['WARD_ADMIN'] })
  await store.load()
  memberships.getWards.mockResolvedValue({ items: [{ ...ward, canViewWardCalendar: false }] })
  await store.load(); store.selectMode('ward')
  expect(store.getState().mode).toBe('mine'); expect(api.ward).toHaveBeenCalledTimes(1)
})
it.each([[404, 'PUBLISHED_ROSTER_NOT_FOUND'], [403, 'NURSE_IDENTITY_REQUIRED'], [404, 'WARD_NOT_FOUND'], [403, 'FORBIDDEN'], [0, 'NETWORK']])('keeps distinct error %s %s and clears old data', async (status, code) => {
  const { store, api } = setup(); await store.load()
  api.mine.mockRejectedValueOnce(new ApiError(status as number, code as string)); await store.load()
  expect(store.getState().result).toBeNull(); expect(store.getState().error).toEqual({ status, code })
  if (code === 'WARD_NOT_FOUND' || code === 'FORBIDDEN') expect(store.getState().ward).toBeNull()
})
it('clears old results immediately and discards responses from an older month', async () => {
  const { store, api } = setup(); await store.load()
  const old = deferred<PersonalCalendar>(); api.mine.mockReturnValueOnce(old.promise)
  const previous = store.load(); await vi.waitFor(() => expect(api.mine).toHaveBeenCalledTimes(2))
  api.mine.mockResolvedValueOnce(mine('2026-12', 2)); store.selectMonth('2026-12')
  expect(store.getState().result).toBeNull()
  await vi.waitFor(() => expect(store.getState().result?.calendar.revision).toBe(2))
  old.resolve(mine()); await previous
  expect(store.getState().result?.calendar.month).toBe('2026-12')
})
it('discards an older mode response and reads the latest published revision on reentry', async () => {
  const { store, api } = setup(); const old = deferred<PersonalCalendar>(); api.mine.mockReturnValueOnce(old.promise)
  const pending = store.load(); await vi.waitFor(() => expect(api.mine).toHaveBeenCalledOnce())
  store.selectMode('ward'); await vi.waitFor(() => expect(store.getState().status).toBe('ready'))
  old.resolve(mine()); await pending; expect(store.getState().result?.mode).toBe('ward')
  store.stop(); api.ward.mockResolvedValueOnce({ ...whole(), revision: 8 }); await store.load()
  expect(store.getState().result?.calendar.revision).toBe(8)
})
it('ignores in-flight membership and calendar responses after leaving', async () => {
  const a = setup(); const memberships = deferred<{ items: WardMembership[] }>()
  a.memberships.getWards.mockReturnValueOnce(memberships.promise); const load = a.store.load(); a.store.stop()
  memberships.resolve({ items: [ward] }); await load; expect(a.api.mine).not.toHaveBeenCalled()
  const b = setup(); const calendar = deferred<PersonalCalendar>(); b.api.mine.mockReturnValueOnce(calendar.promise)
  const pending = b.store.load(); await vi.waitFor(() => expect(b.api.mine).toHaveBeenCalledOnce()); b.store.stop()
  calendar.resolve(mine()); await pending; expect(b.store.getState().result).toBeNull()
})
it('rejects a calendar for another ward or month', async () => {
  const { store, api } = setup(); api.mine.mockResolvedValueOnce({ ...mine(), wardId: 'other' }); await store.load()
  expect(store.getState().error?.code).toBe('INVALID_RESPONSE')
})
it('does not query invalid month inputs', async () => {
  const { store, api } = setup()
  for (const input of ['', '0000-01', '2026-13', '10000-01']) store.selectMonth(input)
  expect(api.mine).not.toHaveBeenCalled(); expect(store.getState().month).toBe('2026-11')
})
it('handles leap years and year boundaries without interpreting small years as 1900', () => {
  expect(monthDates('2028-02').filter(Boolean)).toHaveLength(29)
  expect(monthDates('2026-02').filter(Boolean)).toHaveLength(28)
  expect(monthDates('0001-01').filter(Boolean)).toHaveLength(31)
  expect(moveMonth('2026-12', 1)).toBe('2027-01'); expect(moveMonth('2026-01', -1)).toBe('2025-12')
  expect(validMonth(moveMonth('0001-01', -1))).toBe(false); expect(validMonth(moveMonth('9999-12', 1))).toBe(false)
})
it('uses the ward timezone and includes a next-day end date for night shifts', () => {
  expect(shiftTime('2026-11-01T13:00:00Z', '2026-11-01T22:00:00Z', 'Asia/Seoul')).toBe('22:00 → 2026-11-02 07:00')
})
