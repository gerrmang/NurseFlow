import { createOffRequestApi, validDate } from './offRequestApi'
import { OFF_ITEM, OFF_LIST, OFF_POLICY, OFF_ELIGIBILITY } from '../test/offRequestFixtures'

it('encodes IDs, sends exact bodies and applies the policy ETag', async () => {
  const fetch = vi.fn().mockImplementation(async (path: string, init?: RequestInit) => {
    if (path.includes('roster-eligibility')) return Response.json(OFF_ELIGIBILITY)
    if (path.endsWith('/policy')) return Response.json(OFF_POLICY.value, { headers: { ETag: OFF_POLICY.etag } })
    if (path.includes('?month')) return Response.json(OFF_LIST)
    const action = path.split('/').at(-1)
    const body = init?.body ? JSON.parse(String(init.body)) : null
    return Response.json({ ...OFF_ITEM, requestId: 'r/a', ...(body ?? {}), source: body?.nurseId ? 'ADMIN' : 'NURSE', status: action === 'confirm' ? 'CONFIRMED' : action === 'reject' ? 'REJECTED' : action === 'withdraw' ? 'WITHDRAWN' : 'SUBMITTED' })
  })
  const api = createOffRequestApi(fetch, 'w/a')
  await api.list('2031-03'); await api.submit(OFF_ITEM.date); await api.submit(OFF_ITEM.date, OFF_ITEM.nurseId)
  for (const action of ['confirm', 'reject', 'withdraw'] as const) await api.decide('r/a', action)
  await api.policy(); await api.savePolicy({ monthlyLimit: 0, deadlineDay: 28 }, OFF_POLICY.etag); await api.eligibility()
  expect(fetch.mock.calls.map(([path, init]) => [path, init?.method ?? 'GET', init?.body ? JSON.parse(String(init.body)) : null])).toEqual([
    ['/wards/w%2Fa/off-requests?month=2031-03', 'GET', null],
    ['/wards/w%2Fa/off-requests', 'POST', { date: OFF_ITEM.date }],
    ['/wards/w%2Fa/off-requests', 'POST', { date: OFF_ITEM.date, nurseId: OFF_ITEM.nurseId }],
    ...['confirm', 'reject', 'withdraw'].map(action => [`/wards/w%2Fa/off-requests/r%2Fa/${action}`, 'POST', null]),
    ['/wards/w%2Fa/off-requests/policy', 'GET', null],
    ['/wards/w%2Fa/off-requests/policy', 'PUT', { monthlyLimit: 0, deadlineDay: 28 }],
    ['/wards/w%2Fa/roster-eligibility', 'GET', null],
  ])
  expect(new Headers(fetch.mock.calls[7]![1]?.headers).get('If-Match')).toBe(OFF_POLICY.etag)
  expect(fetch.mock.calls.every(([, init]) => !new Headers(init?.headers).has('Idempotency-Key'))).toBe(true)
})
it.each([null, {}, { ...OFF_LIST, items: null }, { ...OFF_LIST, deadline: 'tomorrow' }, { ...OFF_LIST, monthlyLimit: -1 }, { ...OFF_LIST, items: [{ ...OFF_ITEM, date: '2031-02-30' }] }, { ...OFF_LIST, items: [{ ...OFF_ITEM, date: '2031-04-01' }] }, { ...OFF_LIST, items: [OFF_ITEM, OFF_ITEM] }, { ...OFF_LIST, items: [{ ...OFF_ITEM, status: 'APPROVED' }] }])('rejects malformed or mixed-month lists %#', async value => {
  await expect(createOffRequestApi(async () => Response.json(value), 'w').list('2031-03')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
})
it.each([204, 205, 200])('rejects empty successful responses (%s)', async status => {
  await expect(createOffRequestApi(async () => new Response(null, { status }), 'w').submit(OFF_ITEM.date)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
})
it('keeps server errors and rejects mismatched mutations and ETags', async () => {
  await expect(createOffRequestApi(async () => Response.json({ code: 'OFF_REQUEST_DUPLICATE' }, { status: 409 }), 'w').submit(OFF_ITEM.date)).rejects.toMatchObject({ code: 'OFF_REQUEST_DUPLICATE', status: 409 })
  await expect(createOffRequestApi(async () => Response.json(OFF_ITEM), 'w').decide('another', 'confirm')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  await expect(createOffRequestApi(async () => Response.json(OFF_POLICY.value), 'w').policy()).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  await expect(createOffRequestApi(async () => Response.json({ ...OFF_POLICY.value, deadlineDay: 29 }, { headers: { ETag: OFF_POLICY.etag } }), 'w').policy()).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  await expect(createOffRequestApi(async () => Response.json({ ...OFF_ELIGIBILITY, items: [null] }), 'w').eligibility()).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
})
it.each([['2032-02-29', true], ['2031-02-29', false], ['0000-01-01', false], ['2031-04-31', false], ['0001-01-01', true]])('checks calendar date %s', (date, expected) => expect(validDate(date)).toBe(expected))
it('rejects a proxy response attributed to the nurse and impossible timestamp dates', async () => {
  await expect(createOffRequestApi(async () => Response.json(OFF_ITEM), 'w').submit(OFF_ITEM.date, OFF_ITEM.nurseId)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  await expect(createOffRequestApi(async () => Response.json({ ...OFF_LIST, deadline: '2031-02-30T00:00:00Z' }), 'w').list('2031-03')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
})
