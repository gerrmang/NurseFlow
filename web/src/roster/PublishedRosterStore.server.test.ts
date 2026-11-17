/// <reference types="node" />
// @vitest-environment node
// Only the separate fictional OIDC fixture and in-memory backend documented in B9b-verification.md.
import process from 'node:process'
import { readJson } from '../api/http'
import { createNurseFlowApi } from '../api/nurseflowApi'
import { createPublishedRosterApi } from '../api/publishedRosterApi'
import type { components } from '../api/schema.gen'
import type { WardMembership, WebSession } from '../api/types'
import { PublishedRosterStore } from './PublishedRosterStore'

type Schemas = components['schemas']
const base = 'http://127.0.0.1:18091/api/v1'
const origin = 'http://localhost:5174'
const headers = { 'Content-Type': 'application/json', Origin: origin, 'X-NurseFlow-Client': 'web' }
async function login(account: string) {
  const start = await fetch(`${base}/auth/web/kakao/authorization`, { method: 'POST', headers, body: JSON.stringify({ redirectUri: `${origin}/auth/kakao/callback` }) })
  const { authorizationUrl } = await readJson<{ authorizationUrl: string }>(start)
  const page = await fetch(authorizationUrl)
  const attempt = (await page.text()).match(/name="attempt" value="([^"]+)"/)?.[1]
  if (!attempt) throw new Error('Fictional login fixture unavailable')
  const approved = await fetch('http://127.0.0.1:18090/approve', { method: 'POST', redirect: 'manual', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ attempt, account }) })
  const callback = new URL(approved.headers.get('location')!)
  return readJson<WebSession>(await fetch(`${base}/auth/web/kakao/code`, {
    method: 'POST', headers: { ...headers, Cookie: start.headers.getSetCookie().map(cookie => cookie.split(';')[0]).join('; ') },
    body: JSON.stringify({ code: callback.searchParams.get('code'), state: callback.searchParams.get('state') }),
  }))
}

it.skipIf(process.env.NURSEFLOW_B9B_E2E !== '1')('reads publication, personal scope, re-publication and access errors through the real API and client store', async () => {
  const admin = await login('admin'), nurse = await login('nurse'), outsider = await login('admin2')
  const authorized = (token: string) => async (path: string, init?: RequestInit) => {
    const requestHeaders = new Headers(headers)
    new Headers(init?.headers).forEach((value, key) => requestHeaders.set(key, value))
    requestHeaders.set('Authorization', `Bearer ${token}`)
    return fetch(base + path, { ...init, headers: requestHeaders })
  }
  const adminFetch = authorized(admin.accessToken), nurseFetch = authorized(nurse.accessToken), otherFetch = authorized(outsider.accessToken)
  const adminAccount = createNurseFlowApi(adminFetch), nurseAccount = createNurseFlowApi(nurseFetch)
  const write = (method: string, body: unknown, extra = {}): RequestInit => ({ method, headers: { 'Idempotency-Key': crypto.randomUUID(), ...extra }, body: JSON.stringify(body) })
  const hospitals = await readJson<{ items: Schemas['HospitalCandidate'][] }>(await adminFetch('/hospitals/search?query=365'))
  const hospital = hospitals.items[0]!
  const ward = await readJson<WardMembership>(await adminFetch('/wards', write('POST', { hospital: { source: hospital.source, externalId: hospital.externalId }, wardName: `B9b 가상 공개 검증 ${crypto.randomUUID().slice(0, 8)}`, timeZone: 'Asia/Seoul' })))
  const invited = await adminAccount.createInvitation(ward.wardId, { role: 'WARD_MEMBER' })
  await nurseAccount.redeemInvitation({ token: invited.token, hospitalJoinedOn: '2024-01' })
  const staff = await adminAccount.listActiveStaff(ward.wardId)
  const linked = staff.find(item => item.linkedAccount)!
  expect(linked).toBeTruthy()
  const now = new Date(), month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  const adminApi = createPublishedRosterApi(adminFetch), nurseApi = createPublishedRosterApi(nurseFetch)
  const store = new PublishedRosterStore(ward.wardId, nurseApi, nurseAccount, now)
  await store.load(); expect(store.getState().error?.code).toBe('PUBLISHED_ROSTER_NOT_FOUND')
  await expect(adminApi.mine(ward.wardId, month)).rejects.toMatchObject({ code: 'NURSE_IDENTITY_REQUIRED' })
  const policy = await adminFetch(`/wards/${ward.wardId}/shift-types`)
  const shift = await readJson<Schemas['ShiftType']>(await adminFetch(`/wards/${ward.wardId}/shift-types`, write('POST', { code: 'N', displayName: '가상 야간', kind: 'NIGHT', startTime: '22:00', endTime: '07:00', spansMidnight: true }, { 'If-Match': policy.headers.get('etag')! })))

  async function draft(day: string, count: number) {
    const created = await adminFetch(`/wards/${ward.wardId}/rosters`, write('POST', { month }))
    const initial = await readJson<Schemas['Roster']>(created)
    await readJson(await adminFetch(`/rosters/${initial.rosterId}/requirements`, write('PUT', { items: [{ date: `${month}-${day}`, shiftTypeId: shift.shiftTypeId, requiredCount: count, requiredQualifications: [] }] }, { 'If-Match': created.headers.get('etag')! })))
    const current = await adminFetch(`/rosters/${initial.rosterId}`)
    const roster = await readJson<Schemas['Roster']>(current)
    const assigned = await adminFetch(`/rosters/${roster.rosterId}/slots/${roster.slots[0]!.slotId}/assignment`, write('PUT', { nurseId: linked.nurseId, locked: false }, { 'If-Match': current.headers.get('etag')! }))
    await readJson(assigned)
    return { id: roster.rosterId, etag: assigned.headers.get('etag')! }
  }
  const first = await draft('01', 2)
  await store.load(); expect(store.getState().error?.code).toBe('PUBLISHED_ROSTER_NOT_FOUND')
  await readJson(await adminFetch(`/rosters/${first.id}/confirm`, write('POST', { acknowledgeUnfilledCount: 1 }, { 'If-Match': first.etag })))
  await store.load()
  expect(store.getState().result?.mode).toBe('mine')
  const own = await nurseApi.mine(ward.wardId, month)
  expect(own.assignments).toHaveLength(1); expect(own.assignments[0]!.shiftType).toBe('N')
  expect(own.assignments[0]!.date).toBe(`${month}-01`); expect(own.timeZone).toBe('Asia/Seoul')
  const published = await adminApi.ward(ward.wardId, month)
  expect(published.slots).toHaveLength(2); expect(published.staffing.unfilled).toBe(1)
  const response = await nurseFetch(`/wards/${ward.wardId}/published-rosters/mine?month=${month}`)
  expect(response.headers.get('cache-control')).toContain('no-store')
  expect(await response.json()).not.toHaveProperty('absences')
  const revised = await draft('02', 1)
  expect((await nurseApi.mine(ward.wardId, month)).assignments[0]!.date).toBe(`${month}-01`)
  await readJson(await adminFetch(`/rosters/${revised.id}/confirm`, write('POST', {}, { 'If-Match': revised.etag })))
  await store.load()
  const state = store.getState()
  expect(state.result?.mode === 'mine' && state.result.calendar.assignments[0]!.date).toBe(`${month}-02`)
  await expect(createPublishedRosterApi(otherFetch).ward(ward.wardId, month)).rejects.toMatchObject({ code: 'WARD_NOT_FOUND', status: 404 })
  const emptyInvite = await adminAccount.createInvitation(ward.wardId, { role: 'WARD_MEMBER' })
  await createNurseFlowApi(otherFetch).redeemInvitation({ token: emptyInvite.token, hospitalJoinedOn: '2024-01' })
  expect((await createPublishedRosterApi(otherFetch).mine(ward.wardId, month)).assignments).toEqual([])
  const adminStore = new PublishedRosterStore(ward.wardId, adminApi, adminAccount, now)
  await adminStore.load(); expect(adminStore.getState().result?.mode).toBe('ward')
  store.stop(); adminStore.stop()
}, 60_000)
