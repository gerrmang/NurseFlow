import { createHolidaySettingsApi, policyInput, validPolicy } from './holidaySettingsApi'
import { HOLIDAY_CALENDAR as calendar, HOLIDAY_POLICY as policy } from '../test/holidayFixtures'
import { taggedJson } from '../test/scheduleFixtures'
it('uses the shared policy tag, whole ward list and nullable dedicated defaults', async () => {
  const fetch = vi.fn(async (_path: string, _init?: RequestInit) => taggedJson(calendar)); const api = createHolidaySettingsApi(fetch, 'ward/id')
  await api.saveCalendar('2026-11', [], '"p7"')
  expect(fetch).toHaveBeenLastCalledWith('/wards/ward%2Fid/holiday-calendar?month=2026-11', expect.objectContaining({ method: 'PUT', headers: expect.objectContaining({ 'If-Match': '"p7"' }), body: '{"days":[]}' }))
  fetch.mockResolvedValue(taggedJson(policy)); await api.savePolicy(policyInput(policy), '"p8"')
  const input = JSON.parse(fetch.mock.calls.at(-1)![1]!.body as string)
  expect(input.nightDedicatedTargetMinutes).toBeNull(); expect(input).not.toHaveProperty('policyRevision')
})
it.each([{ ...calendar, month: '2026-12' }, { ...calendar, days: [{ date: '2026-11-31', kind: 'WARD_REST_DAY' }] }, { ...calendar, target: {} }, { ...calendar, publicHolidays: [{ date: '2026-11-02', kind: 'WARD_WORKDAY' }] }])('rejects invalid calendar responses', async value => {
  await expect(createHolidaySettingsApi(async () => taggedJson(value), 'w').calendar('2026-11')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
})
it('rejects missing ETags and preserves server errors', async () => {
  await expect(createHolidaySettingsApi(async () => new Response(JSON.stringify(calendar)), 'w').calendar('2026-11')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  await expect(createHolidaySettingsApi(async () => taggedJson({ code: 'STALE_POLICY_REVISION' }, '"p2"', 412), 'w').saveCalendar('2026-11', [], '"p1"')).rejects.toMatchObject({ status: 412, code: 'STALE_POLICY_REVISION' })
})
it('validates integer limits and zero versus general defaults', () => {
  expect(validPolicy({ ...policyInput(policy), nightDedicatedTargetMinutes: 0 })).toBe(true)
  for (const patch of [{ dailyStandardMinutes: 0 }, { leaveCreditMinutes: 0.5 }, { nightDedicatedTargetMinutes: 44641 }]) expect(validPolicy({ ...policyInput(policy), ...patch })).toBe(false)
})
