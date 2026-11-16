/// <reference types="node" />
// @vitest-environment node
// Run only against the isolated fictional OIDC provider and in-memory backend documented in B5b-verification.md.
import process from 'node:process'
import { ApiError, readJson } from '../api/http'
import { createNurseFlowApi } from '../api/nurseflowApi'
import { createStaffAdminApi } from '../api/staffAdminApi'
import type { WardMembership, WebSession } from '../api/types'
import type { components } from '../api/schema.gen'
import { StaffAdminStore } from './StaffAdminStore'

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
    method: 'POST', headers: { ...headers, Cookie: start.headers.getSetCookie().map((cookie) => cookie.split(';')[0]).join('; ') },
    body: JSON.stringify({ code: callback.searchParams.get('code'), state: callback.searchParams.get('state') }),
  }))
}

it.skipIf(process.env.NURSEFLOW_B5B_E2E !== '1')('manages real teams, career, ordering, membership protections and lost writes through the client store', async () => {
  const admin = await login('admin')
  const member = await login('nurse')
  const other = await login('admin2')
  let loseTeamResponse = false
  let teamPosts = 0
  const authorized = (token: string) => async (path: string, init?: RequestInit) => {
    const requestHeaders = new Headers(init?.headers)
    requestHeaders.set('Authorization', `Bearer ${token}`)
    requestHeaders.set('Origin', origin)
    const response = await fetch(base + path, { ...init, headers: requestHeaders })
    if (path.endsWith('/teams') && init?.method === 'POST') {
      ++teamPosts
      if (loseTeamResponse) { loseTeamResponse = false; expect(response.status).toBe(201); await response.arrayBuffer(); throw new ApiError(0, 'NETWORK') }
    }
    return response
  }
  const adminFetch = authorized(admin.accessToken)
  const account = createNurseFlowApi(adminFetch)
  const nurseAccount = createNurseFlowApi(authorized(member.accessToken))
  const otherAccount = createNurseFlowApi(authorized(other.accessToken))
  const hospitals = await readJson<{ items: components['schemas']['HospitalCandidate'][] }>(await adminFetch('/hospitals/search?query=365'))
  const hospital = hospitals.items[0]!
  const ward = await readJson<WardMembership>(await adminFetch('/wards', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() }, body: JSON.stringify({ hospital: { source: hospital.source, externalId: hospital.externalId }, wardName: `B5b 가상 검증 ${crypto.randomUUID()}`, timeZone: 'Asia/Seoul' }) }))
  const api = createStaffAdminApi(adminFetch, ward.wardId)
  const store = new StaffAdminStore(api, () => account.getWards(), ward.wardId)
  await store.reload()
  expect(store.getSnapshot().data?.teams).toEqual([])
  expect(await store.run('팀 생성', () => api.createTeam({ name: '가상 A팀' }))).toBe(true)
  const teamA = store.getSnapshot().data!.teams[0]!
  await store.run('팀 생성', () => api.createTeam({ name: '가상 B팀' }))
  const teamB = store.getSnapshot().data!.teams.find((team) => team.name === '가상 B팀')!
  await store.run('등록', () => api.createStaff({ displayName: '가상 간호사 가', qualifications: ['가상 자격'], active: true, teamId: teamA.teamId }))
  await store.run('등록', () => api.createStaff({ displayName: '가상 간호사 나', qualifications: [], active: true, teamId: teamA.teamId }))
  const [a, b] = store.getSnapshot().data!.staff
  expect(a).toBeTruthy(); expect(b).toBeTruthy()
  await store.run('정렬', () => api.order(teamA.teamId, [b!.nurseId, a!.nurseId]))
  expect(store.getSnapshot().data!.staff.map((nurse) => nurse.nurseId)).toEqual([b!.nurseId, a!.nurseId])
  await store.run('경력', () => api.setProfile(a!.nurseId, { careerStartedOn: '2020-03', rank: '신규' }))
  await store.run('편성', () => api.settings(a!.nurseId, { seniorEligible: true, stageOverride: 4 }))
  expect(store.getSnapshot().data!.staff.find((nurse) => nurse.nurseId === a!.nurseId)).toMatchObject({ seniorEligible: true, stageOverride: 4, rosterStage: 4 })
  await store.run('팀 이동', () => api.assignTeam(a!.nurseId, teamB.teamId))
  expect(store.getSnapshot().data!.staff.find((nurse) => nurse.nurseId === a!.nurseId)?.teamOrder).toBeNull()
  const history = await api.adjustments(a!.nurseId)
  expect(history.map((item) => item.kind)).toEqual(expect.arrayContaining(['TEAM_ORDER', 'PROFILE_SET_BY_ADMIN', 'SENIOR_ELIGIBLE', 'STAGE_OVERRIDE', 'TEAM_ASSIGNED']))
  expect(await store.run('삭제', () => api.deleteTeam(teamB.teamId))).toBe(false)
  expect(store.getSnapshot().notice).toContain('활성 간호사가 있는 팀')
  expect(await store.run('오래된 정렬', () => api.order(teamA.teamId, [b!.nurseId, a!.nurseId]))).toBe(false)
  expect(store.getSnapshot().notice).toContain('다시 정렬')
  expect(await store.run('마지막 관리자', () => api.membership(admin.user.userId, { active: false }))).toBe(false)
  expect(store.getSnapshot().notice).toContain('마지막 활성 관리자')

  const invited = await account.createInvitation(ward.wardId, { role: 'WARD_MEMBER' })
  await nurseAccount.redeemInvitation({ token: invited.token, hospitalJoinedOn: '2024-01' })
  await store.reload()
  const linked = store.getSnapshot().data!.members.find((item) => item.userId === member.user.userId)!
  const linkedNurse = store.getSnapshot().data!.staff.find((item) => item.nurseId === linked.nurseId)!
  expect(linkedNurse.profile.hospitalJoinedOn).toBe('2024-01')
  expect(await store.run('관리자 지정', () => api.membership(linked.userId, { admin: true }))).toBe(false)
  expect(store.getSnapshot().notice).toContain('먼저 비활성화')
  const setActive = (active: boolean) => api.updateStaff(linkedNurse.nurseId, { displayName: linkedNurse.displayName, qualifications: linkedNurse.qualifications, active })
  await store.run('기록 비활성', () => setActive(false))
  expect(await store.run('관리자 지정', () => api.membership(linked.userId, { admin: true }))).toBe(true)
  expect(await store.run('기록 재활성', () => setActive(true))).toBe(false)
  await store.run('관리자 해제', () => api.membership(linked.userId, { admin: false }))
  await store.run('기록 활성', () => setActive(true))
  await store.run('접근 비활성', () => api.membership(linked.userId, { active: false }))
  expect(await store.run('비활성 구성원 기록 복원', () => setActive(true))).toBe(false)
  expect(store.getSnapshot().notice).toContain('접근을 먼저 재활성화')
  await store.run('접근 활성', () => api.membership(linked.userId, { active: true }))
  expect(store.getSnapshot().data!.staff.find((item) => item.nurseId === linkedNurse.nurseId)?.active).toBe(false)
  await store.run('기록 활성', () => setActive(true))

  const previousCount = teamPosts
  loseTeamResponse = true
  expect(await store.run('유실 팀 생성', () => api.createTeam({ name: '가상 응답 유실 팀' }))).toBe(false)
  expect(store.getSnapshot().uncertain).toBe(true)
  expect(store.getSnapshot().data!.teams.filter((team) => team.name === '가상 응답 유실 팀')).toHaveLength(1)
  await store.run('재전송 금지', () => api.createTeam({ name: '가상 응답 유실 팀' }))
  expect(teamPosts).toBe(previousCount + 1)
  store.acknowledge()

  const secondAdmin = await account.createInvitation(ward.wardId, { role: 'WARD_ADMIN' })
  await otherAccount.redeemInvitation({ token: secondAdmin.token })
  await store.reload()
  expect(await store.run('관리자 전용 역할 해제', () => api.membership(admin.user.userId, { admin: false }))).toBe(false)
  expect(store.getSnapshot().notice).toContain('관리자 전용')
  expect(await store.run('본인 접근 비활성', () => api.membership(admin.user.userId, { active: false }))).toBe(true)
  expect(store.getSnapshot().forbidden).toBe(true)
  expect(store.getSnapshot().data).toBeNull()
}, 60_000)
