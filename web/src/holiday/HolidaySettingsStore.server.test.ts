/// <reference types="node" />
// @vitest-environment node
// Fictional OIDC + actual Spring/H2 on isolated loopback ports. Never production.
import process from 'node:process'
import { ApiError, readJson } from '../api/http'
import { createHolidaySettingsApi } from '../api/holidaySettingsApi'
import { createNurseFlowApi } from '../api/nurseflowApi'
import type { HospitalCandidate, WardMembership, WebSession } from '../api/types'
import { HolidaySettingsStore } from './HolidaySettingsStore'

const base = 'http://127.0.0.1:18091/api/v1', origin = 'http://localhost:5174'
const headers = { 'Content-Type': 'application/json', Origin: origin, 'X-NurseFlow-Client': 'web' }
async function login(account: string) {
  const start = await fetch(`${base}/auth/web/kakao/authorization`, { method: 'POST', headers, body: JSON.stringify({ redirectUri: `${origin}/auth/kakao/callback` }) })
  const { authorizationUrl } = await readJson<{ authorizationUrl: string }>(start)
  const page = await fetch(authorizationUrl)
  const attempt = (await page.text()).match(/name="attempt" value="([^"]+)"/)?.[1]
  if (!attempt) throw new Error('Fictional fixture unavailable')
  const approved = await fetch('http://127.0.0.1:18090/approve', { method: 'POST', redirect: 'manual', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ attempt, account }) })
  const callback = new URL(approved.headers.get('location')!)
  return readJson<WebSession>(await fetch(`${base}/auth/web/kakao/code`, { method: 'POST', headers: { ...headers, Cookie: start.headers.getSetCookie().map(c => c.split(';')[0]).join('; ') }, body: JSON.stringify({ code: callback.searchParams.get('code'), state: callback.searchParams.get('state') }) }))
}
it.skipIf(process.env.NURSEFLOW_HOLIDAY_E2E !== '1')('persists calendar/policy with shared ETag, handles 412/503/lost response and denies members through real Spring', async () => {
  const session = await login('admin'), member = await login('nurse')
  let lose = false, writes = 0
  const authorized = (token: string) => async (path: string, init?: RequestInit) => {
    const h = new Headers(init?.headers); h.set('Authorization', `Bearer ${token}`); h.set('Origin', origin)
    const response = await fetch(base + path, { ...init, headers: h })
    if (init?.method === 'PUT') { ++writes; if (lose) { lose = false; expect(response.status).toBe(200); await response.arrayBuffer(); throw new ApiError(0, 'NETWORK') } }
    return response
  }
  const adminFetch = authorized(session.accessToken), account = createNurseFlowApi(adminFetch)
  const hospital = (await readJson<{ items: HospitalCandidate[] }>(await adminFetch('/hospitals/search?query=365'))).items[0]!
  const ward = await readJson<WardMembership>(await adminFetch('/wards', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify({ hospital: { source: hospital.source, externalId: hospital.externalId }, wardName: `가상 휴일 검증 ${crypto.randomUUID().slice(0, 6)}` }) }))
  const api = createHolidaySettingsApi(adminFetch, ward.wardId), store = new HolidaySettingsStore(api, () => account.getWards(), ward.wardId, '2026-11')
  expect(await store.reload()).toBe(true)
  expect(store.getSnapshot().calendar!.holidaySource.configured).toBe(false)
  store.editDay({ date: '2026-11-02', kind: 'WARD_REST_DAY', name: '가상 병동 휴무' })
  store.editPolicy({ ...store.getSnapshot().policy!, dailyStandardMinutes: 420, nightDedicatedTargetMinutes: 0 })
  const original = store.getSnapshot().etag
  await store.save('calendar'); expect(store.getSnapshot().etag).not.toBe(original)
  expect(store.getSnapshot().policy!.dailyStandardMinutes).toBe(420)
  expect(store.getSnapshot().calendar!.target).toMatchObject({ targetDays: 20, generalTargetMinutes: 9600 })
  await store.save('policy'); expect(store.getSnapshot().calendar!.target.generalTargetMinutes).toBe(8400)
  expect((await api.policy()).value.nightDedicatedTargetMinutes).toBe(0)
  store.editPolicy({ ...store.getSnapshot().policy!, nightDedicatedTargetMinutes: null })
  await store.save('policy'); expect((await api.policy()).value.nightDedicatedTargetMinutes).toBeNull()
  const reopened = new HolidaySettingsStore(api, () => account.getWards(), ward.wardId, '2026-11')
  await reopened.reload(); expect(reopened.getSnapshot().days).toHaveLength(1)
  await api.saveCalendar('2026-11', [], store.getSnapshot().etag)
  await store.save('policy'); expect(store.getSnapshot().stale).toBe(true); expect(store.getSnapshot().notice).toContain('다른 화면')
  await store.reload(); expect(store.getSnapshot().days).toEqual([])
  await store.save('refresh'); expect(store.editable()).toBe(true); expect(store.getSnapshot().notice).toContain('직접 입력')
  lose = true; store.editDay({ date: '2026-11-03', kind: 'WARD_WORKDAY' }); await store.save('calendar')
  expect(store.getSnapshot().uncertain).toBe(true); const count = writes
  await store.save('calendar'); expect(writes).toBe(count)
  await store.reload(); expect(store.getSnapshot().days).toHaveLength(1); store.acknowledge(); expect(store.editable()).toBe(true)
  const invitation = await account.createInvitation(ward.wardId, { role: 'WARD_MEMBER' })
  const memberFetch = authorized(member.accessToken)
  await createNurseFlowApi(memberFetch).redeemInvitation({ token: invitation.token, hospitalJoinedOn: '2020-01' })
  await expect(createHolidaySettingsApi(memberFetch, ward.wardId).calendar('2026-11')).rejects.toMatchObject({ status: 403 })
  await expect(createHolidaySettingsApi(memberFetch, ward.wardId).policy()).rejects.toMatchObject({ status: 403 })
}, 30000)
