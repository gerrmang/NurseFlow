import { createScheduleSettingsApi } from './scheduleSettingsApi'
import { SHIFT, ROSTER, taggedJson } from '../test/scheduleFixtures'

it('preserves policy tags, original creation keys and explicit replacement fields', async () => {
  const fetch = vi.fn().mockImplementation(async () => taggedJson(SHIFT))
  const api = createScheduleSettingsApi(fetch, 'ward/a')
  const body = { code: SHIFT.code, displayName: SHIFT.displayName, kind: 'DAY' as const, startTime: SHIFT.startTime, endTime: SHIFT.endTime, spansMidnight: false, breakMinutes: 0, recognizedMinutes: 480, targetCreditMinutes: 480 }
  await api.createShift(body, '"policy-0"', 'fixed-key')
  await api.updateShift('shift/b', { ...body, active: false, targetCreditMinutes: 0 }, '"policy-1"')
  expect(fetch.mock.calls[0]).toEqual(['/wards/ward%2Fa/shift-types', expect.objectContaining({ method: 'POST', headers: expect.objectContaining({ 'If-Match': '"policy-0"', 'Idempotency-Key': 'fixed-key' }), body: JSON.stringify(body) })])
  expect(fetch.mock.calls[1]![0]).toBe('/wards/ward%2Fa/shift-types/shift%2Fb')
  expect(JSON.parse(fetch.mock.calls[1]![1].body)).toMatchObject({ active: false, targetCreditMinutes: 0 })
})
it('sends roster revisions for replacement and all absence writes, with idempotency only on create', async () => {
  const absence = { absenceId: 'absence', nurseId: 'nurse', kind: 'APPROVED_HALF_DAY' as const, startsAt: '2026-11-02T00:00:00Z', endsAt: '2026-11-02T04:00:00Z' }
  const fetch = vi.fn().mockResolvedValueOnce(taggedJson(ROSTER, '"roster-r2"')).mockResolvedValueOnce(taggedJson(absence, '"roster-r3"')).mockResolvedValueOnce(taggedJson(absence, '"roster-r4"')).mockResolvedValueOnce(new Response(null, { status: 204 }))
  const api = createScheduleSettingsApi(fetch, 'ward')
  await api.requirements('roster/a', [], '"roster-r1"')
  const input = { nurseId: absence.nurseId, kind: absence.kind, startsAt: absence.startsAt, endsAt: absence.endsAt }
  await api.saveAbsence('roster/a', input, '"roster-r2"', undefined, 'key')
  await api.saveAbsence('roster/a', input, '"roster-r3"', 'absence/b')
  await api.deleteAbsence('roster/a', 'absence/b', '"roster-r4"')
  expect(fetch.mock.calls.map(([path, init]) => [path, init.method, init.headers['If-Match'], init.headers['Idempotency-Key']])).toEqual([
    ['/rosters/roster%2Fa/requirements', 'PUT', '"roster-r1"', undefined], ['/rosters/roster%2Fa/absences', 'POST', '"roster-r2"', 'key'], ['/rosters/roster%2Fa/absences/absence%2Fb', 'PUT', '"roster-r3"', undefined], ['/rosters/roster%2Fa/absences/absence%2Fb', 'DELETE', '"roster-r4"', undefined],
  ])
})
it.each([null, {}, { items: null }, { items: [], policyRevision: '1' }, { items: [{}], policyRevision: 1 }])('rejects malformed successful shift lists: %j', async value => {
  const api = createScheduleSettingsApi(async () => taggedJson(value), 'ward')
  await expect(api.shifts()).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
})
it('rejects missing tags and preserves stable server errors', async () => {
  const api = createScheduleSettingsApi(vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ items: [], policyRevision: 0 }))).mockResolvedValueOnce(taggedJson({ code: 'STALE_POLICY_REVISION' }, '"policy-1"', 412)), 'ward')
  await expect(api.shifts()).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  await expect(api.shifts()).rejects.toMatchObject({ status: 412, code: 'STALE_POLICY_REVISION' })
})
it.each([204, 205, 200])('rejects bodyless successful pattern responses (%i)', async status => {
  const api = createScheduleSettingsApi(async () => new Response(null, { status }), 'ward')
  await expect(api.pattern('nurse')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
})
it('rejects malformed absence instants before they reach the renderer', async () => {
  const api = createScheduleSettingsApi(async () => taggedJson({ items: [{ absenceId: 'a', nurseId: 'n', kind: 'APPROVED_LEAVE', startsAt: 'invalid', endsAt: 'invalid' }] }), 'ward')
  await expect(api.absences('roster')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
})
it.each([{ ...ROSTER, timeZone: 'Invalid/Zone' }, { ...ROSTER, staffing: {} }])('rejects unrenderable roster metadata', async value => {
  const api = createScheduleSettingsApi(async () => taggedJson(value), 'ward')
  await expect(api.roster('roster')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
})
