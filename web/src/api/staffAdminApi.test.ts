import { createStaffAdminApi } from './staffAdminApi'
import { NURSE_A, TEAM_A } from '../test/staffFixtures'

it('encodes ward and entity IDs and preserves the full replacement settings', async () => {
  const fetch = vi.fn().mockImplementation(async () => new Response(JSON.stringify(NURSE_A), { status: 200 }))
  const api = createStaffAdminApi(fetch, 'ward/a')
  await api.settings('nurse/b', { seniorEligible: true, stageOverride: 4 })
  await api.settings('nurse/b', { seniorEligible: false, stageOverride: null })
  expect(fetch.mock.calls.map(([url, init]) => [url, init.method, JSON.parse(init.body)])).toEqual([
    ['/wards/ward%2Fa/staff/nurse%2Fb/roster-settings', 'PUT', { seniorEligible: true, stageOverride: 4 }],
    ['/wards/ward%2Fa/staff/nurse%2Fb/roster-settings', 'PUT', { seniorEligible: false, stageOverride: null }],
  ])
})

it('uses the exact paths, verbs and request bodies for every staff action', async () => {
  const fetch = vi.fn().mockImplementation(async () => new Response(JSON.stringify({ items: [] }), { status: 200 }))
  const api = createStaffAdminApi(fetch, 'ward')
  await api.teams(); await api.staff(); await api.members(); await api.policy(); await api.adjustments('nurse')
  await api.createTeam({ name: TEAM_A.name }); await api.updateTeam('team', { name: '팀', sortOrder: 2 })
  await api.createStaff({ displayName: '가상', qualifications: ['가상 자격'], active: true })
  await api.updateStaff('nurse', { displayName: '가상', qualifications: ['가상 자격'], active: false })
  await api.assignTeam('nurse', null); await api.order('team', ['nurse'])
  await api.setProfile('nurse', { careerStartedOn: '2020-01', rank: '신규' }); await api.confirmProfile('nurse')
  await api.membership('user', { active: true })
  fetch.mockResolvedValueOnce(new Response(null, { status: 204 }))
  await api.deleteTeam('team')
  expect(fetch.mock.calls.map(([url, init]) => [url, init?.method ?? 'GET', init?.body ? JSON.parse(init.body) : null])).toEqual([
    ['/wards/ward/teams', 'GET', null], ['/wards/ward/staff?includeInactive=true', 'GET', null], ['/wards/ward/members', 'GET', null], ['/wards/ward/career-policy', 'GET', null], ['/wards/ward/staff/nurse/adjustments', 'GET', null],
    ['/wards/ward/teams', 'POST', { name: TEAM_A.name }], ['/wards/ward/teams/team', 'PUT', { name: '팀', sortOrder: 2 }],
    ['/wards/ward/staff', 'POST', { displayName: '가상', qualifications: ['가상 자격'], active: true }], ['/wards/ward/staff/nurse', 'PUT', { displayName: '가상', qualifications: ['가상 자격'], active: false }],
    ['/wards/ward/staff/nurse/team', 'PUT', { teamId: null }], ['/wards/ward/teams/team/order', 'PUT', { nurseIds: ['nurse'] }],
    ['/wards/ward/staff/nurse/profile', 'PUT', { careerStartedOn: '2020-01', rank: '신규' }], ['/wards/ward/staff/nurse/profile/confirm', 'POST', null],
    ['/wards/ward/memberships/user', 'PATCH', { active: true }], ['/wards/ward/teams/team', 'DELETE', null],
  ])
})
