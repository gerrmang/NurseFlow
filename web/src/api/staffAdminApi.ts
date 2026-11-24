import { ApiError, ClientErrorCode, expectNoContent, readJson } from './http'
import type { AuthorizedFetch } from './nurseflowApi'
import type { CareerPolicy, CareerProfileInput, RosterEligibility, RosterSettingsRequest, StaffAdjustment, StaffInput, StaffMember, Team, TeamInput, UpdateMembershipRequest, WardMember } from './types'

const json = (method: string, body?: unknown): RequestInit => ({
  method, ...(body === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
})

export function createStaffAdminApi(fetch: AuthorizedFetch, wardId: string) {
  const base = `/wards/${encodeURIComponent(wardId)}`
  const nurse = (id: string) => `${base}/staff/${encodeURIComponent(id)}`
  const team = (id: string) => `${base}/teams/${encodeURIComponent(id)}`
  const items = async <T>(path: string) => (await readJson<{ items: T[] }>(await fetch(path))).items
  return {
    teams: () => items<Team>(`${base}/teams`),
    staff: () => items<StaffMember>(`${base}/staff?includeInactive=true`),
    members: () => items<WardMember>(`${base}/members`),
    policy: async () => readJson<CareerPolicy>(await fetch(`${base}/career-policy`)),
    eligibility: async () => {
      const value = await readJson<RosterEligibility>(await fetch(`${base}/roster-eligibility`))
      if (!value || typeof value.demoMode !== 'boolean' || !Number.isInteger(value.policyRevision) || value.policyRevision < 0) {
        throw new ApiError(0, ClientErrorCode.INVALID_RESPONSE)
      }
      return value
    },
    createTeam: async (input: TeamInput) => readJson<Team>(await fetch(`${base}/teams`, json('POST', input))),
    updateTeam: async (id: string, input: TeamInput) => readJson<Team>(await fetch(team(id), json('PUT', input))),
    deleteTeam: async (id: string) => expectNoContent(await fetch(team(id), json('DELETE'))),
    createStaff: async (input: StaffInput) => readJson<StaffMember>(await fetch(`${base}/staff`, json('POST', input))),
    updateStaff: async (id: string, input: StaffInput) => readJson<StaffMember>(await fetch(nurse(id), json('PUT', input))),
    assignTeam: async (id: string, teamId: string | null) => readJson<StaffMember>(await fetch(`${nurse(id)}/team`, json('PUT', { teamId }))),
    order: async (id: string, nurseIds: string[]) => readJson<{ items: StaffMember[] }>(await fetch(`${team(id)}/order`, json('PUT', { nurseIds }))),
    setProfile: async (id: string, input: CareerProfileInput) => readJson<StaffMember>(await fetch(`${nurse(id)}/profile`, json('PUT', input))),
    confirmProfile: async (id: string) => readJson<StaffMember>(await fetch(`${nurse(id)}/profile/confirm`, json('POST'))),
    settings: async (id: string, input: RosterSettingsRequest) => readJson<StaffMember>(await fetch(`${nurse(id)}/roster-settings`, json('PUT', input))),
    adjustments: (id: string) => items<StaffAdjustment>(`${nurse(id)}/adjustments`),
    membership: async (id: string, input: UpdateMembershipRequest) => readJson<WardMember>(await fetch(`${base}/memberships/${encodeURIComponent(id)}`, json('PATCH', input))),
  }
}
export type StaffAdminApi = ReturnType<typeof createStaffAdminApi>
