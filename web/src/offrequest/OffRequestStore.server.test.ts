/// <reference types="node" />
// @vitest-environment node
import process from 'node:process'
import { resolve } from 'node:path'
// @ts-expect-error Existing repository-owned Java/fictional OIDC lifecycle helper is JavaScript.
import { startRuntime } from '../../../scripts/demo/runtime.mjs'
import { createNurseFlowApi, type AuthorizedFetch } from '../api/nurseflowApi'
import { createOffRequestApi } from '../api/offRequestApi'
import { readJson } from '../api/http'
import type { components } from '../api/schema.gen'
import { OffRequestStore } from './OffRequestStore'

type S = components['schemas']
type DemoApi = (method: string, path: string, body?: unknown, headers?: Record<string, string>) => Promise<{ data: unknown; etag: string | null }>
type Runtime = { login: (subject?: string) => Promise<DemoApi>; stop: () => Promise<void> }
// Transport bridge uses real signed sessions and real server data; no tokens are printed or persisted.
const bridge = (api: DemoApi): AuthorizedFetch => async (path, init) => {
  try {
    const { data, etag } = await api(init?.method ?? 'GET', `/api/v1${path}`, init?.body ? JSON.parse(String(init.body)) : undefined, Object.fromEntries(new Headers(init?.headers).entries()))
    return Response.json(data, { headers: etag ? { ETag: etag } : {} })
  } catch (e) {
    const status = e instanceof Error ? /HTTP (\d+)/.exec(e.message)?.[1] : undefined
    if (status) return Response.json({ code: `HTTP_${status}` }, { status: Number(status) })
    throw e
  }
}

it.skipIf(process.env.NURSEFLOW_B8B_E2E !== '1')('verifies nurse/admin lifecycle, policy ETag, late exceptions, draft invalidation and publication preservation against isolated Spring', async () => {
  const runtime: Runtime = await startRuntime({ jar: resolve('../backend/build/libs/nurseflow-backend-0.1.0-SNAPSHOT.jar'), port: 18098 })
  try {
    const admin = bridge(await runtime.login('b8b-admin')), nurse = bridge(await runtime.login('b8b-nurse')), outsider = bridge(await runtime.login('b8b-outsider'))
    const adminAccount = createNurseFlowApi(admin), nurseAccount = createNurseFlowApi(nurse)
    const write = (method: string, body: unknown, extra: Record<string, string> = {}) => ({ method, body: JSON.stringify(body), headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID(), ...extra } })
    const hospitals = await readJson<S['HospitalSearchPage']>(await admin('/hospitals/search?query=가상'))
    const hospital = hospitals.items[0]!
    expect(hospital).toBeTruthy()
    const ward = await readJson<S['WardMembership']>(await admin('/wards', write('POST', { hospital: { source: hospital.source, externalId: hospital.externalId }, wardName: 'B8b 가상 검증 병동', timeZone: 'Asia/Seoul' })))
    const wardPath = `/wards/${ward.wardId}`
    const invite = await adminAccount.createInvitation(ward.wardId, { role: 'WARD_MEMBER' })
    await nurseAccount.redeemInvitation({ token: invite.token, hospitalJoinedOn: '2024-01' })
    const linked = (await adminAccount.listActiveStaff(ward.wardId))[0]!
    const team = await readJson<S['Team']>(await admin(`${wardPath}/teams`, write('POST', { name: '가상팀' })))
    await readJson(await admin(`${wardPath}/staff/${linked.nurseId}/team`, write('PUT', { teamId: team.teamId })))
    await readJson(await admin(`${wardPath}/staff/${linked.nurseId}/roster-settings`, write('PUT', { seniorEligible: true, stageOverride: 4 })))
    const adminApi = createOffRequestApi(admin, ward.wardId), nurseApi = createOffRequestApi(nurse, ward.wardId)
    const initial = await adminApi.policy()
    await adminApi.savePolicy({ monthlyLimit: 1, deadlineDay: 15 }, initial.etag)
    await expect(adminApi.savePolicy({ monthlyLimit: 2, deadlineDay: 15 }, initial.etag)).rejects.toMatchObject({ status: 412 })
    const future = new Date(); future.setUTCMonth(future.getUTCMonth() + 2, 1)
    const month = future.toISOString().slice(0, 7)
    const adminStore = new OffRequestStore(ward.wardId, adminApi, adminAccount, future)
    const memberStore = new OffRequestStore(ward.wardId, nurseApi, nurseAccount, future)
    await adminStore.reload(); await memberStore.reload()
    expect(memberStore.getSnapshot().data?.list.items).toEqual([])
    expect(await memberStore.submit(`${month}-03`)).toBe(true)
    expect(memberStore.getSnapshot().data?.list.items[0]).toMatchObject({ source: 'NURSE', status: 'SUBMITTED', afterDeadline: false })
    await expect(nurseApi.submit(`${month}-04`)).rejects.toMatchObject({ status: 409 })
    const firstId = memberStore.getSnapshot().data!.list.items[0]!.requestId
    expect(await memberStore.decide(firstId, 'withdraw')).toBe(true)
    expect(await memberStore.submit(`${month}-03`)).toBe(true)
    const nurseId = memberStore.getSnapshot().data!.list.items.find(item => item.status === 'SUBMITTED')!.requestId
    await adminStore.reload(); expect(await adminStore.submit(`${month}-04`, linked.nurseId)).toBe(true)
    expect((await nurseApi.list(month)).items.filter(item => item.status === 'SUBMITTED')).toHaveLength(2)
    expect((await nurse(`${wardPath}/off-requests/policy`)).status).toBe(403)
    expect((await nurse(`${wardPath}/off-requests/${nurseId}/confirm`, { method: 'POST' })).status).toBe(403)
    expect((await outsider(`${wardPath}/off-requests?month=${month}`)).status).toBe(404)

    const shiftResponse = await admin(`${wardPath}/shift-types`)
    const shift = await readJson<S['ShiftType']>(await admin(`${wardPath}/shift-types`, write('POST', { code: 'D', displayName: '가상 데이', kind: 'DAY', startTime: '07:00', endTime: '15:00', spansMidnight: false }, { 'If-Match': shiftResponse.headers.get('etag')! })))
    const makeDraft = async () => {
      const res = await admin(`${wardPath}/rosters`, write('POST', { month }))
      const roster = await readJson<S['Roster']>(res)
      await readJson(await admin(`/rosters/${roster.rosterId}/requirements`, write('PUT', { items: [{ date: `${month}-03`, teamId: team.teamId, shiftTypeId: shift.shiftTypeId, requiredCount: 1, requiredQualifications: [] }] }, { 'If-Match': res.headers.get('etag')! })))
      const current = await admin(`/rosters/${roster.rosterId}`)
      const value = await readJson<S['Roster']>(current)
      const assigned = await admin(`/rosters/${roster.rosterId}/slots/${value.slots[0]!.slotId}/assignment`, write('PUT', { nurseId: linked.nurseId, locked: false }, { 'If-Match': current.headers.get('etag')! }))
      await readJson(assigned)
      return { id: roster.rosterId, etag: assigned.headers.get('etag')! }
    }
    const publishedDraft = await makeDraft()
    await readJson(await admin(`/rosters/${publishedDraft.id}/confirm`, write('POST', {}, { 'If-Match': publishedDraft.etag })))
    const published = await readJson<S['PublishedWardCalendar']>(await admin(`${wardPath}/published-rosters?month=${month}`))
    const draft = await makeDraft()
    const before = await readJson<S['Roster']>(await admin(`/rosters/${draft.id}`))
    await adminStore.reload(); expect(await adminStore.decide(nurseId, 'confirm')).toBe(true)
    const after = await readJson<S['Roster']>(await admin(`/rosters/${draft.id}`))
    expect(after.revision).toBeGreaterThan(before.revision)
    expect(await readJson(await admin(`${wardPath}/published-rosters?month=${month}`))).toEqual(published)
    const current = await admin(`/rosters/${draft.id}`)
    expect((await admin(`/rosters/${draft.id}/confirm`, write('POST', {}, { 'If-Match': current.headers.get('etag')! }))).status).toBeGreaterThanOrEqual(400)
    await adminStore.reload(); expect(await adminStore.decide(nurseId, 'withdraw')).toBe(true)

    // The ward was explicitly created in Seoul. Avoid choosing yesterday at a UTC/local midnight boundary.
    const wardToday = new Date(Date.now() + 9 * 3600_000).toISOString().slice(0, 10)
    const lateMonth = wardToday.slice(0, 7)
    const lateRequest = await nurseApi.submit(wardToday)
    expect(lateRequest.afterDeadline).toBe(true)
    await expect(nurseApi.decide(lateRequest.requestId, 'withdraw')).rejects.toMatchObject({ status: 409 })
    await adminApi.decide(lateRequest.requestId, 'reject')
    expect((await nurseApi.list(lateMonth)).items[0]!.status).toBe('REJECTED')
    adminStore.dispose(); memberStore.dispose()
  } finally { await runtime.stop() }
}, 120_000)
