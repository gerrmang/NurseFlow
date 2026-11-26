/// <reference types="node" />
// @vitest-environment node
// Only the fictional OIDC provider + isolated in-memory backend described in B7b-verification.md.
import process from 'node:process'
import { ApiError, readJson } from '../api/http'
import { createScheduleSettingsApi, type ShiftInput } from '../api/scheduleSettingsApi'
import { createStaffAdminApi } from '../api/staffAdminApi'
import { createNurseFlowApi } from '../api/nurseflowApi'
import type { WardMembership, WebSession, HospitalCandidate } from '../api/types'
import { ScheduleSettingsStore } from './ScheduleSettingsStore'

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
  return readJson<WebSession>(await fetch(`${base}/auth/web/kakao/code`, { method: 'POST', headers: { ...headers, Cookie: start.headers.getSetCookie().map(c => c.split(';')[0]).join('; ') }, body: JSON.stringify({ code: callback.searchParams.get('code'), state: callback.searchParams.get('state') }) }))
}

it.skipIf(process.env.NURSEFLOW_B7B_E2E !== '1')('persists all four settings through the real server, detects conflicts and restores a fresh store', async () => {
  const admin = await login('admin'), member = await login('nurse')
  let loseResponse = false, shiftPosts = 0
  const authorized = (token: string) => async (path: string, init?: RequestInit) => {
    const h = new Headers(init?.headers); h.set('Authorization', `Bearer ${token}`); h.set('Origin', origin)
    const response = await fetch(base + path, { ...init, headers: h })
    if (path.endsWith('/shift-types') && init?.method === 'POST') { ++shiftPosts; if (loseResponse) { loseResponse = false; expect(response.status).toBe(201); await response.arrayBuffer(); throw new ApiError(0, 'NETWORK') } }
    return response
  }
  const fetchAdmin = authorized(admin.accessToken), account = createNurseFlowApi(fetchAdmin)
  const hospital = (await readJson<{ items: HospitalCandidate[] }>(await fetchAdmin('/hospitals/search?query=365'))).items[0]!
  const ward = await readJson<WardMembership>(await fetchAdmin('/wards', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify({ hospital: { source: hospital.source, externalId: hospital.externalId }, wardName: `B7b 가상 검증 ${crypto.randomUUID().slice(0, 6)}`, timeZone: 'Asia/Seoul' }) }))
  const staff = createStaffAdminApi(fetchAdmin, ward.wardId), api = createScheduleSettingsApi(fetchAdmin, ward.wardId)
  const team = await staff.createTeam({ name: '가상 A팀' })
  const nurse = await staff.createStaff({ displayName: '가상 설정 간호사', qualifications: ['가상 자격'], teamId: team.teamId, active: true })
  const store = new ScheduleSettingsStore(api, staff, () => account.getWards(), ward.wardId, '2026-11')
  await store.reload(); expect(store.getSnapshot().data?.draft).toBeNull()
  const day: ShiftInput = { code: 'D', displayName: '가상 Day', kind: 'DAY', startTime: '07:00', endTime: '15:00', spansMidnight: false, breakMinutes: 30, recognizedMinutes: 450, targetCreditMinutes: 450 }
  expect(await store.run('유형 생성', () => api.createShift(day, store.getSnapshot().data!.shifts.etag, crypto.randomUUID()))).toBe(true)
  const shift = store.getSnapshot().data!.shifts.value.items[0]!
  expect(shift).toMatchObject({ breakMinutes: 30, recognizedMinutes: 450, durationMinutes: 480 })
  expect(await store.run('초안 생성', () => api.createRoster('2026-11', crypto.randomUUID()))).toBe(true)
  const draft = () => store.getSnapshot().data!.draft!
  expect(await store.run('수요', () => api.requirements(draft().value.rosterId, [{ date: '2026-11-02', shiftTypeId: shift.shiftTypeId, teamId: team.teamId, requiredCount: 2, requiredQualifications: ['가상 자격'] }], draft().etag))).toBe(true)
  expect(draft().value.slots).toHaveLength(2)
  expect(draft().value.slots.filter(s => s.seniorRequired)).toHaveLength(1)
  expect(draft().value.staffing.seniorUnfilled).toBe(1)
  const oldRosterTag = draft().etag
  const preview = await store.preview(shift.shiftTypeId, { ...day, endTime: '16:00' })
  expect(preview?.affectedDrafts).toHaveLength(1); expect(preview?.rewritesDraftSlotTimes).toBe(true)
  expect(await store.run('유형 수정', () => api.updateShift(shift.shiftTypeId, { ...day, endTime: '16:00' }, store.getSnapshot().data!.shifts.etag))).toBe(true)
  expect(draft().value.needsRevalidation).toBe(true); expect(draft().etag).not.toBe(oldRosterTag)
  expect(draft().value.slots[0]!.endAt).toBe('2026-11-02T07:00:00Z')
  await expect(api.requirements(draft().value.rosterId, [], oldRosterTag)).rejects.toMatchObject({ status: 412 })
  const absence = { nurseId: nurse.nurseId, kind: 'APPROVED_HALF_DAY' as const, startsAt: '2026-11-03T00:00:00Z', endsAt: '2026-11-03T04:00:00Z' }
  expect(await store.run('부재 등록', () => api.saveAbsence(draft().value.rosterId, absence, draft().etag, undefined, crypto.randomUUID()))).toBe(true)
  const absenceId = store.getSnapshot().data!.absences[0]!.absenceId
  expect(await store.run('부재 수정', () => api.saveAbsence(draft().value.rosterId, { ...absence, endsAt: '2026-11-03T05:00:00Z' }, draft().etag, absenceId))).toBe(true)
  expect(store.getSnapshot().data!.absences[0]!.endsAt).toBe('2026-11-03T05:00:00Z')
  expect(await store.run('형태', () => api.savePattern(nurse.nurseId, { workPattern: 'NIGHT_ONLY' }))).toBe(true)
  expect(await api.pattern(nurse.nurseId)).toEqual({ workPattern: 'NIGHT_ONLY' })
  const reopened = new ScheduleSettingsStore(api, staff, () => account.getWards(), ward.wardId, '2026-11')
  expect(await reopened.reload()).toBe(true)
  expect(reopened.getSnapshot().data!.absences).toHaveLength(1); expect(reopened.getSnapshot().data!.draft!.value.slots).toHaveLength(2)
  const originalPolicy = store.getSnapshot().data!.shifts.etag
  await api.updateShift(shift.shiftTypeId, { ...day, endTime: '16:00', targetCreditMinutes: 400 }, originalPolicy)
  expect(await store.run('오래된 수정', () => api.updateShift(shift.shiftTypeId, day, originalPolicy))).toBe(false)
  expect(store.getSnapshot().notice).toContain('다른 화면에서 변경')
  loseResponse = true
  expect(await store.run('응답 유실 생성', () => api.createShift({ ...day, code: 'D2' }, store.getSnapshot().data!.shifts.etag, crypto.randomUUID()))).toBe(false)
  expect(store.getSnapshot().uncertain).toBe(true); expect(store.getSnapshot().data!.shifts.value.items.filter(s => s.code === 'D2')).toHaveLength(1)
  const count = shiftPosts; await store.run('재전송 차단', () => api.createShift({ ...day, code: 'D2' }, store.getSnapshot().data!.shifts.etag, crypto.randomUUID())); expect(shiftPosts).toBe(count)
  store.acknowledge()
  expect(await store.run('부재 삭제', () => api.deleteAbsence(draft().value.rosterId, absenceId, draft().etag))).toBe(true)
  expect(store.getSnapshot().data!.absences).toEqual([])
  expect(await store.run('VY 등록', () => api.saveAbsence(draft().value.rosterId, { ...absence, kind: 'APPROVED_LEAVE', startsAt: '2026-11-05T15:00:00Z', endsAt: '2026-11-06T15:00:00Z' }, draft().etag, undefined, crypto.randomUUID()))).toBe(true)
  expect(store.getSnapshot().data!.absences[0]!.kind).toBe('APPROVED_LEAVE')
  expect(await store.run('유형 비활성화', () => api.deactivateShift(shift.shiftTypeId, store.getSnapshot().data!.shifts.etag))).toBe(true)
  expect(store.getSnapshot().data!.shifts.value.items.find(s => s.shiftTypeId === shift.shiftTypeId)!.active).toBe(false)
  expect(draft().value.slots).toHaveLength(2)
  expect(await store.run('유형 재활성화', () => api.updateShift(shift.shiftTypeId, { ...day, active: true }, store.getSnapshot().data!.shifts.etag))).toBe(true)
  expect(store.getSnapshot().data!.shifts.value.items.find(s => s.shiftTypeId === shift.shiftTypeId)!.active).toBe(true)
  expect(await store.run('Night 생성', () => api.createShift({ ...day, code: 'N', kind: 'NIGHT', startTime: '22:00', endTime: '07:00', spansMidnight: true }, store.getSnapshot().data!.shifts.etag, crypto.randomUUID()))).toBe(true)
  expect(store.getSnapshot().data!.shifts.value.items.find(s => s.code === 'N')).toMatchObject({ durationMinutes: 540, spansMidnight: true })
  const invitation = await account.createInvitation(ward.wardId, { role: 'WARD_MEMBER' })
  await createNurseFlowApi(authorized(member.accessToken)).redeemInvitation({ token: invitation.token, hospitalJoinedOn: '2024-01' })
  const memberApi = createScheduleSettingsApi(authorized(member.accessToken), ward.wardId)
  await expect(memberApi.savePattern(nurse.nurseId, { workPattern: 'GENERAL' })).rejects.toMatchObject({ status: 403 })
  await expect(memberApi.rosters('2026-11')).rejects.toMatchObject({ status: 403 })
}, 60_000)
